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
