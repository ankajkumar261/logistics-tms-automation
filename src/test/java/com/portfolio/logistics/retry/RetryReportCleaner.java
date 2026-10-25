package com.portfolio.logistics.retry;

import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the reported Total/Passed/Failed/Skipped honest when {@link RetryAnalyzer} is active.
 *
 * TestNG records every superseded attempt of a retried test as SKIPPED, so a suite that retries
 * transient failures reports phantom skips alongside each test's real outcome — a suite where
 * nothing was skipped still prints "Skipped: 9". This listener drops those intermediate entries at
 * the end of the run, leaving exactly one result per test with the status it finally reached.
 *
 * It only removes a skip that is contradicted by a pass or failure of the same test. A test that
 * genuinely skipped has no other result and is left exactly as it is.
 */
public class RetryReportCleaner implements ITestListener {

    @Override
    public void onFinish(ITestContext context) {
        Set<String> decided = new HashSet<>();
        for (ITestResult r : context.getPassedTests().getAllResults()) decided.add(key(r));
        for (ITestResult r : context.getFailedTests().getAllResults()) decided.add(key(r));

        List<ITestResult> retryArtefacts = new ArrayList<>();
        for (ITestResult skipped : context.getSkippedTests().getAllResults()) {
            if (decided.contains(key(skipped))) {
                retryArtefacts.add(skipped);
            }
        }
        for (ITestResult artefact : retryArtefacts) {
            context.getSkippedTests().removeResult(artefact);
        }

        if (!retryArtefacts.isEmpty()) {
            System.out.println("[RetryReportCleaner] Removed " + retryArtefacts.size()
                    + " superseded retry attempt(s) from the skipped count for '"
                    + context.getName() + "'.");
        }
    }

    /** Identity of a test invocation: class + method + parameters (data-provider rows differ). */
    private static String key(ITestResult r) {
        StringBuilder sb = new StringBuilder(r.getTestClass().getName())
                .append('#').append(r.getMethod().getMethodName());
        Object[] params = r.getParameters();
        if (params != null && params.length > 0) {
            sb.append('(');
            for (Object p : params) sb.append(String.valueOf(p)).append(',');
            sb.append(')');
        }
        return sb.toString();
    }
}
