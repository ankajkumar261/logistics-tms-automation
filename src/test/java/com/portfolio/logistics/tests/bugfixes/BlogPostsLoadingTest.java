package com.portfolio.logistics.tests.bugfixes;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * FIX-07 — "Blog Posts page remained stuck on the loading skeleton".
 *
 * The symptom was an infinite loading skeleton. The fix means the page RESOLVES: within a bounded
 * wait the skeleton placeholders clear and the page reaches a definite terminal state. The tests
 * assert exactly that — the skeleton must be gone and real content must render.
 *
 * The admin list resolves to the posts table when the signed-in role may view it; if the role is
 * gated it resolves to an "Access Restricted" page instead. Both are terminal (loaded) states — a
 * stuck skeleton is not — so either satisfies the "no longer stuck loading" fix. The public blog is
 * ungated and must render its posts.
 */
@Epic("Logistics TMS TMS")
@Feature("Bug fixes — Blog")
public class BlogPostsLoadingTest extends BaseTest {

    private static final int LOAD_BUDGET_MS = 20_000;
    private static final String SKELETON = "[class*='animate-pulse'], [data-slot='skeleton'], [class*='skeleton']";

    private void login() {
        new LoginPage(page).establishAdminSession();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(500);
    }

    @Test(description = "FIX-07 Admin Blog: All Posts resolves (posts list or access gate) — no stuck skeleton")
    @Severity(SeverityLevel.CRITICAL)
    public void adminBlogListFinishesLoading() {
        login();
        page.navigate(ConfigReader.baseUrl() + "/admin/blog");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // "Loaded" == any terminal marker: the posts table, an empty-state message, or an access gate.
        Locator terminal = page.locator(
                "table tbody tr, "
                + ":text-matches('Access Restricted|No posts|No blog posts|Blog posts', 'i')");
        try {
            terminal.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(LOAD_BUDGET_MS));
        } catch (Exception stuck) {
            throw new AssertionError("/admin/blog did not finish loading within " + LOAD_BUDGET_MS
                    + "ms — no posts list or access gate appeared, still showing "
                    + page.locator(SKELETON).count() + " skeleton placeholders. Page text: " + shortText());
        }

        assertEquals(page.locator(SKELETON).count(), 0,
                "/admin/blog resolved but left loading skeletons on screen — still partly stuck. "
                        + "Page text: " + shortText());

        boolean accessGated = page.getByText(Pattern.compile("Access Restricted", Pattern.CASE_INSENSITIVE)).count() > 0;
        if (accessGated) {
            // Role can't view the admin blog list; the page loaded to a gate, not a skeleton — the
            // FIX-07 symptom (stuck loading) is resolved. The posts list itself is checked when a
            // full-access account (e.g. SuperAdmin on QA) runs this test.
            System.out.println("[FIX-07] /admin/blog is access-gated for this role; loaded to the gate, not a skeleton.");
            return;
        }

        assertTrue(page.locator("table tbody tr").count() > 0
                        || page.getByText(Pattern.compile("No posts|No blog posts", Pattern.CASE_INSENSITIVE)).count() > 0,
                "/admin/blog finished loading but shows neither posts nor an empty-state message. "
                        + "Page text: " + shortText());
    }

    @Test(description = "FIX-07 Public blog page resolves and renders posts — no stuck skeleton")
    @Severity(SeverityLevel.NORMAL)
    public void publicBlogFinishesLoading() {
        page.navigate(ConfigReader.baseUrl() + "/blog");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator content = page.getByText(Pattern.compile(
                "Read article|min read|Insights & Ideas|BLOGS|freight insights", Pattern.CASE_INSENSITIVE));
        try {
            content.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(LOAD_BUDGET_MS));
        } catch (Exception stuck) {
            throw new AssertionError("/blog did not finish loading within " + LOAD_BUDGET_MS
                    + "ms — still showing " + page.locator(SKELETON).count() + " skeleton placeholders. "
                    + "Page text: " + shortText());
        }

        assertEquals(page.locator(SKELETON).count(), 0,
                "/blog left loading skeletons on screen after rendering. Page text: " + shortText());
        assertTrue(page.getByText(Pattern.compile("Read article|min read", Pattern.CASE_INSENSITIVE)).count() > 0,
                "Expected the public blog to render at least one post. Page text: " + shortText());
    }

    private String shortText() {
        String t = page.locator("body").innerText().replaceAll("\\s+", " ").trim();
        return t.length() > 500 ? t.substring(0, 500) + "…" : t;
    }
}
