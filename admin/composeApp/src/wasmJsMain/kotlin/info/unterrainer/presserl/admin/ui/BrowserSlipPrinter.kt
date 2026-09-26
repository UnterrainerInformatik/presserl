package info.unterrainer.presserl.admin.ui

import info.unterrainer.presserl.admin.ui.account.PrintableSlip
import info.unterrainer.presserl.admin.ui.account.SlipPrinter
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.events.Event

/**
 * Prints the slip from real DOM elements, since the Compose canvas prints as a blurry bitmap:
 * `<section id="presserl-slip">` is built with `textContent` only (never `innerHTML`), shown alone
 * by the print rules in styles.css and removed after printing. No inline styles or scripts, so the
 * CSP stays unchanged.
 */
class BrowserSlipPrinter : SlipPrinter {

    override fun print(slip: PrintableSlip) {
        document.getElementById(SLIP_ID)?.remove()
        val section = document.createElement("section")
        section.id = SLIP_ID
        section.appendChild(text("h1", slip.title))
        section.appendChild(text("h2", slip.heading))
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

    private companion object {
        const val SLIP_ID = "presserl-slip"
    }
}
