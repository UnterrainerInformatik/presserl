package info.unterrainer.presserl.admin.ui

/** A warning in the device log (Android: `logcat`, tag `presserl`; web: the browser console). */
expect fun logWarning(message: String, cause: Throwable?)
