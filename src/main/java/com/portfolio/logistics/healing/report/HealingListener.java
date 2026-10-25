package com.portfolio.logistics.healing.report;

import com.portfolio.logistics.healing.Healing;
import com.portfolio.logistics.healing.core.HealingConfig;
import com.portfolio.logistics.healing.core.HealingContext;
import com.portfolio.logistics.healing.core.HealingEngine;
import com.portfolio.logistics.healing.core.HealingEvent;
import com.portfolio.logistics.healing.core.HealingMode;
import org.testng.IExecutionListener;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ties heal events to TestNG results. Registered for every suite through
 * META-INF/services/org.testng.ITestNGListener, so the suite XMLs don't need to list it.
 *
 * TestNG 7.10 order per test: @BeforeMethod → onTestStart → test → afterInvocation → onTestSuccess /
 * onTestFailure (allure-testng writes the result here) → @AfterMethod. PASS_WITH_HEALING is therefore
 * applied in afterInvocation, while the Allure test case is still open.
 */
public class HealingListener implements ITestListener, IInvokedMethodListener, IExecutionListener {

    @Override
    public void onExecutionStart() {
        if (HealingConfig.load().mode != HealingMode.OFF) AllureResults.ensureCategory();
    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult result) {
        if (method.isConfigurationMethod() && method.getTestMethod().isBeforeMethodConfiguration()) {
            HealingContext.beginSetup();
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        HealingContext.beginTest(testId(result));
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult result) {
        if (!method.isTestMethod()) return;
        List<HealingEvent> applied = HealingContext.testEvents().stream().filter(e -> e.applied).toList();
        if (!applied.isEmpty()) AllureResults.markTest(result.getStatus() == ITestResult.SUCCESS, applied);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        HealingContext.endTest(HealingEvent.Outcome.PASSED);
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        HealingContext.endTest(HealingEvent.Outcome.PASSED);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        HealingContext.endTest(HealingEvent.Outcome.FAILED);
    }

    @Override
    public void onTestFailedWithTimeout(ITestResult result) {
        HealingContext.endTest(HealingEvent.Outcome.FAILED);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        HealingContext.endTest(result.wasRetried() ? HealingEvent.Outcome.RETRIED : HealingEvent.Outcome.SKIPPED);
    }

    @Override
    public void onExecutionFinish() {
        if (!Healing.isInitialized()) return; // no page was wrapped in this run
        HealingEngine engine = Healing.engine();
        engine.store().flush();
        HealingSummary.write(engine);
    }

    static String testId(ITestResult r) {
        String id = r.getTestClass().getRealClass().getSimpleName() + "." + r.getMethod().getMethodName();
        Object[] params = r.getParameters();
        if (params != null && params.length > 0) {
            id += Arrays.stream(params).map(String::valueOf).collect(Collectors.joining(",", "(", ")"));
        }
        return id;
    }
}
