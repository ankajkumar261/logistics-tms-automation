package com.portfolio.logistics.tests.healing;

import com.anthropic.models.messages.MessageCreateParams;
import com.portfolio.logistics.healing.ai.ClaudeLocatorAdvisor;
import com.portfolio.logistics.healing.core.ActionKind;
import com.portfolio.logistics.healing.core.ElementSnapshot;
import com.portfolio.logistics.healing.core.HealingConfig;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * The AI fallback is off by default and can't be exercised without an API key, so this checks the
 * request it would send — offline: it builds, uses the configured model, and carries element metadata
 * but nothing else (no positional CSS, no data-* values).
 */
@Epic("Self-healing framework")
@Feature("AI fallback")
public class AiAdvisorRequestTest {

    @Test(description = "AI request builds offline and contains only identifying metadata")
    public void requestBuildsWithMetadataOnly() {
        ElementSnapshot ref = new ElementSnapshot();
        ref.tag = "button";
        ref.role = "button";
        ref.accessibleName = "Add Customer";
        ElementSnapshot cand = new ElementSnapshot();
        cand.tag = "button";
        cand.role = "button";
        cand.accessibleName = "New Customer";
        cand.cssPath = "body > div:nth-of-type(3) > button:nth-of-type(1)";
        cand.dataAttrs = Map.of("data-row-id", "CUST-12345");

        MessageCreateParams p = ClaudeLocatorAdvisor.request(HealingConfig.builder().build().aiModel,
                "internal:role=button[name=\"Add Customer\"i]", ActionKind.CLICK, ref, List.of(cand));

        assertEquals(p.model().asString(), "claude-opus-5-5");
        String user = p.messages().get(0).content().toString();
        assertTrue(user.contains("New Customer"), user);
        assertFalse(user.contains("nth-of-type"), "positional CSS is not sent");
        assertFalse(user.contains("CUST-12345"), "data-* values are not sent");
    }
}
