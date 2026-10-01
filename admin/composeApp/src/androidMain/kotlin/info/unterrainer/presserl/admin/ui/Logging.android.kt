package info.unterrainer.presserl.admin.ui

import android.util.Log

actual fun logWarning(message: String, cause: Throwable?) {
    Log.w("presserl", message, cause)
}
