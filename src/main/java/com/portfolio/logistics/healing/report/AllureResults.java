package com.portfolio.logistics.healing.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portfolio.logistics.healing.core.HealingEvent;
import io.qameta.allure.Allure;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.StatusDetails;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Allure has no custom statuses, so PASS_WITH_HEALING is expressed with what it does support:
 *  - the test stays "passed" (dashboards and CI keep meaning what they meant);
 *  - its status message starts with PASS_WITH_HEALING, and a categories.json entry groups such
 *    tests under "Passed with self-healing" in the Categories tab;
 *  - tags PASS_WITH_HEALING / self-healed for filtering;
 *  - environment.properties gets the run's healing totals.
 * allure-testng's onTestSuccess only sets the status (it leaves the message alone), so the message
 * set here survives; a failing test's message is overwritten with its real error, as it should be.
 */
final class AllureResults {

    static final String PASS_WITH_HEALING = "PASS_WITH_HEALING";
    private static final Logger LOG = LogManager.getLogger("com.portfolio.logistics.healing");
    private static final String CATEGORY = "Passed with self-healing (PASS_WITH_HEALING)";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AllureResults() { }

    static Path resultsDir() {
        return Paths.get(System.getProperty("allure.results.directory", "allure-results"));
    }

    /** Called while the test case is still open in Allure (right after the test method returns). */
    static void markTest(boolean passed, List<HealingEvent> applied) {
        String healedList = applied.stream()
                .map(e -> e.original + " → " + e.healed + String.format(" (%.1f%%)", e.confidence))
                .collect(Collectors.joining("; "));
        Allure.getLifecycle().updateTestCase(tr -> {
            tr.getLabels().add(new Label().setName("tag").setValue("self-healed"));
            if (!passed) return;
            tr.getLabels().add(new Label().setName("tag").setValue(PASS_WITH_HEALING));
            StatusDetails d = tr.getStatusDetails() != null ? tr.getStatusDetails() : new StatusDetails();
            d.setMessage(PASS_WITH_HEALING + ": " + applied.size() + " locator(s) self-healed — " + healedList
                    + ". Update the page object(s); see the 'Self-healed locator' step(s).");
            tr.setStatusDetails(d);
        });
    }

    static void ensureCategory() {
        Path file = resultsDir().resolve("categories.json");
        try {
            Files.createDirectories(file.getParent());
            JsonArray categories = new JsonArray();
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement existing = JsonParser.parseReader(r);
                    if (existing.isJsonArray()) categories = existing.getAsJsonArray();
                }
            }
            for (JsonElement c : categories) {
                if (c.isJsonObject() && CATEGORY.equals(c.getAsJsonObject().get("name").getAsString())) return;
            }
            JsonObject ours = new JsonObject();
            ours.addProperty("name", CATEGORY);
            ours.addProperty("description", "Passed only because one or more locators were self-healed. "
                    + "Update the page objects with the suggested locators.");
            JsonArray statuses = new JsonArray();
            statuses.add("passed");
            ours.add("matchedStatuses", statuses);
            ours.addProperty("messageRegex", "(?s)" + PASS_WITH_HEALING + ".*");
            categories.add(ours);
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(categories, w);
            }
        } catch (Exception e) {
            LOG.warn("[HEAL] Could not write Allure categories ({}): {}", file, e.toString());
        }
    }

    static void writeEnvironment(Map<String, String> entries) {
        Path file = resultsDir().resolve("environment.properties");
        try {
            Files.createDirectories(file.getParent());
            Properties p = new Properties();
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    p.load(r);
                }
            }
            p.putAll(entries);
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                p.store(w, null);
            }
        } catch (Exception e) {
            LOG.warn("[HEAL] Could not write Allure environment ({}): {}", file, e.toString());
        }
    }
}
