package com.portfolio.logistics.healing.playwright;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.logistics.healing.core.ActionKind;
import com.portfolio.logistics.healing.core.DriverAdapter;
import com.portfolio.logistics.healing.core.ElementSnapshot;
import com.portfolio.logistics.healing.core.LocatorSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Playwright side of a heal: sweeps the DOM (inside the original locator's container, or the whole
 * page) with {@code healing/dom-snapshot.js}, and builds replacement locators that are verified to
 * hit exactly the chosen element.
 */
final class PlaywrightAdapter implements DriverAdapter {

    private static final String LIB = loadLib();
    static final String SNAPSHOT_FN = "el => {\n" + LIB + "\nreturn H.snapshot(el);\n}";
    private static final String COLLECT_IN_FN = "(roots, opts) => {\n" + LIB + "\nreturn H.collect(roots, opts);\n}";
    private static final String COLLECT_PAGE_FN = "opts => {\n" + LIB + "\nreturn H.collect([document.documentElement], opts);\n}";
    private static final String VERIFY_FN =
            "(els, i) => els.length === 1 && !!window.__healCandidates && els[0] === window.__healCandidates[i]";

    private static final Gson GSON = new Gson();
    private static final Pattern SIMPLE_ID = Pattern.compile("[A-Za-z][\\w-]*");
    private static final int MAX_NAME = 80;
    private static final int MAX_VERIFIED = 3;

    private final Page page;
    /** Container the original locator searched in; null = whole page. */
    private final Locator scope;
    private final String scopeSelector;
    private final int maxCandidates;

    PlaywrightAdapter(Page page, Locator scope, String scopeSelector, int maxCandidates) {
        this.page = page;
        this.scope = scope;
        this.scopeSelector = scopeSelector;
        this.maxCandidates = maxCandidates;
    }

    @Override
    public List<ElementSnapshot> candidates(ActionKind action) {
        Map<String, Object> opts = Map.of("action", action.name(), "max", maxCandidates);
        Object raw = scope != null ? scope.evaluateAll(COLLECT_IN_FN, opts) : page.evaluate(COLLECT_PAGE_FN, opts);
        return GSON.fromJson(GSON.toJsonTree(raw), new TypeToken<List<ElementSnapshot>>() { }.getType());
    }

    @Override
    public List<LocatorSpec> locatorsFor(ElementSnapshot c) {
        List<LocatorSpec> verified = new ArrayList<>();
        for (LocatorSpec spec : proposals(c)) {
            if (verify(spec, c.index)) {
                verified.add(spec);
                if (verified.size() >= MAX_VERIFIED) break;
            }
        }
        return verified;
    }

    @Override
    public String location() {
        return pathOf(page.url());
    }

    /** URL path only — no host, no query string (which may carry ids or tokens). */
    static String pathOf(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null || path.isEmpty() ? url : path;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    @Override
    public String platform() {
        return "web";
    }

    /** Build the native locator for a spec, relative to the same container as the original. */
    Locator toLocator(LocatorSpec spec) {
        boolean inScope = spec.scoped && scope != null;
        switch (spec.kind) {
            case "ROLE": {
                AriaRole role = AriaRole.valueOf(spec.role.toUpperCase(Locale.ROOT));
                return inScope
                        ? scope.getByRole(role, new Locator.GetByRoleOptions().setName(spec.value).setExact(true))
                        : page.getByRole(role, new Page.GetByRoleOptions().setName(spec.value).setExact(true));
            }
            case "LABEL":
                return inScope ? scope.getByLabel(spec.value, new Locator.GetByLabelOptions().setExact(true))
                        : page.getByLabel(spec.value, new Page.GetByLabelOptions().setExact(true));
            case "PLACEHOLDER":
                return inScope ? scope.getByPlaceholder(spec.value, new Locator.GetByPlaceholderOptions().setExact(true))
                        : page.getByPlaceholder(spec.value, new Page.GetByPlaceholderOptions().setExact(true));
            case "TEXT":
                return inScope ? scope.getByText(spec.value, new Locator.GetByTextOptions().setExact(true))
                        : page.getByText(spec.value, new Page.GetByTextOptions().setExact(true));
            default:
                return inScope ? scope.locator(spec.value) : page.locator(spec.value);
        }
    }

    static ElementSnapshot fingerprint(Locator element) {
        Object raw = element.evaluate(SNAPSHOT_FN, null, new Locator.EvaluateOptions().setTimeout(2000));
        return GSON.fromJson(GSON.toJsonTree(raw), ElementSnapshot.class);
    }

    /** Most stable first: test id, id, name, role+name, label, placeholder, text, positional CSS. */
    private List<LocatorSpec> proposals(ElementSnapshot c) {
        List<LocatorSpec> out = new ArrayList<>();
        if (present(c.testId)) out.add(css("[" + (present(c.testIdAttr) ? c.testIdAttr : "data-testid") + "=\"" + cssEsc(c.testId) + "\"]"));
        if (present(c.id)) out.add(css(SIMPLE_ID.matcher(c.id).matches() ? "#" + c.id : "[id=\"" + cssEsc(c.id) + "\"]"));
        if (present(c.name) && present(c.tag)) out.add(css(c.tag + "[name=\"" + cssEsc(c.name) + "\"]"));
        if (present(c.role) && fits(c.accessibleName) && isAriaRole(c.role)) out.add(spec("ROLE", c.accessibleName, c.role));
        if (fits(c.labelText)) out.add(spec("LABEL", c.labelText, null));
        if (fits(c.placeholder)) out.add(spec("PLACEHOLDER", c.placeholder, null));
        if (fits(c.text) && !present(c.labelText)) out.add(spec("TEXT", c.text, null));
        if (present(c.cssPath)) out.add(css(c.cssPath));
        return out;
    }

    private boolean verify(LocatorSpec spec, Integer index) {
        try {
            return Boolean.TRUE.equals(toLocator(spec).evaluateAll(VERIFY_FN, index));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private LocatorSpec css(String selector) {
        return spec("CSS", selector, null);
    }

    private LocatorSpec spec(String kind, String value, String role) {
        LocatorSpec s = new LocatorSpec(kind, value, role, scope != null);
        s.javaCode = javaCode(s);
        return s;
    }

    /** The line a person would put in the page object. */
    private String javaCode(LocatorSpec s) {
        boolean inScope = s.scoped;
        String base = inScope ? "scope" : "page";
        String opts = inScope ? "Locator" : "Page";
        String v = javaEsc(s.value);
        String code = switch (s.kind) {
            case "ROLE" -> base + ".getByRole(AriaRole." + s.role.toUpperCase(Locale.ROOT) + ", new " + opts
                    + ".GetByRoleOptions().setName(\"" + v + "\").setExact(true))";
            case "LABEL" -> base + ".getByLabel(\"" + v + "\", new " + opts + ".GetByLabelOptions().setExact(true))";
            case "PLACEHOLDER" -> base + ".getByPlaceholder(\"" + v + "\", new " + opts + ".GetByPlaceholderOptions().setExact(true))";
            case "TEXT" -> base + ".getByText(\"" + v + "\", new " + opts + ".GetByTextOptions().setExact(true))";
            default -> base + ".locator(\"" + v + "\")";
        };
        return inScope ? code + "   // scope = " + scopeSelector : code;
    }

    private static boolean isAriaRole(String role) {
        try {
            AriaRole.valueOf(role.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean fits(String s) {
        return present(s) && s.length() <= MAX_NAME;
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static String cssEsc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String javaEsc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String loadLib() {
        try (InputStream in = PlaywrightAdapter.class.getClassLoader().getResourceAsStream("healing/dom-snapshot.js")) {
            if (in == null) throw new IllegalStateException("healing/dom-snapshot.js missing from the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
