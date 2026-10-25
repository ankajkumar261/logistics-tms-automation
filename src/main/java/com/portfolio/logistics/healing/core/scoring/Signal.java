package com.portfolio.logistics.healing.core.scoring;

/** One kind of evidence that a candidate is the element the broken locator used to find. */
public enum Signal {
    /** id, name, test id, link target. */
    IDENTITY(true),
    /** aria-label, label text, aria-labelledby, computed accessible name. */
    ACCESSIBLE_NAME(true),
    /** ARIA role / tag / input type. */
    ROLE(false),
    /** Visible text. */
    TEXT(true),
    /** placeholder, title, alt. */
    HINTS(true),
    /** Ancestor path, containing section heading, inside-a-dialog. */
    STRUCTURE(false),
    /** Position on the page. */
    GEOMETRY(false);

    /**
     * Content signals say WHICH element this is. A heal needs at least one of them to match strongly —
     * two icon buttons side by side match on role, structure and position alone.
     */
    public final boolean content;

    Signal(boolean content) {
        this.content = content;
    }
}
