package com.portfolio.logistics.tests.admin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 12 — Admin (Blog CMS)")
public class BlogManagementTest extends BaseTest {

    private void login() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
    }

    private long ts() { return System.currentTimeMillis() % 1_000_000L; }

    @Test(description = "12.5 Blog CMS: add a post category")
    @Severity(SeverityLevel.NORMAL)
    public void addCategory() {
        login();
        page.navigate(ConfigReader.baseUrl() + "/admin/blog/categories");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1500);

        String cat = "QA-Cat-" + ts();
        Locator input = page.locator("input[placeholder*='New category name']").first();
        input.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        input.fill(cat);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Add$", Pattern.CASE_INSENSITIVE))).first().click();
        page.waitForTimeout(2000);

        assertTrue(page.getByText(cat).count() > 0, "Expected new blog category '" + cat + "' to appear.");
    }

    @Test(description = "12.5 Blog CMS: add a new post (saved as draft)")
    @Severity(SeverityLevel.CRITICAL)
    public void addBlogPost() throws Exception {
        login();
        page.navigate(ConfigReader.baseUrl() + "/admin/blog/new");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1500);

        long t = ts();
        String title = "QA Post " + t;
        page.locator("input[placeholder*='How to do X']").first().fill(title);
        page.locator("input[placeholder*='how-to-do-x']").first().fill("qa-post-" + t);
        page.locator("textarea[placeholder*='Short SEO description']").first()
                .fill("QA automated draft post " + t);

        // Content (best-effort — rich-text contenteditable).
        try {
            Locator editor = page.locator("[contenteditable='true']").first();
            if (editor.count() > 0) { editor.click(); editor.pressSequentially("QA draft body " + t); }
        } catch (Exception ignored) { }

        // Cover image (required) — hidden input inside the "Upload image" label.
        Path img = Path.of("src/test/resources/demoBlogtest.jpg");
        assertTrue(Files.exists(img), "Cover image not found at " + img.toAbsolutePath());

        Locator coverInput = page.locator("label:has-text('Upload image') input[type='file']").first();
        if (coverInput.count() == 0) {
            coverInput = page.locator("input[accept*='image/']").first();   // fallback by accept attr
        }
        coverInput.setInputFiles(img);      // works on hidden inputs
        page.waitForTimeout(2500);          // let the upload + preview render
        System.out.println("[Blog] cover image uploaded from " + img.getFileName());
        // Save as draft (not Publish — avoid pushing to the public site).
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("Save draft", Pattern.CASE_INSENSITIVE))).first().click();
        page.waitForTimeout(3000);

        // Verify: the post actually appears in All Posts.
        page.navigate(ConfigReader.baseUrl() + "/admin/blog");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1500);

        Locator search = page.locator("input[type='search'], input[placeholder*='Search' i]").first();
        if (search.count() > 0) {
            search.fill(title);
            page.waitForTimeout(1500);
        }

        Locator post = page.getByText(title, new Page.GetByTextOptions().setExact(false));
        assertTrue(post.count() > 0 && post.first().isVisible(),
                "Expected blog post '" + title + "' to appear in All Posts after saving as draft.");
    }

}
