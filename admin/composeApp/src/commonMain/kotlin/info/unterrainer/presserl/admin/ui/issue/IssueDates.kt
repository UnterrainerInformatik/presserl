package info.unterrainer.presserl.admin.ui.issue

/** A publication date as typed in `yyyy-mm-dd`. */
sealed interface DateInput {
    /** Nothing typed: no publication date. */
    data object Empty : DateInput

    /** A calendar date; [iso] is zero-padded (`2026-10-12`). */
    data class Valid(val iso: String) : DateInput

    data object Invalid : DateInput
}

private val DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")

/** Reads [input] (surrounding blanks ignored); only existing calendar dates are valid. */
fun parseDate(input: String): DateInput {
    val text = input.trim()
    if (text.isEmpty()) return DateInput.Empty
    val match = DATE.matchEntire(text) ?: return DateInput.Invalid
    val (year, month, day) = match.destructured.toList().map { it.toInt() }
    if (year < 1 || month !in 1..12 || day !in 1..daysIn(year, month)) return DateInput.Invalid
    return DateInput.Valid(year.toString().padStart(4, '0') + "-" + month.pad() + "-" + day.pad())
}

private fun Int.pad(): String = toString().padStart(2, '0')

private fun daysIn(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private const val MILLIS_PER_DAY = 86_400_000L

/** UTC midnight of [iso], a date as [parseDate] returns it (`2026-10-12`), in milliseconds since the epoch. */
fun isoToEpochMillis(iso: String): Long {
    val (year, month, day) = iso.split('-').map { it.toInt() }
    return daysFromCivil(year, month, day) * MILLIS_PER_DAY
}

/** The UTC calendar date of [millis] since the epoch as `yyyy-mm-dd`. */
fun epochMillisToIso(millis: Long): String {
    val (year, month, day) = civilFromDays(millis.floorDiv(MILLIS_PER_DAY))
    return year.toString().padStart(4, '0') + "-" + month.pad() + "-" + day.pad()
}

// Days-from-civil and its inverse after Howard Hinnant (http://howardhinnant.github.io/date_algorithms.html);
// the year is shifted to start in March, so the leap day is the last day of a 400-year era's year.

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = y.floorDiv(400L)
    val yearOfEra = y - era * 400
    val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146_097 + dayOfEra - 719_468
}

private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val dayOfEra = z - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val shiftedMonth = (5 * dayOfYear + 2) / 153
    val day = (dayOfYear - (153 * shiftedMonth + 2) / 5 + 1).toInt()
    val month = (if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9).toInt()
    val year = (yearOfEra + era * 400 + if (month <= 2) 1 else 0).toInt()
    return Triple(year, month, day)
}
