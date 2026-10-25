package com.portfolio.logistics.utils;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Records the backend API calls a page makes, so a test can assert on what the UI actually sent and
 * what the server actually answered — not just on whether a dialog closed.
 *
 * Several of the defects this suite covers are invisible from the DOM alone: a field that never
 * reaches the payload, or a save that the UI treats as fine while the API answered 400. Attach a
 * watcher right after login and assert against {@link #lastCall(String)}.
 *
 * Only non-GET calls to the TMS backend are recorded; GETs and telemetry/Sentry traffic are noise.
 */
public final class ApiWatcher {

    /** One recorded write call: what the UI sent and what came back. */
    public static final class Call {
        public final String method;
        public final String url;
        public final String requestBody;
        public final int status;
        public final String responseBody;

        Call(String method, String url, String requestBody, int status, String responseBody) {
            this.method = method;
            this.url = url;
            this.requestBody = requestBody == null ? "" : requestBody;
            this.status = status;
            this.responseBody = responseBody == null ? "" : responseBody;
        }

        public boolean ok() { return status >= 200 && status < 300; }

        /** True if the request payload carried {key} with a non-empty value (naive JSON probe). */
        public boolean sent(String key, String value) {
            return requestBody.contains("\"" + key + "\":\"" + value + "\"")
                    || requestBody.contains("\"" + key + "\":" + value);
        }

        /** True if the payload carried {key} at all (any value, including null/empty). */
        public boolean sentKey(String key) {
            return requestBody.contains("\"" + key + "\"");
        }

        @Override
        public String toString() {
            return method + " " + url + " -> HTTP " + status
                    + "\n  request : " + trim(requestBody)
                    + "\n  response: " + trim(responseBody);
        }

        private static String trim(String s) {
            String one = s.replaceAll("\\s+", " ").trim();
            return one.length() > 600 ? one.substring(0, 600) + "…" : one;
        }
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();

    private ApiWatcher() { }

    /** Start recording write calls made by {page}. Call once, straight after login. */
    public static ApiWatcher attach(Page page) {
        ApiWatcher watcher = new ApiWatcher();
        page.onResponse(response -> {
            Request request = response.request();
            if ("GET".equalsIgnoreCase(request.method())) return;
            if (!isBackend(response.url())) return;
            String body;
            try { body = response.text(); } catch (Exception e) { body = "<body unavailable: " + e.getMessage() + ">"; }
            watcher.calls.add(new Call(request.method(), response.url(),
                    request.postData(), response.status(), body));
        });
        return watcher;
    }

    /** The API host is a different origin from the frontend; ignore same-origin telemetry tunnels. */
    private static boolean isBackend(String url) {
        return url.contains("/api/") && !url.contains("/api/tunnel");
    }

    /** True if the UI made at least one write call to {urlPart}. */
    public boolean called(String urlPart) {
        return !calls(urlPart).isEmpty();
    }

    /** Every recorded call whose URL contains {urlPart}. */
    public List<Call> calls(String urlPart) {
        return calls.stream()
                .filter(c -> c.url.toLowerCase(Locale.ROOT).contains(urlPart.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    /** The most recent call whose URL contains {urlPart}, if the UI made one at all. */
    public Optional<Call> lastCall(String urlPart) {
        List<Call> matches = calls(urlPart);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(matches.size() - 1));
    }

    /**
     * The most recent call to {urlPart}, or an AssertionError naming what WAS seen. A missing call is
     * itself a finding: the UI never submitted, so the test must fail rather than pass quietly.
     */
    public Call requireCall(String urlPart) {
        return lastCall(urlPart).orElseThrow(() -> new AssertionError(
                "The UI never called " + urlPart + " — the form did not submit. Calls seen: " + summary()));
    }

    /** Every recorded call, one per line — useful in assertion messages. */
    public String summary() {
        if (calls.isEmpty()) return "(no write calls recorded)";
        return "\n  " + calls.stream()
                .map(c -> c.method + " " + c.url + " -> " + c.status)
                .collect(Collectors.joining("\n  "));
    }

    /** Forget everything recorded so far (e.g. between two actions in one test). */
    public void clear() {
        calls.clear();
    }
}
