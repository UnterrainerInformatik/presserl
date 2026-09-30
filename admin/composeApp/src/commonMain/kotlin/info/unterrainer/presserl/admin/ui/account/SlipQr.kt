package info.unterrainer.presserl.admin.ui.account

import io.github.alexzhirkevich.qrose.QroseEncoders
import io.ktor.http.URLProtocol
import io.ktor.http.decodeURLPart
import io.ktor.http.parseUrl
import io.github.alexzhirkevich.qrose.matrix.QR
import io.github.alexzhirkevich.qrose.matrix.qr.QrErrorCorrection

/**
 * The QR code on the account slip (design D1): `<reader address>/qr?u=<username>#pw=<password>`. The password sits
 * in the fragment, which browsers never send to a server; the mobile app later reads all three parts from it.
 */
object SlipQr {

    /** The username rule of account creation. */
    private val USERNAME = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    private const val PATH = "/qr"
    private const val FRAGMENT_PREFIX = "pw="

    fun payload(siteUrl: String, username: String, password: String): String =
        "${siteUrl.trimEnd('/')}/qr?u=$username#pw=$password"

    /**
     * The inverse of [payload]: the server address, username and pass-phrase of a scanned slip, or `null` when [text]
     * is not a slip code. The address must use `https`, or `http` when [allowHttp] (debug builds).
     */
    fun parse(text: String, allowHttp: Boolean = false): SlipCredentials? {
        val url = parseUrl(text.trim()) ?: return null
        val schemeAllowed = url.protocol == URLProtocol.HTTPS || (allowHttp && url.protocol == URLProtocol.HTTP)
        if (!schemeAllowed || url.host.isEmpty() || url.user != null) return null
        if (!url.encodedPath.endsWith(PATH)) return null
        val username = url.parameters.getAll("u")?.singleOrNull()?.takeIf(USERNAME::matches) ?: return null
        val passPhrase = url.encodedFragment.takeIf { it.startsWith(FRAGMENT_PREFIX) }
            ?.removePrefix(FRAGMENT_PREFIX)?.decodeURLPart()?.takeIf { it.isNotEmpty() } ?: return null
        val port = if (url.specifiedPort == 0 || url.specifiedPort == url.protocol.defaultPort) "" else ":${url.port}"
        val base = "${url.protocol.name}://${url.host}$port${url.encodedPath.removeSuffix(PATH)}"
        return SlipCredentials(base, username, passPhrase)
    }
}

/** What a slip's QR code carries: the newspaper's address (without trailing slash), username and pass-phrase. */
data class SlipCredentials(val base: String, val username: String, val passPhrase: String) {
    override fun toString(): String = "SlipCredentials(base=$base, username=$username)"
}

/**
 * A QR code (error correction level M) as a square matrix of [size] modules, without the quiet zone; `true` is a
 * dark module. Encoded by qrose (`qrose-encoder-matrix`, MIT).
 */
class QrCode private constructor(val size: Int, private val modules: BooleanArray) {

    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    /** The dark runs of row [y] as `(start, length)` pairs, so renderers draw one shape per run. */
    fun darkRuns(y: Int): List<Pair<Int, Int>> = buildList {
        val end = this@QrCode.size
        var x = 0
        while (x < end) {
            if (get(x, y)) {
                val start = x
                while (x < end && get(x, y)) x++
                add(start to x - start)
            } else {
                x++
            }
        }
    }

    override fun toString(): String = "QrCode(size=$size)"

    companion object {
        /** Modules of light border around the code that scanners need. */
        const val QUIET_ZONE = 4

        fun encode(text: String): QrCode {
            val matrix = QroseEncoders.QR(QrErrorCorrection.M).encode(text)
            val size = matrix.width
            return QrCode(size, BooleanArray(size * size) { matrix[it % size, it / size] })
        }
    }
}
