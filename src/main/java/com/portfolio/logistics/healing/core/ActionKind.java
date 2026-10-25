package com.portfolio.logistics.healing.core;

/**
 * What the test was trying to do with the element. Healing only ever substitutes an element the
 * action can apply to — a fill() is never healed onto a checkbox, a selectOption() only onto a select.
 *
 * Presence/absence checks (count, isVisible, waitFor HIDDEN) have no ActionKind on purpose: they are
 * never healed, so "is it gone?" keeps its exact meaning.
 */
public enum ActionKind {
    CLICK,
    HOVER,
    FILL,
    TYPE,
    PRESS,
    CHECK,
    SELECT,
    UPLOAD,
    /** waitFor() for VISIBLE / ATTACHED. */
    WAIT,
    /** innerText / textContent / inputValue / getAttribute — only when healing.healReads=true. */
    READ
}
