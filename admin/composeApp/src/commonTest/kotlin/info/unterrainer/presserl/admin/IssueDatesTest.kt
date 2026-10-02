package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.ui.issue.DateInput
import info.unterrainer.presserl.admin.ui.issue.epochMillisToIso
import info.unterrainer.presserl.admin.ui.issue.isoToEpochMillis
import info.unterrainer.presserl.admin.ui.issue.parseDate
import kotlin.test.Test
import kotlin.test.assertEquals

class IssueDatesTest {
    private val day = 86_400_000L

    @Test
    fun convertsKnownDates() {
        assertEquals(0L, isoToEpochMillis("1970-01-01"))
        assertEquals("1970-01-01", epochMillisToIso(0L))
        // 2024-02-29 is day 19 782 after the epoch
        assertEquals(19_782 * day, isoToEpochMillis("2024-02-29"))
        assertEquals("2024-02-29", epochMillisToIso(19_782 * day))
        assertEquals(20_738 * day, isoToEpochMillis("2026-10-12"))
        assertEquals("2026-10-12", epochMillisToIso(20_738 * day))
        assertEquals("2026-12-31", epochMillisToIso(isoToEpochMillis("2027-01-01") - day))
        assertEquals("1969-12-31", epochMillisToIso(-1L))
    }

    @Test
    fun anyTimeOfTheDayGivesThatDate() {
        assertEquals("2026-10-12", epochMillisToIso(isoToEpochMillis("2026-10-12") + day - 1))
    }

    @Test
    fun roundTripsEveryDayOverCenturies() {
        // 1899-03-01 .. ~2100: covers the 1900 and 2100 non-leap years and the 2000 leap year
        var previous: String? = null
        for (days in -25_873L..47_500L) {
            val iso = epochMillisToIso(days * day)
            assertEquals(DateInput.Valid(iso), parseDate(iso), "day $days")
            assertEquals(days * day, isoToEpochMillis(iso), iso)
            previous?.let { assertEquals(true, it < iso, "$it before $iso") }
            previous = iso
        }
    }
}
