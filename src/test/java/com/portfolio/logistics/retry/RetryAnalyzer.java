package com.portfolio.logistics.retry;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Retries a failed test up to MAX_RETRIES times. The suite runs UI tests against a shared, latency-
 * variable staging environment where transient failures (net::ERR_ABORTED on SPA navigation, nav/menu
 * render races, list-search timing under parallel load) are unavoidable and move between runs. A fresh
 * BrowserContext + navigation on retry clears them; a genuinely broken feature still fails every attempt.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final int MAX_RETRIES = 2; // up to 3 total attempts

    private int attempts = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (attempts < MAX_RETRIES) {
            attempts++;
            System.out.println("[Retry] " + result.getTestClass().getRealClass().getSimpleName()
                    + "::" + result.getName() + " — attempt " + (attempts + 1) + " of " + (MAX_RETRIES + 1));
            return true;
        }
        return false;
    }
}
