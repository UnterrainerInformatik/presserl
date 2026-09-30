package info.unterrainer.presserl.admin.ui.connect

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.ui.account.AndroidSlipPrinter
import info.unterrainer.presserl.admin.ui.account.SlipPrinter
import info.unterrainer.presserl.admin.ui.media.installImagePicker
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Locale

/**
 * Everything the activity of the Android app needs: the connection flow over [AndroidConnectionStore], the HTTP client
 * and the scanner. [debug] builds accept `http` addresses and the intent extra [EXTRA_QR], which feeds a payload into
 * the scan path for automated checks.
 */
class AndroidAppHost(private val activity: ComponentActivity, private val debug: Boolean) {

    private val http = HttpClient(OkHttp)
    private val slipPrinter: SlipPrinter = AndroidSlipPrinter(activity)

    init {
        installImagePicker(activity)
    }

    val model = ConnectionModel(activity.lifecycleScope, AndroidConnectionStore(activity), allowHttp = debug) { base ->
        ApiClient(http, base) { error("client-config needs no token") }.clientConfig()
    }

    /** At the start of the activity: debug payload or the remembered server. */
    fun start(intent: Intent?) {
        if (!handleIntent(intent)) model.resume()
    }

    /** `true` when [intent] carried a debug payload. */
    fun handleIntent(intent: Intent?): Boolean {
        val payload = intent?.takeIf { debug }?.getStringExtra(EXTRA_QR) ?: return false
        intent.removeExtra(EXTRA_QR)
        model.scanned(payload)
        return true
    }

    @Composable
    fun Content() = AndroidApp(
        model,
        http,
        slipPrinter,
        onScan = { scanQrCode(activity, onCode = model::scanned, onUnavailable = model::scannerUnavailable) },
        // the language the app's texts use: the phone's, or the app language chosen in the system settings
        onPrivacyPolicy = {
            val url = privacyPolicyUrl(activity.resources.configuration.locales[0])
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
    )

    companion object {
        const val EXTRA_QR = "qr"

        /** The app's privacy policy in German for a German [locale], in English otherwise. */
        fun privacyPolicyUrl(locale: Locale): String =
            "https://unterrainer.info/app/presserl/privacy?lang=" + if (locale.language == "de") "de" else "en"
    }
}
