package com.portfolio.logistics.healing.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.portfolio.logistics.healing.core.ActionKind;
import com.portfolio.logistics.healing.core.ElementSnapshot;
import com.portfolio.logistics.healing.core.HealingConfig;
import com.portfolio.logistics.healing.core.ai.AiLocatorAdvisor;
import com.portfolio.logistics.healing.core.ai.AiVerdict;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link AiLocatorAdvisor} backed by Claude (Anthropic Messages API). Used only when
 * {@code healing.ai.enabled=true}; credentials come from the environment (ANTHROPIC_API_KEY, or an
 * {@code ant auth login} profile) — never from config.properties.
 *
 * What is sent: the broken selector, the action, and element metadata for the reference and up to 10
 * candidates (tag, role, ids, labels, visible text, section heading, position). Never typed input
 * values, cookies or page HTML. Any failure returns empty and the heal proceeds without AI.
 */
public final class ClaudeLocatorAdvisor implements AiLocatorAdvisor {

    private static final Logger LOG = LogManager.getLogger("com.portfolio.logistics.healing");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final String SYSTEM = """
            You help a UI test-automation framework recover from a broken element locator.
            A test used a locator that no longer matches anything. You get a description of the element it \
            used to find (the reference) and a numbered list of elements currently on the page.
            Decide which candidate, if any, is the SAME element: the same control with the same purpose, \
            phealthcarebly renamed, re-labelled or moved. A different control that merely looks similar (another \
            field in the same form, another button in the same toolbar) is NOT the same element.
            If no candidate is clearly the same element, answer choice -1. A wrong pick makes the test act \
            on the wrong element, which is worse than failing, so be conservative.
            confidence is how sure you are (0-100) that your choice is correct.""";

    private final HealingConfig config;
    private volatile AnthropicClient client;
    private volatile boolean disabled;

    public ClaudeLocatorAdvisor(HealingConfig config) {
        this.config = config;
    }

    @Override
    public Optional<AiVerdict> choose(String originalSelector, ActionKind action, ElementSnapshot reference,
                                      List<ElementSnapshot> candidates) {
        if (disabled) return Optional.empty();
        try {
            MessageCreateParams params = request(config.aiModel, originalSelector, action, reference, candidates);
            Message msg = client().messages().create(params);
            if (msg.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
                LOG.warn("[HEAL] AI advisor declined to answer; continuing without it");
                return Optional.empty();
            }
            String text = msg.content().stream()
                    .flatMap(b -> b.text().stream())
                    .map(TextBlock::text)
                    .collect(Collectors.joining());
            AiVerdict v = GSON.fromJson(text, AiVerdict.class);
            return v == null ? Optional.empty() : Optional.of(v);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            disable("credentials were rejected (" + e.getMessage() + ")");
        } catch (RateLimitException e) {
            LOG.warn("[HEAL] AI advisor rate-limited; continuing without it: {}", e.getMessage());
        } catch (AnthropicServiceException e) {
            LOG.warn("[HEAL] AI advisor API error; continuing without it: {}", e.getMessage());
        } catch (AnthropicIoException e) {
            LOG.warn("[HEAL] AI advisor unreachable; continuing without it: {}", e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("[HEAL] AI advisor failed; continuing without it: {}", e.toString());
        }
        return Optional.empty();
    }

    private AnthropicClient client() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    try {
                        c = AnthropicOkHttpClient.builder()
                                .fromEnv()
                                .timeout(Duration.ofSeconds(config.aiTimeoutSeconds))
                                .maxRetries(2)
                                .build();
                    } catch (RuntimeException e) {
                        disable("no Anthropic credentials found — set ANTHROPIC_API_KEY (" + e.getMessage() + ")");
                        throw e;
                    }
                    client = c;
                }
            }
        }
        return c;
    }

    private void disable(String why) {
        disabled = true;
        LOG.warn("[HEAL] AI fallback switched off for this run: {}", why);
    }

    /** The exact request sent to the API (public so the self-test can check it without a network call). */
    public static MessageCreateParams request(String model, String originalSelector, ActionKind action,
                                              ElementSnapshot reference, List<ElementSnapshot> candidates) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(4096L)
                .system(SYSTEM)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(JsonOutputFormat.builder().schema(schema()).build())
                        .build())
                // Server-side fallback: if a safety classifier declines, the API retries on another model.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessage(prompt(originalSelector, action, reference, candidates))
                .build();
    }

    private static String prompt(String originalSelector, ActionKind action, ElementSnapshot reference,
                                 List<ElementSnapshot> candidates) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Map<String, Object> m = describe(candidates.get(i));
            m.put("choice", i);
            list.add(m);
        }
        return "Broken locator: " + originalSelector + "\n"
                + "Action the test performs: " + action + "\n\n"
                + "Reference (what the element looked like):\n" + GSON.toJson(describe(reference)) + "\n\n"
                + "Candidates on the page now:\n" + GSON.toJson(list);
    }

    /** Only identifying metadata — no values, no HTML. */
    private static Map<String, Object> describe(ElementSnapshot e) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "tag", e.tag);
        put(m, "type", e.type);
        put(m, "role", e.role);
        put(m, "id", e.id);
        put(m, "name", e.name);
        put(m, "testId", e.testId);
        put(m, "accessibleName", e.accessibleName);
        put(m, "label", e.labelText);
        put(m, "text", e.text);
        put(m, "placeholder", e.placeholder);
        put(m, "title", e.title);
        put(m, "href", e.href);
        put(m, "sectionHeading", e.heading);
        if (e.inDialog != null) m.put("inDialog", e.inDialog);
        if (e.ancestry != null && !e.ancestry.isEmpty()) m.put("parents", e.ancestry);
        if (e.x != null && e.y != null) m.put("position", Map.of("x", e.x, "y", e.y));
        if (e.enabled != null) m.put("enabled", e.enabled);
        return m;
    }

    private static void put(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }

    private static JsonOutputFormat.Schema schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("choice", Map.of("type", "integer", "description", "Index of the matching candidate, or -1 if none is clearly the same element"));
        props.put("confidence", Map.of("type", "number", "description", "0-100"));
        props.put("rationale", Map.of("type", "string", "description", "One sentence on why"));
        return JsonOutputFormat.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(props))
                .putAdditionalProperty("required", JsonValue.from(List.of("choice", "confidence", "rationale")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();
    }
}
