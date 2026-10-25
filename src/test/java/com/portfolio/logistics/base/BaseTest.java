package com.portfolio.logistics.base;

import com.microsoft.playwright.Page;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.drivers.BrowserManager;
import com.portfolio.logistics.healing.Healing;
import com.portfolio.logistics.utils.ApiWatcher;
import io.qameta.allure.Allure;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base for all Logistics TMS tests. Handles:
 *  - fresh isolated browser/page per test method
 *  - one navigation retry (transient ERR_CONNECTION_REFUSED shouldn't cascade
 *    the whole suite into skips — same fix we added to the E-commerce BaseTest)
 *  - console error capture (Module 0 asserts a clean console)
 *  - an {@link ApiWatcher} on every page, so a test can assert on what the UI actually sent to the
 *    backend and what came back — several defects are only visible there, not in the DOM
 *  - screenshot-on-failure attached to the Allure report
 *  - self-healing locators ({@link Healing}): healing.* in config.properties, healing.mode=OFF disables
 */
public abstract class BaseTest {

    protected Page page;
    /** Records the backend write calls this test's page makes. See {@link ApiWatcher}. */
    protected ApiWatcher api;
    private final List<String> consoleErrors = new CopyOnWriteArrayList<>();

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        // Self-healing locators: page objects and tests are unchanged; a locator that finds nothing is
        // replaced only at >= 90% confidence, and the test is then reported as PASS_WITH_HEALING.
        page = Healing.wrap(BrowserManager.startPage());

        // TestNG reuses one instance per class, so errors from the previous method would otherwise
        // leak into this one and fail an unrelated assertion.
        consoleErrors.clear();

        // Collect console errors so smoke tests can assert a clean console.
        page.onConsoleMessage(msg -> {
            if ("error".equalsIgnoreCase(msg.type())) {
                consoleErrors.add(msg.text());
            }
        });

        api = ApiWatcher.attach(page);

        navigateWithRetry(ConfigReader.baseUrl());
    }

    /** Navigate, retrying once after a short pause on transient network failure. */
    protected void navigateWithRetry(String url) {
        try {
            page.navigate(url);
        } catch (Exception first) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            page.navigate(url); // second failure is a real one — let it surface
        }
    }

    protected List<String> consoleErrors() {
        return Collections.unmodifiableList(consoleErrors);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        try {
            if (result.getStatus() == ITestResult.FAILURE && page != null) {
                byte[] shot = page.screenshot();
                Allure.addAttachment(
                        "Failure — " + result.getName(),
                        "image/png",
                        new ByteArrayInputStream(shot),
                        "png");
                // The API traffic is usually what explains the failure; attach it next to the shot.
                if (api != null) {
                    Allure.addAttachment("API calls — " + result.getName(), "text/plain", api.summary());
                }
            }
        } catch (Exception ignored) {
            // never let teardown reporting mask the real failure
        } finally {
            BrowserManager.stop();
        }
    }
}
