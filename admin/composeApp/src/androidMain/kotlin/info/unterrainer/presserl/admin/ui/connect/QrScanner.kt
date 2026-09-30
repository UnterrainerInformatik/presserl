package info.unterrainer.presserl.admin.ui.connect

import android.app.Activity
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Scans one QR code with Google Play services' own scanner UI (design D6): no camera permission, decoded on the
 * device. [onCode] gets the text, closing the scanner calls nothing, [onUnavailable] follows devices without it.
 */
fun scanQrCode(activity: Activity, onCode: (String) -> Unit, onUnavailable: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(activity, options).startScan()
        .addOnSuccessListener { barcode -> onCode(barcode.rawValue.orEmpty()) }
        .addOnFailureListener { e ->
            // closing the scanner with the system back action arrives as a failure (INTERNAL), so only the codes of a
            // missing or outdated scanner count as unavailable; anything else leaves the start screen as it was
            val code = (e as? MlKitException)?.errorCode
            Log.w("presserl", "QR scanner ended with code $code", e)
            if (code in UNAVAILABLE) onUnavailable()
        }
}

private val UNAVAILABLE = setOf(
    MlKitException.UNAVAILABLE,
    MlKitException.CODE_SCANNER_UNAVAILABLE,
    MlKitException.CODE_SCANNER_APP_NAME_UNAVAILABLE,
    MlKitException.CODE_SCANNER_PIPELINE_INITIALIZATION_ERROR,
    MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD,
)
