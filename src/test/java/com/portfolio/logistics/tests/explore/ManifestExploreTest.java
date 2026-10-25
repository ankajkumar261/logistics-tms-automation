package com.portfolio.logistics.tests.explore;

import com.microsoft.playwright.*;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.pages.LoginPage;
import org.testng.annotations.Test;

import java.nio.file.Paths;

/** Throwaway explorer: click a Trip-Without-Manifest chip and capture the create flow. */
public class ManifestExploreTest {

    @Test
    public void explore() {
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newContext().newPage();
            new LoginPage(page).open().loginAsSuperAdmin();
            page.waitForTimeout(3000);
            page.navigate(ConfigReader.baseUrl() + "/trips/manifest");
            page.waitForTimeout(4000);

            // Describe the chip element + its clickable ancestor
            System.out.println("===== chip element chain =====");
            System.out.println(page.evaluate("() => {" +
                "const el=[...document.querySelectorAll('*')].find(e=>/^(ORD|TRP)-\\d/.test((e.innerText||'').trim()) && e.children.length<=2);" +
                "if(!el) return 'no chip';" +
                "let n=el, chain=[];" +
                "for(let i=0;i<5&&n;i++){chain.push(n.tagName+(n.getAttribute('role')?'[role='+n.getAttribute('role')+']':'')+(n.className?'.'+String(n.className).slice(0,40):''));n=n.parentElement;}" +
                "return chain.join('  >  ');}"));

            String before = page.url();
            // Click the clickable ancestor of the first chip
            Locator chip = page.locator("xpath=//*[starts-with(normalize-space(.),'TRP-') or starts-with(normalize-space(.),'ORD-')][not(.//*[starts-with(normalize-space(.),'TRP-')])]").first();
            System.out.println("\nclicking chip: " + chip.innerText().trim().replaceAll("\\s+"," "));
            chip.click();
            page.waitForTimeout(4000);
            System.out.println("URL before=" + before + "  after=" + page.url());

            System.out.println("\n===== dialog / drawer after click =====");
            Object dlg = page.evaluate("() => {" +
                "const d=document.querySelector('[role=dialog],.modal,[class*=drawer],[class*=Drawer],[class*=sheet],[class*=Sheet]');" +
                "if(!d) return '(no dialog/drawer)';" +
                "const inputs=[...d.querySelectorAll('input,select,textarea')].map(i=>i.tagName+':'+(i.name||i.placeholder||i.type));" +
                "const btns=[...d.querySelectorAll('button')].map(b=>b.innerText.trim()).filter(Boolean);" +
                "const heads=[...d.querySelectorAll('h1,h2,h3,label')].map(h=>h.innerText.trim()).filter(Boolean).slice(0,15);" +
                "return {heads, inputs, btns};}");
            System.out.println(dlg);

            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("exports/manifest-after-click.png")).setFullPage(true));
            System.out.println("\n[screenshot] exports/manifest-after-click.png");
            browser.close();
        }
    }
}
