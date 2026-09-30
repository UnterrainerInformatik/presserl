package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.ui.account.PrintableSlip
import info.unterrainer.presserl.admin.ui.account.QrCode
import info.unterrainer.presserl.admin.ui.account.slipHtml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlipHtmlTest {

    private val slip = PrintableSlip(
        title = "Schul<zeitung> & \"Co\"",
        heading = "Dein Zugang",
        rows = listOf("Benutzername" to "lena", "Passwort" to "tiger-wolke-apfel-leiter"),
        secretRow = 1,
        note = "Nur jetzt sichtbar",
        qrPayload = "https://zeitung.example.org/qr?u=lena#pw=tiger-wolke-apfel-leiter",
        qrHint = "Mit der App scannen",
    )

    @Test
    fun textIsEscaped() {
        val html = slipHtml(slip)

        assertTrue("<h1>Schul&lt;zeitung&gt; &amp; &quot;Co&quot;</h1>" in html, html)
        assertFalse("<zeitung>" in html)
    }

    @Test
    fun secretRowIsPrintedLarge() {
        val html = slipHtml(slip)

        assertTrue("<dt>Passwort</dt><dd class=\"secret\">tiger-wolke-apfel-leiter</dd>" in html)
        assertTrue("<dt>Benutzername</dt><dd>lena</dd>" in html)
    }

    @Test
    fun qrCodeHasOneSubpathPerDarkRun() {
        val code = QrCode.encode(slip.qrPayload)
        val runs = (0 until code.size).sumOf { code.darkRuns(it).size }

        assertEquals(runs, Regex("M\\d+ \\d+h\\d+v1h-\\d+z").findAll(slipHtml(slip)).count())
        assertTrue("viewBox=\"0 0 45 45\"" in slipHtml(slip))
    }
}
