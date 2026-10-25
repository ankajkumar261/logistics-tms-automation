package com.portfolio.logistics.tests.trips;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.pages.TripDetailPage;
import com.portfolio.logistics.pages.TripDocumentsPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 4 — Trips & Dispatch")
public class TripDeliveryCompletionTest extends BaseTest {

    /**
     * 4.7 Delivery completion — mark a trip delivered + attach a POD.
     * EXP (per QA flow doc): trip status = Delivered; the order becomes invoiceable.
     *
     * NOTE: this test may FAIL at the POD-attach step. The attachment
     * upload posts to the frontend same-origin /api/trips/{id}/attachments proxy, which is
     * currently returning HTTP 502 (see 4.6 / TripAttachmentTest — backend origin is healthy,
     * the frontend /api proxy is down). Everything up to the upload is validated and passes.
     * When the proxy is fixed, the POD upload + invoiceable checks light up with no changes.
     */
    @Test(description = "4.7 Delivery completion: mark trip delivered + attach POD; expect Delivered + order invoiceable")
    @Severity(SeverityLevel.CRITICAL)
    public void deliveryCompletion() throws Exception {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000); // let the app settle post-login before deep-linking

        // --- Pick a dispatchable trip by reading the LIST Status column directly.
        //  We scan each row's status cell for "Scheduled" explicitly. (A Locator.filter(hasText=/\bScheduled\b/)
        //  one-liner did NOT match this table reliably — it returned 0 even with a Scheduled row present —
        //  so we do the per-cell scan, which is proven to work here.) ---
        TripDetailPage trip = new TripDetailPage(page);
        page.navigate(ConfigReader.baseUrl() + "/trips");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.locator("table tbody tr").first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        page.waitForTimeout(800); // let the status badges paint

        // Walk EVERY page of /trips, not just the first — a Scheduled trip often sits further in,
        // and stopping at page 1 previously turned a findable trip into a skip.
        Locator chosen = null;
        String tripNo = null;
        for (int pageIndex = 0; pageIndex < 30 && chosen == null; pageIndex++) {
            int rowCount = page.locator("table tbody tr").count();
            for (int i = 0; i < rowCount; i++) {
                Locator r = page.locator("table tbody tr").nth(i);
                boolean isScheduled = r.locator("td").allInnerTexts().stream()
                        .anyMatch(c -> c.trim().equalsIgnoreCase("Scheduled"));
                if (isScheduled) {
                    chosen = r;
                    tripNo = r.locator("td").first().innerText().trim().replaceAll("\\s+", " ");
                    break;
                }
            }
            if (chosen != null) break;
            Locator next = page.locator("button[aria-label='Go to next page']").first();
            if (next.count() == 0 || !next.isEnabled()) break;
            next.click();
            page.waitForTimeout(900);
        }
        assertNotNull(chosen,
                "4.7 found no Scheduled (dispatchable) trip anywhere in /trips — every trip is already "
                        + "Dispatched/In Transit/Completed, so the delivery workflow could not be exercised. "
                        + "This is a test-data gap in this environment: create a fresh trip (4.1) first.");
        chosen.click();
        page.waitForURL("**/trips/**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        // The trip detail renders its workflow button asynchronously — wait for it before advancing,
        // otherwise advanceStep() can check too early and report the button missing.
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName(Pattern.compile("Dispatch Trip", Pattern.CASE_INSENSITIVE)))
                .first().waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        page.waitForTimeout(500);
        String tripUrl = page.url(); // remember the trip so we can reopen it for the POD upload
        System.out.println("[4.7] Picked Scheduled trip " + tripNo + " -> " + tripUrl);

        // --- Advance the full delivery workflow (confirmed live). Stepper stages / button labels:
        //     Planned  --Dispatch Trip-->  Dispatched  --Start Trip-->  Started
        //     --Mark Delivery Arrived-->  En-Route  --Mark Delivered-->  Delivered  --Complete Trip-->  (done)
        // The trips LIST status column uses a coarser vocabulary: Scheduled → Dispatched → In Transit → Completed. ---
        assertTrue(trip.advanceStep("Dispatch Trip", "Dispatch"),
                "Expected 'Dispatch Trip' for trip " + tripNo);
        assertTrue(trip.advanceStep("Start Trip", "Start"),
                "Expected 'Start Trip' after dispatch");
        assertTrue(trip.advanceStep("Mark Delivery Arrived", "Arrived", "Mark Arrived", "En-Route", "Arrive"),
                "Expected 'Mark Delivery Arrived' after start");
        assertTrue(trip.advanceStep("Mark Delivered", "Delivered", "Complete Delivery", "Mark Delivery"),
                "Expected 'Mark Delivered' after arrival");
        assertTrue(trip.advanceStep("Complete Trip", "Complete"),
                "Expected 'Complete Trip' after delivered");

        // --- EXP part 1: the trip reaches its terminal list status (works today; completion doesn't need the POD).
        // There is NO "Delivered" badge in the list — the stepper's Delivered rolls up to "Completed". ---
        assertTrue(trip.listStatusIs(tripNo, "Completed") || trip.listStatusIs(tripNo, "Delivered"),
                "Expected trip " + tripNo + " to show Completed after the delivery workflow "
                + "(list vocabulary: Scheduled → Dispatched → In Transit → Completed).");

        // --- Attach a POD document (demo PDF generated at runtime) ---
        Path pod = Files.createTempFile("QA-POD-", ".pdf");
        Files.write(pod, ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n"
                + "trailer<</Root 1 0 R>>\n%%EOF").getBytes());
        String fileName = pod.getFileName().toString();

        // Attach the POD last (after the workflow is verified): completion doesn't require a POD here
        // — 4.2 completes trips with none — so this isolates the known upload 502 to the end of the test.
        page.navigate(tripUrl); // reopen the completed trip to attach proof of delivery
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        // The only file-upload surface is the Documents card → Files tab (BOL/POD tabs are metadata forms).
        // TODO(when unblocked): set the Files-tab category combobox to "POD" to tag it as proof-of-delivery.
        TripDocumentsPage docs = new TripDocumentsPage(page);
        boolean uploaded = docs.uploadDocument("Files", pod);
        assertTrue(uploaded, "Could not drive the Files uploader for trip " + tripNo
                + " (expected Documents card → Files tab → file input).");

        // KNOWN BLOCKER (today): the upload POST returns 502 from the frontend /api proxy — same as 4.6.
        int status = docs.lastUploadStatus();
        assertTrue(status >= 200 && status < 300,
                "POD upload POST /api/trips/{id}/attachments for " + tripNo + " returned HTTP " + status
                + ". The Files uploader was driven correctly, so this is the same server-side blocker as 4.6: "
                + "the frontend same-origin /api/* proxy returns 502 while the backend origin (tms-backend) is 200. "
                + "Not a test-automation defect.");

        assertTrue(docs.documentAppears(fileName),
                "Expected POD '" + fileName + "' to appear under Files for " + tripNo);

        // --- EXP part 2: the order becomes invoiceable (runs once the upload above is unblocked) ---
        assertTrue(orderIsInvoiceable(),
                "Expected trip " + tripNo + "'s order to be invoiceable after delivery "
                + "(a 'Create Invoice' action should be available).");
    }

    /** Best-effort check that the delivered trip's order can now be invoiced. */
    private boolean orderIsInvoiceable() {
        Locator createInvoice = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Create Invoice", Pattern.CASE_INSENSITIVE)));
        try {
            return createInvoice.count() > 0
                    && createInvoice.first().isVisible()
                    && createInvoice.first().isEnabled();
        } catch (Exception e) {
            return false;
        }
    }
}
