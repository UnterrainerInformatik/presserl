package info.unterrainer.presserl.admin.ui.account

/**
 * The account slip as a standalone HTML document for platforms that print HTML (Android): the same content and
 * layout as the web print (styles.css), all text escaped, the QR code as inline SVG of the code's dark runs.
 */
fun slipHtml(slip: PrintableSlip): String = buildString {
    append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>").append(SLIP_CSS).append("</style></head><body>")
    append("<section id=\"presserl-slip\">")
    append("<h1>").append(escape(slip.title)).append("</h1>")
    append("<h2>").append(escape(slip.heading)).append("</h2>")
    append("<figure>").append(qrSvg(QrCode.encode(slip.qrPayload)))
    append("<figcaption>").append(escape(slip.qrHint)).append("</figcaption></figure>")
    append("<dl>")
    slip.rows.forEachIndexed { index, (label, value) ->
        append("<dt>").append(escape(label)).append("</dt>")
        append(if (index == slip.secretRow) "<dd class=\"secret\">" else "<dd>").append(escape(value)).append("</dd>")
    }
    append("</dl>")
    append("<p>").append(escape(slip.note)).append("</p>")
    append("</section></body></html>")
}

private fun escape(text: String): String = buildString {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}

/** [code] in a viewBox of modules including the quiet zone: a white square and one path of dark runs. */
private fun qrSvg(code: QrCode): String {
    val extent = code.size + 2 * QrCode.QUIET_ZONE
    val path = buildString {
        for (y in 0 until code.size) {
            for ((start, length) in code.darkRuns(y)) {
                append("M${start + QrCode.QUIET_ZONE} ${y + QrCode.QUIET_ZONE}h${length}v1h-${length}z")
            }
        }
    }
    return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $extent $extent\" shape-rendering=\"crispEdges\">" +
        "<rect width=\"$extent\" height=\"$extent\" fill=\"#fff\"/><path d=\"$path\" fill=\"#000\"/></svg>"
}

/** The print rules of styles.css (`#presserl-slip` in `@media print`), always applied here. */
private const val SLIP_CSS = """
@page { size: A4; margin: 20mm; }
html, body { margin: 0; background: #fff; }
#presserl-slip { color: #000; font-family: Georgia, "Times New Roman", serif; break-inside: avoid; }
#presserl-slip h1 { font-size: 28pt; margin: 0 0 4mm; }
#presserl-slip h2 { font-size: 16pt; font-weight: normal; margin: 0 0 10mm; }
#presserl-slip figure { float: right; width: 35mm; margin: 0 0 6mm 8mm; text-align: center; }
#presserl-slip figure svg { display: block; width: 35mm; height: 35mm; }
#presserl-slip figcaption { font-size: 9pt; margin-top: 2mm; }
#presserl-slip dt { font-size: 11pt; margin-top: 6mm; }
#presserl-slip dd { font-family: "DejaVu Sans Mono", Consolas, "Courier New", monospace; font-size: 16pt; margin: 1mm 0 0; word-break: break-all; }
#presserl-slip dd.secret { font-size: 24pt; font-weight: bold; }
#presserl-slip p { clear: both; font-size: 11pt; margin-top: 12mm; }
"""
