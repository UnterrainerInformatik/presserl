package info.unterrainer.presserl.admin.ui.account

/**
 * The account slip as printed: [title] (newspaper name), [heading], labelled [rows], a QR code of [qrPayload] with
 * the line [qrHint] and a [note]. [secretRow] is the index of the row printed large (the password). The payload
 * contains the password, so neither shows up in [toString].
 */
data class PrintableSlip(
    val title: String,
    val heading: String,
    val rows: List<Pair<String, String>>,
    val secretRow: Int,
    val note: String,
    val qrPayload: String,
    val qrHint: String,
) {
    override fun toString(): String = "PrintableSlip(title=$title)"
}

/** Opens the browser's print dialog for the slip alone (design D10). */
fun interface SlipPrinter {
    fun print(slip: PrintableSlip)
}
