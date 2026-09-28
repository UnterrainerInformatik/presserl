package info.unterrainer.presserl.admin.ui

/** An ISO-8601 timestamp from the API as local date and time in the browser's format. */
expect fun formatTimestamp(iso: String): String

/** An ISO date (`2026-10-12`) from the API in the browser's long date format, e.g. `12. Oktober 2026`. */
expect fun formatDate(iso: String): String
