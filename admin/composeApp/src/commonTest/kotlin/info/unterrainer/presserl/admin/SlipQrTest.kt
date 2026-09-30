package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.ui.account.PrintableSlip
import info.unterrainer.presserl.admin.ui.account.QrCode
import info.unterrainer.presserl.admin.ui.account.SlipCredentials
import info.unterrainer.presserl.admin.ui.account.SlipQr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlipQrTest {

    private val payload = "https://zeitung.example.org/qr?u=lena#pw=tiger-wolke-apfel-leiter"

    @Test
    fun payloadOfTheSpecScenario() {
        assertEquals(payload, SlipQr.payload("https://zeitung.example.org", "lena", "tiger-wolke-apfel-leiter"))
    }

    @Test
    fun trailingSlashIsTrimmed() {
        assertEquals(payload, SlipQr.payload("https://zeitung.example.org/", "lena", "tiger-wolke-apfel-leiter"))
    }

    @Test
    fun parseIsTheInverseOfPayload() {
        val payload = SlipQr.payload("https://zeitung.example.org", "lena-2", "tiger-wolke-apfel-leiter")

        assertEquals(
            SlipCredentials("https://zeitung.example.org", "lena-2", "tiger-wolke-apfel-leiter"),
            SlipQr.parse(payload),
        )
    }

    @Test
    fun parseKeepsPathPrefixAndPort() {
        assertEquals(
            SlipCredentials("https://example.org:8443/zeitung", "lena", "tiger"),
            SlipQr.parse("https://example.org:8443/zeitung/qr?u=lena#pw=tiger"),
        )
    }

    @Test
    fun parseDropsTheDefaultPort() {
        assertEquals("https://example.org", SlipQr.parse("https://example.org:443/qr?u=lena#pw=tiger")?.base)
    }

    @Test
    fun parseOfAPayloadBuiltFromATrailingSlash() {
        val payload = SlipQr.payload("https://zeitung.example.org/", "lena", "tiger")

        assertEquals("https://zeitung.example.org", SlipQr.parse(payload)?.base)
    }

    @Test
    fun parseToleratesSurroundingWhitespace() {
        assertEquals("lena", SlipQr.parse("  $payload\n")?.username)
    }

    @Test
    fun parseDecodesAPercentEncodedPassPhrase() {
        assertEquals("über-maß", SlipQr.parse("https://example.org/qr?u=lena#pw=%C3%BCber-ma%C3%9F")?.passPhrase)
    }

    @Test
    fun parseRejectsMissingOrInvalidUsername() {
        assertNull(SlipQr.parse("https://example.org/qr#pw=tiger"))
        assertNull(SlipQr.parse("https://example.org/qr?u=#pw=tiger"))
        assertNull(SlipQr.parse("https://example.org/qr?u=Lena#pw=tiger"))
        assertNull(SlipQr.parse("https://example.org/qr?u=lena-#pw=tiger"))
        assertNull(SlipQr.parse("https://example.org/qr?u=le%20na#pw=tiger"))
        assertNull(SlipQr.parse("https://example.org/qr?u=lena&u=anna#pw=tiger"))
    }

    @Test
    fun parseRejectsMissingOrEmptyPassPhrase() {
        assertNull(SlipQr.parse("https://example.org/qr?u=lena"))
        assertNull(SlipQr.parse("https://example.org/qr?u=lena#pw="))
        assertNull(SlipQr.parse("https://example.org/qr?u=lena#password=tiger"))
    }

    @Test
    fun parseRejectsAnotherPath() {
        assertNull(SlipQr.parse("https://example.org/qrx?u=lena#pw=tiger"))
        assertNull(SlipQr.parse("https://example.org/qr/more?u=lena#pw=tiger"))
        assertNull(SlipQr.parse("https://example.org/?u=lena#pw=tiger"))
    }

    @Test
    fun parseRejectsForeignCodes() {
        assertNull(SlipQr.parse("https://example.com/some/page"))
        assertNull(SlipQr.parse("WIFI:S:home;T:WPA;P:secret;;"))
        assertNull(SlipQr.parse("just some text"))
        assertNull(SlipQr.parse(""))
        assertNull(SlipQr.parse("ftp://example.org/qr?u=lena#pw=tiger"))
        assertNull(SlipQr.parse("https://evil@example.org/qr?u=lena#pw=tiger"))
    }

    @Test
    fun parseAcceptsHttpOnlyWhenAllowed() {
        val http = "http://10.0.2.2:8080/qr?u=lena#pw=tiger"

        assertNull(SlipQr.parse(http))
        assertEquals(SlipCredentials("http://10.0.2.2:8080", "lena", "tiger"), SlipQr.parse(http, allowHttp = true))
    }

    @Test
    fun credentialsHidePassPhrase() {
        assertFalse(SlipCredentials("https://example.org", "lena", "tiger").toString().contains("tiger"))
    }

    @Test
    fun encodesVersionFiveWithFinderPatterns() {
        // 65 bytes at level M need version 5: 4 * 5 + 17 modules
        val code = QrCode.encode(payload)

        assertEquals(37, code.size)
        for ((left, top) in listOf(0 to 0, code.size - 7 to 0, 0 to code.size - 7)) {
            assertFinderPattern(code, left, top)
        }
    }

    @Test
    fun darkRunsCoverExactlyTheDarkModules() {
        val code = QrCode.encode(payload)

        for (y in 0 until code.size) {
            val fromRuns = BooleanArray(code.size)
            code.darkRuns(y).forEach { (start, length) -> for (x in start until start + length) fromRuns[x] = true }
            assertEquals((0 until code.size).map { code[it, y] }, fromRuns.toList(), "row $y")
        }
    }

    @Test
    fun printableSlipHidesPasswordAndPayload() {
        val slip = PrintableSlip(
            title = "Zeitung",
            heading = "Dein Zugang",
            rows = listOf("Passwort" to "tiger-wolke-apfel-leiter"),
            secretRow = 0,
            note = "Nur jetzt",
            qrPayload = payload,
            qrHint = "Code scannen öffnet die Anmeldung.",
        )

        assertFalse(slip.toString().contains("tiger"))
        assertFalse(slip.toString().contains("/qr"))
    }

    /** 7×7: dark ring, light ring, dark 3×3 core. */
    private fun assertFinderPattern(code: QrCode, left: Int, top: Int) {
        for (dy in 0 until 7) {
            for (dx in 0 until 7) {
                val ring = maxOf(kotlin.math.abs(dx - 3), kotlin.math.abs(dy - 3))
                assertTrue(code[left + dx, top + dy] == (ring != 2), "finder at ($left,$top) module ($dx,$dy)")
            }
        }
    }
}
