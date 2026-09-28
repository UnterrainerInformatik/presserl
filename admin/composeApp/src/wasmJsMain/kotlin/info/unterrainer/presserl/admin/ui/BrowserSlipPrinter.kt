package info.unterrainer.presserl.admin.ui

import info.unterrainer.presserl.admin.ui.account.PrintableSlip
import info.unterrainer.presserl.admin.ui.account.QrCode
import info.unterrainer.presserl.admin.ui.account.SlipPrinter
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.events.Event

/**
 * Prints the slip from real DOM elements, since the Compose canvas prints as a blurry bitmap:
 * `<section id="presserl-slip">` is built with `textContent` only (never `innerHTML`), shown alone
 * by the print rules in styles.css and removed after printing. The QR code is an `<svg>` built with
 * `createElementNS`, coloured by the `fill` attribute. No inline styles or scripts, so the CSP stays
 * unchanged.
 */
class BrowserSlipPrinter : SlipPrinter {

    override fun print(slip: PrintableSlip) {
        document.getElementById(SLIP_ID)?.remove()
        val section = document.createElement("section")
        section.id = SLIP_ID
        section.appendChild(text("h1", slip.title))
        section.appendChild(text("h2", slip.heading))
        section.appendChild(document.createElement("figure").also {
            it.appendChild(qrSvg(QrCode.encode(slip.qrPayload)))
            it.appendChild(text("figcaption", slip.qrHint))
        })
        val rows = document.createElement("dl")
        slip.rows.forEachIndexed { index, (label, value) ->
            rows.appendChild(text("dt", label))
            rows.appendChild(text("dd", value).also { if (index == slip.secretRow) it.className = "secret" })
        }
        section.appendChild(rows)
        section.appendChild(text("p", slip.note))
        document.body!!.appendChild(section)

        lateinit var cleanUp: (Event) -> Unit
        cleanUp = {
            window.removeEventListener("afterprint", cleanUp)
            section.remove()
        }
        window.addEventListener("afterprint", cleanUp)
        window.print()
    }

    private fun text(tag: String, content: String): Element =
        document.createElement(tag).also { it.textContent = content }

    /** [code] in a viewBox of modules including the quiet zone: a white square and one path of dark runs. */
    private fun qrSvg(code: QrCode): Element {
        val extent = (code.size + 2 * QrCode.QUIET_ZONE).toString()
        val path = buildString {
            for (y in 0 until code.size) {
                for ((start, length) in code.darkRuns(y)) {
                    append("M${start + QrCode.QUIET_ZONE} ${y + QrCode.QUIET_ZONE}h${length}v1h-${length}z")
                }
            }
        }
        return svg("svg").apply {
            setAttribute("viewBox", "0 0 $extent $extent")
            setAttribute("shape-rendering", "crispEdges")
            appendChild(svg("rect").apply {
                setAttribute("width", extent)
                setAttribute("height", extent)
                setAttribute("fill", "#fff")
            })
            appendChild(svg("path").apply {
                setAttribute("d", path)
                setAttribute("fill", "#000")
            })
        }
    }

    private fun svg(tag: String): Element = document.createElementNS(SVG_NS, tag)

    private companion object {
        const val SLIP_ID = "presserl-slip"
        const val SVG_NS = "http://www.w3.org/2000/svg"
    }
}
