package com.portfolio.logistics.tests.healing;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.logistics.healing.Healing;
import com.portfolio.logistics.healing.core.HealingConfig;
import com.portfolio.logistics.healing.core.HealingEngine;
import com.portfolio.logistics.healing.core.HealingEvent;
import com.portfolio.logistics.healing.core.HealingMode;
import com.portfolio.logistics.healing.core.HealingRejectedException;
import com.portfolio.logistics.healing.core.scoring.ConfidenceScorer;
import com.portfolio.logistics.healing.core.scoring.ScoringProfile;
import com.portfolio.logistics.healing.core.store.JsonHealingStore;
import com.portfolio.logistics.healing.report.AllureHealingReporter;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Self-healing engine against local HTML pages: each test loads a "before" page (where the original
 * locator works and its baseline is recorded), then a changed "after" page, and checks the engine
 * heals what it should and refuses what it shouldn't. No staging access needed.
 *
 * The small methods like {@link #companyName(Page)} play the role of page-object locator methods:
 * baselines are keyed by the method that builds the locator.
 */
@Epic("Self-healing framework")
@Feature("Healing behaviour")
public class SelfHealingFixtureTest {

    private static final double TIMEOUT_MS = 1500;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page raw;
    private Path storeDir;

    @BeforeClass
    public void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterClass(alwaysRun = true)
    public void shutDown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeMethod
    public void freshPage() throws IOException {
        context = browser.newContext();
        raw = context.newPage();
        raw.setDefaultTimeout(TIMEOUT_MS);
        storeDir = Files.createTempDirectory("healing-store");
    }

    @AfterMethod(alwaysRun = true)
    public void closePage() {
        if (context != null) context.close();
    }

    // ---- "page object" locator methods ----

    private static Locator companyName(Page page) { return page.locator("#companyName"); }
    private static Locator weight(Page page)      { return page.locator("#weight"); }
    private static Locator saveButton(Page page)  { return page.locator("#save"); }
    private static Locator dialog(Page page)      { return page.locator("div[role='dialog']"); }
    private static Locator dialogConfirm(Page page) {
        return dialog(page).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Confirm"));
    }

    // ---- pages ----

    private static final String FORM_V1 = """
            <h2>New customer</h2>
            <form>
              <div><label for="companyName">Company Name</label><input id="companyName" name="company"></div>
              <div><label for="email">Email</label><input id="email" name="email"></div>
            </form>""";

    /** Developer renamed the id; label, name and position are unchanged. */
    private static final String FORM_V2_ID_RENAMED = """
            <h2>New customer</h2>
            <form>
              <div><label for="company-name-input">Company Name</label><input id="company-name-input" name="company"></div>
              <div><label for="email">Email</label><input id="email" name="email"></div>
            </form>""";

    @Test(description = "Renamed id is healed using the baseline recorded while the original worked")
    public void renamedIdHealsFromBaseline() {
        HealingEngine engine = engine(HealingMode.HEAL);
        Page page = Healing.wrap(raw, engine);

        raw.setContent(FORM_V1);
        companyName(page).fill("Acme");                       // original works → baseline recorded

        raw.setContent(FORM_V2_ID_RENAMED);
        companyName(page).fill("Acme Freight");               // original finds nothing → healed

        assertEquals(raw.locator("#company-name-input").inputValue(), "Acme Freight");
        assertEquals(raw.locator("#email").inputValue(), "", "the neighbouring field must be untouched");
        HealingEvent e = single(engine);
        assertEquals(e.decision, "HEALED");
        assertEquals(e.strategy, "BASELINE");
        assertTrue(e.confidence >= 90, "confidence " + e.confidence);
        assertNotNull(e.suggestedCode);
    }

    @Test(description = "Without a baseline, a near-identical id is healed from the selector's intent")
    public void renamedIdHealsWithoutBaseline() {
        HealingEngine engine = engine(HealingMode.HEAL);
        Page page = Healing.wrap(raw, engine);
        raw.setContent("<form><input id='company-name'><input id='email'></form>");

        companyName(page).fill("Acme");

        assertEquals(raw.locator("#company-name").inputValue(), "Acme");
        HealingEvent e = single(engine);
        assertEquals(e.strategy, "SELECTOR_INTENT");
        assertEquals(e.confidence, 95.0, "identity match × no-baseline factor 0.95");
    }

    @Test(description = "A removed field is NOT healed onto a neighbouring field; the original error is thrown")
    public void removedFieldFailsWithOriginalError() {
        HealingEngine engine = engine(HealingMode.HEAL);
        Page page = Healing.wrap(raw, engine);
        raw.setContent("""
                <h3>Cargo</h3><form>
                <div><label for="weight">Weight*</label><input id="weight" name="weight"></div>
                <div><label for="commodity">Commodity</label><input id="commodity" name="commodity"></div></form>""");
        weight(page).fill("5000");

        raw.setContent("""
                <h3>Cargo</h3><form>
                <div><label for="commodity">Commodity</label><input id="commodity" name="commodity"></div></form>""");
        TimeoutError err = expectThrows(TimeoutError.class, () -> weight(page).fill("5000"));

        assertEquals(raw.locator("#commodity").inputValue(), "");
        assertTrue(Arrays.stream(err.getSuppressed()).anyMatch(s -> s instanceof HealingRejectedException),
                "the engine's reasoning is attached to the original error");
        assertEquals(single(engine).decision, "LOW_CONFIDENCE");
    }

    @Test(description = "Two equally good look-alikes: ambiguous, nothing is clicked")
    public void lookAlikeElementsAreAmbiguous() {
        HealingEngine engine = engine(HealingMode.HEAL);
        Page page = Healing.wrap(raw, engine);
        String counter = "<script>window.clicks = 0</script>";
        raw.setContent(counter + "<form><div><button type='button' id='save' name='save' onclick='clicks++'>Save</button></div></form>");
        saveButton(page).click();

        raw.setContent(counter
                + "<form><div><button type='button' name='save' onclick='clicks++'>Save</button></div></form>"
                + "<form><div><button type='button' name='save' onclick='clicks++'>Save</button></div></form>");
        expectThrows(TimeoutError.class, () -> saveButton(page).click());

        assertEquals(raw.evaluate("window.clicks"), 0);
        assertEquals(single(engine).decision, "AMBIGUOUS");
    }

    @Test(description = "Original exists but is disabled: app state, no healing attempt at all")
    public void disabledOriginalIsNotHealed() {
        HealingEngine engine = engine(HealingMode.HEAL);
        Page page = Healing.wrap(raw, engine);
        raw.setContent("<script>window.clicks = 0</script>"
                + "<button id='save' disabled>Save</button><button id='save2' onclick='clicks++'>Save</button>");

        TimeoutError err = expectThrows(TimeoutError.class, () -> saveButton(page).click());

        assertEquals(raw.evaluate("window.clicks"), 0);
        assertTrue(engine.store().eventsThisRun().isEmpty());
        assertEquals(err.getSuppressed().length, 0, "error is exactly what Playwright threw");
    }

    @Test(description = "count() and isVisible() are never healed and never slowed down")
    public void presenceChecksAreNeverHealed() {
        HealingEngine engine = engine(HealingMode.HEAL);
        Page page = Healing.wrap(raw, engine);
        raw.setContent(FORM_V2_ID_RENAMED);

        long start = System.currentTimeMillis();
        assertEquals(companyName(page).count(), 0);
        assertFalse(companyName(page).isVisible());
        assertTrue(System.currentTimeMillis() - start < 1000);
        assertTrue(engine.store().eventsThisRun().isEmpty());
    }

    @Test(description = "A locator scoped to a dialog is never healed onto a button outside it")
    public void containerScopeIsRespected() {
        HealingEngine engine = engine(HealingMode.HEAL);
        Page page = Healing.wrap(raw, engine);
        raw.setContent("<script>window.clicks = 0</script>"
                + "<div role='dialog'><p>Are you sure?</p><button>Confirm</button></div>");
        dialogConfirm(page).click();

        raw.setContent("<script>window.clicks = 0</script><button onclick='clicks++'>Confirm</button>");
        expectThrows(TimeoutError.class, () -> dialogConfirm(page).click());

        assertEquals(raw.evaluate("window.clicks"), 0);
        assertEquals(single(engine).decision, "NO_CANDIDATES");
    }

    @Test(description = "An action with a short explicit timeout is a probe: never healed")
    public void shortExplicitTimeoutIsAProbe() {
        HealingEngine engine = engine(HealingMode.HEAL);
        Page page = Healing.wrap(raw, engine);
        raw.setContent("<input id='company-name'>");

        expectThrows(TimeoutError.class,
                () -> companyName(page).fill("x", new Locator.FillOptions().setTimeout(500)));
        assertTrue(engine.store().eventsThisRun().isEmpty());
    }

    @Test(description = "SUGGEST mode reports the replacement but the test still fails")
    public void suggestModeReportsButFails() {
        HealingEngine engine = engine(HealingMode.SUGGEST);
        Page page = Healing.wrap(raw, engine);
        raw.setContent(FORM_V1);
        companyName(page).fill("Acme");
        raw.setContent(FORM_V2_ID_RENAMED);

        expectThrows(TimeoutError.class, () -> companyName(page).fill("Acme Freight"));

        assertEquals(raw.locator("#company-name-input").inputValue(), "");
        HealingEvent e = single(engine);
        assertEquals(e.decision, "SUGGESTED");
        assertNotNull(e.healed);
    }

    @Test(description = "healing.mode=OFF hands back the page untouched")
    public void offModeReturnsTheRawPage() {
        assertSame(Healing.wrap(raw, engine(HealingMode.OFF)), raw);
    }

    @Test(description = "Locators passed back into Playwright (filter has, or, locator(Locator)) still work")
    public void locatorsPassedBackIntoPlaywrightWork() {
        Page page = Healing.wrap(raw, engine(HealingMode.HEAL));
        raw.setContent("<div role='dialog'><h2>Create Incorporation</h2><input></div><div role='dialog'><h2>Pick</h2></div>");

        Locator modal = page.locator("div[role='dialog']").filter(new Locator.FilterOptions()
                .setHas(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Create Incorporation"))));
        assertEquals(modal.count(), 1);
        assertEquals(page.locator("h2").or(page.locator("input")).count(), 3);
        assertEquals(modal.locator(page.locator("input")).count(), 1);
        assertSame(modal.page(), page);
    }

    @Test(description = "Reads (innerText etc.) are not healed by default — their values feed assertions")
    public void readsAreNotHealedByDefault() {
        HealingEngine engine = engine(HealingMode.HEAL);
        Page page = Healing.wrap(raw, engine);
        raw.setContent("<span id='status-label'>Paid</span>");

        expectThrows(TimeoutError.class, () -> page.locator("#status").innerText());
        assertTrue(engine.store().eventsThisRun().isEmpty());
    }

    @Test(description = "A locator broken earlier in the run heals after the short probe, not the full timeout")
    public void knownBrokenLocatorHealsQuickly() {
        HealingEngine engine = engine(HealingMode.HEAL);
        Page page = Healing.wrap(raw, engine);
        raw.setContent(FORM_V1);
        companyName(page).fill("Acme");
        raw.setContent(FORM_V2_ID_RENAMED);
        companyName(page).fill("first");                      // full timeout, then heal

        long start = System.currentTimeMillis();
        companyName(page).fill("second");                     // known broken: short probe, then heal
        long took = System.currentTimeMillis() - start;

        assertEquals(raw.locator("#company-name-input").inputValue(), "second");
        assertTrue(took < TIMEOUT_MS, "took " + took + " ms; expected well under the " + TIMEOUT_MS + " ms timeout");
    }

    @Test(description = "Baselines persist across runs (store written and read back)")
    public void baselinePersistsAcrossRuns() {
        HealingEngine firstRun = engine(HealingMode.HEAL);
        raw.setContent(FORM_V1);
        companyName(Healing.wrap(raw, firstRun)).fill("Acme");
        firstRun.store().flush();

        HealingEngine secondRun = engine(HealingMode.HEAL);   // new store instance reading the same folder
        raw.setContent(FORM_V2_ID_RENAMED);
        companyName(Healing.wrap(raw, secondRun)).fill("Acme Freight");

        assertEquals(single(secondRun).strategy, "BASELINE");
        assertEquals(raw.locator("#company-name-input").inputValue(), "Acme Freight");
    }

    @Test(description = "If the action fails on the healed element, that replacement is blocked next time")
    public void failedHealIsBlockedNextTime() {
        HealingEngine engine = engine(HealingMode.HEAL);
        Page page = Healing.wrap(raw, engine);
        raw.setContent("<form><div><button type='button' id='save' name='save'>Save</button></div></form>");
        saveButton(page).click();

        // Renamed, but covered by an overlay so the click can't land.
        String covered = "<form><div><button type='button' id='save-btn' name='save'>Save</button></div></form>"
                + "<div style='position:fixed;inset:0;background:rgba(0,0,0,.1)'></div>";
        raw.setContent(covered);
        expectThrows(TimeoutError.class, () -> saveButton(page).click());
        assertEquals(single(engine).decision, "HEALED");

        raw.setContent(covered);
        expectThrows(TimeoutError.class, () -> saveButton(page).click());
        List<HealingEvent> events = engine.store().eventsThisRun();
        assertEquals(events.get(events.size() - 1).decision, "BLACKLISTED");
    }

    // ---- helpers ----

    private HealingEngine engine(HealingMode mode) {
        HealingConfig cfg = HealingConfig.builder()
                .mode(mode)
                .storeDir(storeDir)
                .knownBrokenProbeMs(300)
                .build();
        HealingEngine e = new HealingEngine(cfg, new JsonHealingStore(storeDir),
                new ConfidenceScorer(ScoringProfile.web()), null);
        e.addReporter(new AllureHealingReporter());
        return e;
    }

    private static HealingEvent single(HealingEngine engine) {
        List<HealingEvent> events = engine.store().eventsThisRun();
        assertEquals(events.size(), 1, "heal events: " + events.stream().map(HealingEvent::oneLine).toList());
        return events.get(0);
    }
}
