package com.portfolio.logistics.tests.healing;

import com.portfolio.logistics.healing.core.ElementSnapshot;
import com.portfolio.logistics.healing.core.LocatorIntent;
import com.portfolio.logistics.healing.core.scoring.ConfidenceScorer;
import com.portfolio.logistics.healing.core.scoring.ScoredCandidate;
import com.portfolio.logistics.healing.core.scoring.ScoringProfile;
import com.portfolio.logistics.healing.core.scoring.StringSimilarity;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/** Self-healing engine: string similarity, selector parsing and confidence scoring (no browser). */
@Epic("Self-healing framework")
@Feature("Scoring")
public class HealingScoringTest {

    private final ConfidenceScorer scorer = new ConfidenceScorer(ScoringProfile.web());

    @Test(description = "Formatting-only differences in an identifier or label count as identical")
    public void formattingDifferencesAreIgnored() {
        assertEquals(StringSimilarity.similarity("companyName", "company-name"), 1.0);
        assertEquals(StringSimilarity.similarity("Company Name*", "company_names"), 1.0);
        assertEquals(StringSimilarity.similarity("Weight*", "weight"), 1.0);
    }

    @Test(description = "A different word is not a rename: phone vs phone2, Add vs New Customer")
    public void differentWordsScoreLow() {
        assertTrue(StringSimilarity.similarity("phone", "phone2") < 0.8);
        assertTrue(StringSimilarity.similarity("Add Customer", "New Customer") < 0.7);
        assertTrue(StringSimilarity.similarity("weight", "commodity") < 0.2);
        assertEquals(StringSimilarity.similarity("", "anything"), 0.0);
    }

    @Test(description = "Playwright selector strings are read into an intent")
    public void selectorIntentIsParsed() {
        ElementSnapshot id = LocatorIntent.parse("#companyName");
        assertEquals(id.id, "companyName");

        ElementSnapshot role = LocatorIntent.parse("internal:role=button[name=\"Add Customer\"i] >> nth=0");
        assertEquals(role.role, "button");
        assertEquals(role.accessibleName, "Add Customer");

        ElementSnapshot css = LocatorIntent.parse("form button[role='combobox'][aria-haspopup='dialog']");
        assertEquals(css.tag, "button");
        assertEquals(css.role, "combobox");

        ElementSnapshot input = LocatorIntent.parse("input[placeholder*='Search customer' i]");
        assertEquals(input.tag, "input");
        assertEquals(input.placeholder, "Search customer");

        ElementSnapshot scoped = LocatorIntent.parse("div[role='dialog'] >> internal:role=button[name=/Confirm|Yes/i]");
        assertEquals(scoped.role, "button");
        assertNull(scoped.accessibleName, "a regex name states no single expected value");

        assertEquals(LocatorIntent.parse("text=Invalid credentials").text, "Invalid credentials");
        assertEquals(LocatorIntent.parse("internal:label=\"Email\"i").labelText, "Email");
        assertEquals(LocatorIntent.parse("internal:testid=[data-testid=\"save\"s]").testId, "save");
        assertEquals(LocatorIntent.parse("xpath=//input[@name='weight']").name, "weight");
    }

    @Test(description = "The same element scores 100%; a neighbouring field in the same form does not")
    public void sameElementVersusNeighbour() {
        ElementSnapshot weight = field("weight", "Weight*", 100);
        ScoredCandidate same = scorer.score(weight, field("weight", "Weight*", 100), 1.0);
        assertEquals(same.confidence, 100.0);
        assertTrue(same.contentEvidence);

        ScoredCandidate neighbour = scorer.score(weight, field("commodity", "Commodity", 140), 1.0);
        assertTrue(neighbour.confidence < 60, "neighbour scored " + neighbour.confidence);
    }

    @Test(description = "Matching only on role, structure and position is never evidence enough")
    public void structureAloneIsNotEvidence() {
        ElementSnapshot icon = new ElementSnapshot();
        icon.tag = "button";
        icon.role = "button";
        icon.ancestry = List.of("div", "header");
        icon.x = 10.0;
        icon.y = 10.0;
        ElementSnapshot otherIcon = new ElementSnapshot();
        otherIcon.tag = "button";
        otherIcon.role = "button";
        otherIcon.ancestry = List.of("div", "header");
        otherIcon.x = 50.0;
        otherIcon.y = 10.0;
        ScoredCandidate s = scorer.score(icon, otherIcon, 1.0);
        assertTrue(s.confidence > 90, "looks alike: " + s.confidence);
        assertFalse(s.contentEvidence, "but nothing identifies it as the same element");
    }

    @Test(description = "Without a baseline the score is scaled down (healing.noBaselineFactor)")
    public void noBaselineFactorApplies() {
        ElementSnapshot intent = LocatorIntent.parse("#companyName");
        ElementSnapshot renamed = field("company-name", "Company Name", 0);
        renamed.id = "company-name";
        assertEquals(scorer.score(intent, renamed, 0.95).confidence, 95.0);
    }

    private static ElementSnapshot field(String name, String label, double y) {
        ElementSnapshot s = new ElementSnapshot();
        s.tag = "input";
        s.type = "text";
        s.role = "textbox";
        s.id = name;
        s.name = name;
        s.labelText = label;
        s.accessibleName = label;
        s.ancestry = List.of("div", "form");
        s.heading = "Cargo";
        s.inDialog = false;
        s.x = 100.0;
        s.y = y;
        s.w = 200.0;
        s.h = 30.0;
        return s;
    }
}
