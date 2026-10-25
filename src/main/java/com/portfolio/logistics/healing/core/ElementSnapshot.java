package com.portfolio.logistics.healing.core;

import java.util.List;
import java.util.Map;

/**
 * A driver-neutral description of one UI element: what it is (tag/role/type), what identifies it
 * (id, name, test id, accessible name, label, text) and where it sits (ancestry, heading, position).
 *
 * The same shape serves three purposes:
 *  - the baseline fingerprint captured while the original locator still worked,
 *  - the reference built from a locator's selector when no baseline exists yet ({@link LocatorIntent}),
 *  - each candidate found on the live page during a heal.
 * A null field means "unknown" and is left out of scoring. Web fills it from the DOM; an Appium
 * adapter would fill it from page-source XML (resource-id → id, content-desc → accessibleName, ...).
 *
 * Never holds user-entered values: snapshots are logged, persisted and may be sent to the AI advisor.
 */
public class ElementSnapshot {

    public String tag;
    public String type;
    public String id;
    public String name;
    public String testId;
    public String testIdAttr;
    public String href;
    public String role;
    public String accessibleName;
    public String ariaLabel;
    public String labelText;
    public String text;
    public String placeholder;
    public String title;
    public String alt;
    public Map<String, String> dataAttrs;

    /** Parent tags nearest-first, e.g. ["div", "form", "div[role=dialog]"]. */
    public List<String> ancestry;
    /** Heading of the nearest dialog/section/form the element sits in. */
    public String heading;
    public Boolean inDialog;

    /** Page coordinates (not viewport), so scrolling doesn't move them. */
    public Double x;
    public Double y;
    public Double w;
    public Double h;

    public Boolean visible;
    public Boolean enabled;
    public Boolean editable;

    /** Position in the candidate list of one heal attempt; links the snapshot to the live element. */
    public Integer index;
    /** Index of the nearest candidate that contains this one, or -1. */
    public Integer ancestorIndex;
    /** Last-resort positional selector. */
    public String cssPath;

    /** Short human description for logs, e.g. {@code input#company-name[name=companyName] "Company Name"}. */
    public String describe() {
        StringBuilder sb = new StringBuilder(present(tag) ? tag : "element");
        if (present(id)) sb.append('#').append(id);
        if (present(name)) sb.append("[name=").append(name).append(']');
        if (present(testId)) sb.append('[').append(present(testIdAttr) ? testIdAttr : "data-testid")
                .append('=').append(testId).append(']');
        if (present(role) && !role.equals(tag)) sb.append(" role=").append(role);
        String label = firstPresent(accessibleName, labelText, text, placeholder);
        if (label != null) sb.append(" \"").append(label.length() > 50 ? label.substring(0, 50) + "…" : label).append('"');
        return sb.toString();
    }

    static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    static String firstPresent(String... values) {
        for (String v : values) if (present(v)) return v;
        return null;
    }
}
