package info.unterrainer.presserl.admin.ui.account

import android.app.Activity
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Opens the system print dialog with the slip alone (print or save as PDF): [slipHtml] in an off-screen web view
 * without JavaScript, handed to the [PrintManager] once loaded.
 */
class AndroidSlipPrinter(private val activity: Activity) : SlipPrinter {

    /** Kept until the print job has the document; a collected web view would cancel it. */
    private var printing: WebView? = null

    override fun print(slip: PrintableSlip) {
        val view = WebView(activity)
        view.settings.javaScriptEnabled = false
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(page: WebView, url: String) {
                val job = slip.title.ifBlank { "presserl" }
                val attributes = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
                activity.getSystemService(PrintManager::class.java).print(job, page.createPrintDocumentAdapter(job), attributes)
            }
        }
        printing = view
        view.loadDataWithBaseURL(null, slipHtml(slip), "text/html", "utf-8", null)
    }
}
