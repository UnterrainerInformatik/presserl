package info.unterrainer.presserl.admin.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/** Same styles as the web app (`dateStyle: 'medium', timeStyle: 'short'`), e.g. `30.09.2026, 14:45` in German. */
actual fun formatTimestamp(iso: String): String = formatTimestamp(iso, Locale.getDefault(), ZoneId.systemDefault())

internal fun formatTimestamp(iso: String, locale: Locale, zone: ZoneId): String = try {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
        .format(Instant.parse(iso).atZone(zone))
} catch (e: DateTimeParseException) {
    iso
}

/** Same style as the web app (`dateStyle: 'long'`), e.g. `12. Oktober 2026` in German. */
actual fun formatDate(iso: String): String = formatDate(iso, Locale.getDefault())

internal fun formatDate(iso: String, locale: Locale): String = try {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(LocalDate.parse(iso))
} catch (e: DateTimeParseException) {
    iso
}
