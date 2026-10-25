package com.portfolio.logistics.utils;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class ExportHelper {

    private final Page page;
    // All exports land here — read them later.
    public static final Path EXPORT_DIR = Paths.get("exports");

    public ExportHelper(Page page) {
        this.page = page;
    }

    /** Click Export -> Export to PDF and save the download into the exports/ folder.
     *  Works on tabs whose "Export" button opens a menu with "Export to PDF". */
    public Path exportPdf(String fileNameHint) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Export$", Pattern.CASE_INSENSITIVE))).first().click();
        page.waitForTimeout(400);

        Locator pdfItem = page.getByText("Export to PDF", new Page.GetByTextOptions().setExact(false));
        Download download = page.waitForDownload(() -> pdfItem.first().click());

        Path target = EXPORT_DIR.resolve(fileNameHint + "-" + System.currentTimeMillis() + ".pdf");
        download.saveAs(target);
        System.out.println("[ExportHelper] Saved: " + target.toAbsolutePath());
        return target;
    }

    /** Click a direct "Export CSV" button and save the download into the exports/ folder.
     *  Used by the report pages (sales / profitability / fuel / aging) that export straight to CSV. */
    public Path exportCsv(String fileNameHint) {
        Locator csvBtn = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("Export CSV", Pattern.CASE_INSENSITIVE)));
        Download download = page.waitForDownload(() -> csvBtn.first().click());

        String suggested = download.suggestedFilename();
        String ext = (suggested != null && suggested.contains(".")) ? suggested.substring(suggested.lastIndexOf('.')) : ".csv";
        Path target = EXPORT_DIR.resolve(fileNameHint + "-" + System.currentTimeMillis() + ext);
        download.saveAs(target);
        System.out.println("[ExportHelper] Saved: " + target.toAbsolutePath());
        return target;
    }
}