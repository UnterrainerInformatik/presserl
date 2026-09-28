package info.unterrainer.presserl.admin.ui.account

import io.github.alexzhirkevich.qrose.QroseEncoders
import io.github.alexzhirkevich.qrose.matrix.QR
import io.github.alexzhirkevich.qrose.matrix.qr.QrErrorCorrection

/**
 * The QR code on the account slip (design D1): `<reader address>/qr?u=<username>#pw=<password>`. The password sits
 * in the fragment, which browsers never send to a server; the mobile app later reads all three parts from it.
 */
object SlipQr {

    fun payload(siteUrl: String, username: String, password: String): String =
        "${siteUrl.trimEnd('/')}/qr?u=$username#pw=$password"
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
