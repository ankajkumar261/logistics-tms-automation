package com.portfolio.logistics.utils;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import com.portfolio.logistics.config.ConfigReader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Establishes an authenticated session WITHOUT driving the login form.
 *
 * The Logistics TMS LIVE login UI is intermittently a no-op in headless runs (a single Sign-In click
 * occasionally does not submit), which would turn any authenticated test into a flaky failure. The
 * backend auth API, by contrast, is 100% reliable. So for tests whose subject is NOT the login page
 * itself, we authenticate through the API and hand the browser the resulting token the same way the
 * app does (the {@code tiq_token} cookie), then load the app already signed in.
 *
 * The dedicated auth/role-login tests still exercise the real login FORM — this is only for tests
 * that merely need a signed-in session to reach the feature under test.
 */
public final class ApiSession {

    /** Backend origin derived from the frontend base URL (staging.logistics.com -> staging-backend.logistics.com). */
    private static String backendBase() {
        String base = ConfigReader.baseUrl().replaceAll("/+$", "");
        if (base.contains("://staging.logistics.com")) return base.replace("://staging.logistics.com", "://staging-backend.logistics.com");
        if (base.contains("tms-frontend")) return base.replace("tms-frontend", "tms-backend");
        // Fallback: assume an api subpath on the same origin.
        return base;
    }

    private ApiSession() { }

    /** POST the credentials to the backend and return the JWT, or throw with the server's answer. */
    public static String fetchToken(String corporateId, String username, String password) {
        String url = backendBase() + "/api/auth/login";
        String body = String.format(
                "{\"corporateId\":\"%s\",\"username\":\"%s\",\"password\":\"%s\"}",
                corporateId, username, password);
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new IllegalStateException("Auth API " + url + " returned HTTP " + res.statusCode()
                        + " for " + corporateId + "/" + username + " -> " + res.body());
            }
            Matcher m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(res.body());
            if (!m.find()) {
                throw new IllegalStateException("Auth API returned no token: " + res.body());
            }
            return m.group(1);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Auth API call to " + url + " failed: " + e.getMessage(), e);
        }
    }

    /** The frontend host for the current base URL. */
    private static String frontendHost() {
        try { return URI.create(ConfigReader.baseUrl()).getHost(); }
        catch (Exception e) { return null; }
    }

    /**
     * Cookie domains the tiq_token must be set on so it reaches BOTH the frontend and the backend.
     * The two live on different subdomains (tms-frontend / tms-backend .example.co.in), so a cookie
     * scoped to the frontend host alone is never sent to the backend and the app reads as logged out.
     * We therefore also scope it to the shared parent domain (.example.co.in / .logistics.com).
     */
    private static List<String> cookieDomains(String host) {
        java.util.LinkedHashSet<String> domains = new java.util.LinkedHashSet<>();
        if (host == null) return new java.util.ArrayList<>(domains);
        domains.add(host);            // exact host
        domains.add("." + host);      // host + its subdomains
        String[] labels = host.split("\\.");
        // Add progressively broader parents down to the registrable domain, covering sibling
        // subdomains like tms-backend.* while never going as broad as a public suffix (>=2 labels).
        for (int i = 1; i < labels.length - 1; i++) {
            StringBuilder parent = new StringBuilder();
            for (int j = i; j < labels.length; j++) {
                if (parent.length() > 0) parent.append('.');
                parent.append(labels[j]);
            }
            if (parent.toString().contains(".")) domains.add("." + parent);
        }
        return new java.util.ArrayList<>(domains);
    }

    /**
     * Authenticate {corporateId}/{username}/{password} via the API and load the app already signed
     * in. Sets the tiq_token cookie (scoped so it reaches the backend subdomain too) plus a
     * localStorage mirror injected before the SPA boots, then navigates to /dashboard.
     */
    public static void establish(Page page, String corporateId, String username, String password) {
        String token = fetchToken(corporateId, username, password);
        BrowserContext ctx = page.context();
        String host = frontendHost();
        java.util.List<Cookie> cookies = new java.util.ArrayList<>();
        for (String domain : cookieDomains(host)) {
            cookies.add(new Cookie("tiq_token", token).setDomain(domain).setPath("/"));
        }
        if (!cookies.isEmpty()) ctx.addCookies(cookies);

        // Also mirror the token into localStorage BEFORE any page script runs (some builds read it
        // from there and send an Authorization header), so auth holds regardless of cookie scoping.
        try {
            ctx.addInitScript("(() => { try { localStorage.setItem('tiq_token', '" + token
                    + "'); localStorage.setItem('token', '" + token + "'); } catch (e) {} })()");
        } catch (Exception ignored) { }

        page.navigate(ConfigReader.baseUrl() + "/dashboard");
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE); } catch (Exception ignored) { }
    }
}
