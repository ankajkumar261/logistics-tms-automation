package com.portfolio.logistics.utils;

import com.microsoft.playwright.Page;

import java.util.Arrays;

/**
 * Helpers for the report date filters. A native date picker delivers a date by setting the input's
 * value and firing 'input' + 'change' — this reproduces "selecting a date from the calendar" (the
 * path that BUG-009..011/021/022 said was ignored) so tests can assert the report actually applies it.
 */
public final class ReportDates {

    private ReportDates() { }

    /** Simulate the calendar picker delivering {yyyyMmDd} to the Nth date input, then let the report react. */
    public static void pickDate(Page page, int index, String yyyyMmDd) {
        page.evaluate(
                "([i,v]) => { const el = document.querySelectorAll('input[type=date]')[i]; if(!el) return;" +
                " const set = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;" +
                " set.call(el, v);" +
                " el.dispatchEvent(new Event('input', {bubbles:true}));" +
                " el.dispatchEvent(new Event('change', {bubbles:true})); }",
                Arrays.asList(index, yyyyMmDd));
        page.waitForTimeout(1800);
    }

    /** True if the report table shows a "No … data/period/filters" empty state. */
    public static boolean showsEmptyState(Page page) {
        String body = (String) page.evaluate(
                "() => { const tb = document.querySelector('table tbody'); return tb ? tb.innerText.toLowerCase() : ''; }");
        return body.contains("no ") && (body.contains("data") || body.contains("period")
                || body.contains("filters") || body.contains("found") || body.contains("result"));
    }
}
