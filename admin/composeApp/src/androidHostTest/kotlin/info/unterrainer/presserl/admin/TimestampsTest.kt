package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.ui.formatDate
import info.unterrainer.presserl.admin.ui.formatTimestamp
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** The expected texts are what the web app's `toLocaleString` produces for the same input (Chrome/ICU). */
class TimestampsTest {

    private val vienna = ZoneId.of("Europe/Vienna")

    @Test
    fun germanTimestampLikeTheWebApp() {
        assertEquals("30.09.2026, 14:45", formatTimestamp("2026-09-30T12:45:00Z", Locale.GERMAN, vienna))
        assertEquals("30.09.2026, 14:45", formatTimestamp("2026-09-30T12:45:00.123456Z", Locale.forLanguageTag("de-AT"), vienna))
    }

    @Test
    fun englishTimestampLikeTheWebApp() {
        // the JDK puts a narrow no-break space before PM where ICU in the browser has a plain one
        assertEquals("Sep 30, 2026, 2:45 PM", formatTimestamp("2026-09-30T12:45:00Z", Locale.US, vienna).replace(' ', ' '))
    }

    @Test
    fun longDateLikeTheWebApp() {
        assertEquals("12. Oktober 2026", formatDate("2026-10-12", Locale.GERMAN))
        assertEquals("October 12, 2026", formatDate("2026-10-12", Locale.US))
    }

    @Test
    fun unparsableInputIsShownAsItIs() {
        assertEquals("gestern", formatTimestamp("gestern", Locale.GERMAN, vienna))
        assertEquals("2026-13-40", formatDate("2026-13-40", Locale.GERMAN))
    }
}
