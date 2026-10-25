package com.portfolio.logistics.healing.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what a selector was looking for, for heals where no baseline fingerprint exists yet (the
 * locator has never worked since healing was installed). Understands Playwright's selector strings:
 *
 * <pre>
 *   #companyName                                   → id
 *   input[name='weight']                           → tag + name
 *   internal:role=button[name="Add Customer"i]     → role + accessible name   (getByRole)
 *   internal:text="Invoices"i / text=Invoices      → text                     (getByText)
 *   internal:label="Email"i                        → label                    (getByLabel)
 *   internal:attr=[placeholder="Search"i]          → placeholder              (getByPlaceholder)
 *   internal:testid=[data-testid="save"s]          → test id                  (getByTestId)
 *   xpath=//input[@id='x']                         → id (and @name, text(), tag)
 * </pre>
 *
 * Chained selectors ({@code a >> b >> nth=0}) describe their last element. Regex names (getByRole
 * with a Pattern) carry no single expected value and are ignored.
 */
public final class LocatorIntent {

    private static final Pattern ROLE = Pattern.compile("^internal:role=([a-z]+)(.*)$");
    private static final Pattern ROLE_NAME = Pattern.compile("\\[name=\"((?:[^\"\\\\]|\\\\.)*)\"[is]?]");
    private static final Pattern QUOTED = Pattern.compile("^\"((?:[^\"\\\\]|\\\\.)*)\"[is]?$");
    private static final Pattern ATTR = Pattern.compile("^internal:(?:attr|testid)=\\[([\\w-]+)=\"((?:[^\"\\\\]|\\\\.)*)\"[is]?]$");
    private static final Pattern HAS_TEXT = Pattern.compile("^internal:has-text=\"((?:[^\"\\\\]|\\\\.)*)\"[is]?$");

    private static final Pattern CSS_ID = Pattern.compile("#([A-Za-z_][\\w-]*)");
    private static final Pattern CSS_ATTR = Pattern.compile("\\[([\\w-]+)\\s*([*^$~|]?=)\\s*(?:'([^']*)'|\"([^\"]*)\"|([^\\]\\s]+))(?:\\s+[is])?]");
    private static final Pattern CSS_HAS_TEXT = Pattern.compile(":(?:has-text|text|text-is)\\(\\s*(?:'([^']*)'|\"([^\"]*)\")\\s*\\)");
    private static final Pattern CSS_TAG = Pattern.compile("^([a-zA-Z][a-zA-Z0-9]*)");

    private static final Pattern XP_ATTR = Pattern.compile("@([\\w-]+)\\s*=\\s*(?:'([^']*)'|\"([^\"]*)\")");
    private static final Pattern XP_TEXT = Pattern.compile("(?:text\\(\\)|normalize-space\\(\\)|\\.)\\s*,?\\s*=?\\s*(?:'([^']*)'|\"([^\"]*)\")");
    private static final Pattern XP_TAG = Pattern.compile("(?:^|/|::)([a-zA-Z][a-zA-Z0-9]*)(?=\\[|$)");

    private LocatorIntent() { }

    /** Returns a reference snapshot with only the fields the selector states; everything else null. */
    public static ElementSnapshot parse(String selector) {
        ElementSnapshot ref = new ElementSnapshot();
        if (selector == null || selector.isBlank()) return ref;
        List<String> parts = splitChain(selector);
        // Walk back to the last segment that names an element; refinements after it add text hints.
        for (int i = parts.size() - 1; i >= 0; i--) {
            String p = parts.get(i).trim();
            Matcher ht = HAS_TEXT.matcher(p);
            if (ht.matches()) {
                if (ref.text == null) ref.text = unescape(ht.group(1));
                continue;
            }
            if (p.startsWith("nth=") || p.startsWith("visible=") || p.startsWith("internal:has")
                    || p.startsWith("internal:and") || p.startsWith("internal:or") || p.startsWith("internal:describe")) {
                continue;
            }
            applySegment(p, ref);
            break;
        }
        return ref;
    }

    private static void applySegment(String p, ElementSnapshot ref) {
        Matcher m;
        if ((m = ROLE.matcher(p)).matches()) {
            ref.role = m.group(1);
            Matcher n = ROLE_NAME.matcher(m.group(2));
            if (n.find()) ref.accessibleName = unescape(n.group(1));
            return;
        }
        if (p.startsWith("internal:text=") || p.startsWith("text=")) {
            String v = p.substring(p.indexOf('=') + 1).trim();
            Matcher q = QUOTED.matcher(v);
            if (q.matches()) ref.text = unescape(q.group(1));
            else if (!v.startsWith("/")) ref.text = v;
            return;
        }
        if (p.startsWith("internal:label=")) {
            Matcher q = QUOTED.matcher(p.substring("internal:label=".length()).trim());
            if (q.matches()) ref.labelText = unescape(q.group(1));
            return;
        }
        if ((m = ATTR.matcher(p)).matches()) {
            setAttr(ref, m.group(1), unescape(m.group(2)), "=");
            return;
        }
        if (p.startsWith("xpath=") || p.startsWith("//") || p.startsWith("(//")) {
            parseXpath(p.startsWith("xpath=") ? p.substring(6) : p, ref);
            return;
        }
        parseCss(p.startsWith("css=") ? p.substring(4) : p, ref);
    }

    private static void parseCss(String css, ElementSnapshot ref) {
        String compound = lastCompound(firstAlternative(css));
        Matcher tag = CSS_TAG.matcher(compound);
        if (tag.find()) ref.tag = tag.group(1).toLowerCase();
        Matcher id = CSS_ID.matcher(stripBrackets(compound));
        if (id.find()) ref.id = id.group(1);
        Matcher a = CSS_ATTR.matcher(compound);
        while (a.find()) {
            String value = a.group(3) != null ? a.group(3) : a.group(4) != null ? a.group(4) : a.group(5);
            setAttr(ref, a.group(1), value, a.group(2));
        }
        Matcher t = CSS_HAS_TEXT.matcher(compound);
        if (t.find()) ref.text = t.group(1) != null ? t.group(1) : t.group(2);
    }

    private static void parseXpath(String xp, ElementSnapshot ref) {
        Matcher a = XP_ATTR.matcher(xp);
        while (a.find()) setAttr(ref, a.group(1), a.group(2) != null ? a.group(2) : a.group(3), "=");
        Matcher t = XP_TEXT.matcher(xp);
        if (t.find()) ref.text = t.group(1) != null ? t.group(1) : t.group(2);
        Matcher tag = XP_TAG.matcher(xp);
        String last = null;
        while (tag.find()) last = tag.group(1);
        if (last != null && !last.equals("following") && !last.equals("preceding") && !last.equals("ancestor")
                && !last.equals("descendant") && !last.equals("self") && !last.equals("parent")) {
            ref.tag = last.toLowerCase();
        }
    }

    /** Only exact (=) and contains (*=) matches say what the value is; ^= / $= are too partial to use. */
    private static void setAttr(ElementSnapshot ref, String attr, String value, String op) {
        if (value == null || value.isBlank() || !(op.equals("=") || op.equals("*="))) return;
        switch (attr.toLowerCase()) {
            case "id" -> ref.id = value;
            case "name" -> ref.name = value;
            case "type" -> ref.type = value.toLowerCase();
            case "role" -> ref.role = value.toLowerCase();
            case "placeholder" -> ref.placeholder = value;
            case "aria-label" -> ref.ariaLabel = value;
            case "title" -> ref.title = value;
            case "alt" -> ref.alt = value;
            case "href" -> ref.href = value;
            case "data-testid", "data-test-id", "data-test", "data-qa", "data-cy" -> {
                ref.testId = value;
                ref.testIdAttr = attr.toLowerCase();
            }
            default -> { /* other attributes (aria-haspopup, class, ...) don't identify the element */ }
        }
    }

    /** Splits {@code a >> b >> c} on top-level separators, ignoring any inside quotes or brackets. */
    static List<String> splitChain(String selector) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < selector.length(); i++) {
            char c = selector.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[' || c == '(') {
                depth++;
            } else if (c == ']' || c == ')') {
                depth--;
            } else if (depth == 0 && selector.startsWith(" >> ", i)) {
                out.add(selector.substring(start, i));
                start = i + 4;
                i += 3;
            }
        }
        out.add(selector.substring(start));
        return out;
    }

    private static String firstAlternative(String css) {
        int depth = 0;
        for (int i = 0; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '[' || c == '(') depth++;
            else if (c == ']' || c == ')') depth--;
            else if (c == ',' && depth == 0) return css.substring(0, i).trim();
        }
        return css.trim();
    }

    /** The last compound of a CSS selector: {@code form div > input[name=x]} → {@code input[name=x]}. */
    private static String lastCompound(String css) {
        int depth = 0;
        int cut = 0;
        for (int i = 0; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '[' || c == '(') depth++;
            else if (c == ']' || c == ')') depth--;
            else if (depth == 0 && (c == ' ' || c == '>' || c == '+' || c == '~')) cut = i + 1;
        }
        return css.substring(cut).trim();
    }

    private static String stripBrackets(String s) {
        return s.replaceAll("\\[[^]]*]", "").replaceAll("\\([^)]*\\)", "");
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
