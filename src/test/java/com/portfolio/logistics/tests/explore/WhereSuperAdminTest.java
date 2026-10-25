package com.portfolio.logistics.tests.explore;

import com.microsoft.playwright.*;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.pages.LoginPage;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.List;

/** Throwaway explorer: find WHERE super-admin / companies live in the live UI. */
public class WhereSuperAdminTest {

    @Test
    public void locate() {
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newContext().newPage();

            new LoginPage(page).open().loginAsSuperAdmin();
            page.waitForTimeout(4000);
            System.out.println("[URL after login] " + page.url());

            // Dump every sidebar/nav link (text -> href)
            System.out.println("\n===== NAV / SIDEBAR LINKS =====");
            List<ElementHandle> links = page.querySelectorAll("a[href]");
            for (ElementHandle a : links) {
                String href = a.getAttribute("href");
                String text = a.innerText().trim().replaceAll("\\s+", " ");
                if (href != null && (href.contains("/admin") || href.contains("tenant")
                        || href.contains("compan") || href.contains("organi")
                        || text.toLowerCase().contains("admin")
                        || text.toLowerCase().contains("compan")
                        || text.toLowerCase().contains("tenant")
                        || text.toLowerCase().contains("organi"))) {
                    System.out.println("  \"" + text + "\"  ->  " + href);
                }
            }

            // Visit the known routes and report heading + row counts
            String[] routes = {"/admin", "/admin/tenants"};
            for (String r : routes) {
                page.navigate(ConfigReader.baseUrl() + r);
                page.waitForTimeout(3500);
                System.out.println("\n===== ROUTE " + r + " =====");
                System.out.println("  final URL : " + page.url());
                System.out.println("  title     : " + page.title());
                ElementHandle h1 = page.querySelector("h1, h2");
                System.out.println("  heading   : " + (h1 != null ? h1.innerText().trim() : "(none)"));
                System.out.println("  table rows: " + page.querySelectorAll("table tbody tr").size());
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("exports/where-" + r.replaceAll("[/]", "_") + ".png"))
                        .setFullPage(true));
            }
            browser.close();
        }
    }
}
