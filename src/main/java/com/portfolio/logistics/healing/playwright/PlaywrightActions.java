package com.portfolio.logistics.healing.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.healing.core.ActionKind;
import com.portfolio.logistics.healing.core.HealingConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Which Locator methods may be healed, and what their arguments say about intent. */
final class PlaywrightActions {

    private PlaywrightActions() { }

    /**
     * Null = never healed. That covers every presence/absence check (count, isVisible, isHidden,
     * isEnabled, all*, waitFor HIDDEN/DETACHED, ...) so negative assertions keep their exact meaning,
     * and reads unless healing.healReads is on.
     */
    static ActionKind kindOf(Method m, Object[] args, HealingConfig cfg) {
        switch (m.getName()) {
            case "click": case "dblclick": case "tap":
                return ActionKind.CLICK;
            case "hover": case "focus": case "scrollIntoViewIfNeeded":
                return ActionKind.HOVER;
            case "fill": case "clear":
                return ActionKind.FILL;
            case "pressSequentially": case "type":
                return ActionKind.TYPE;
            case "press":
                return ActionKind.PRESS;
            case "check": case "uncheck": case "setChecked":
                return ActionKind.CHECK;
            case "selectOption":
                return ActionKind.SELECT;
            case "setInputFiles":
                return ActionKind.UPLOAD;
            case "waitFor": {
                WaitForSelectorState state = waitState(args);
                return state == null || state == WaitForSelectorState.VISIBLE || state == WaitForSelectorState.ATTACHED
                        ? ActionKind.WAIT : null;
            }
            case "innerText": case "textContent": case "inputValue": case "getAttribute": case "innerHTML":
                return cfg.healReads ? ActionKind.READ : null;
            default:
                return null;
        }
    }

    /** The timeout the caller set explicitly on this call, if any. */
    static Long explicitTimeout(Object[] args) {
        if (args == null) return null;
        for (Object a : args) {
            if (!isOptions(a)) continue;
            try {
                Field f = a.getClass().getField("timeout");
                Object v = f.get(a);
                if (v instanceof Number n) return n.longValue();
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // options without a timeout
            }
        }
        return null;
    }

    static boolean isOptions(Object o) {
        return o != null && o.getClass().getName().startsWith("com.microsoft.playwright.")
                && o.getClass().getEnclosingClass() != null && o.getClass().getSimpleName().endsWith("Options");
    }

    private static WaitForSelectorState waitState(Object[] args) {
        if (args != null) {
            for (Object a : args) {
                if (a instanceof Locator.WaitForOptions o) return o.state;
            }
        }
        return null;
    }
}
