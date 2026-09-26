package info.unterrainer.presserl.admin.auth

import kotlin.io.encoding.Base64

/** SHA-256 digest (Web Crypto in the browser). */
expect suspend fun sha256(data: ByteArray): ByteArray

/** Cryptographically secure random bytes. */
expect fun secureRandomBytes(size: Int): ByteArray

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

fun base64UrlEncode(bytes: ByteArray): String = base64Url.encode(bytes)

/**
 * PKCE (RFC 7636) with the `S256` method, plus the `state` value guarding the redirect.
 */
object Pkce {

    const val METHOD = "S256"

    /** 32 random bytes → 43 characters, the minimum verifier length. */
    fun newVerifier(): String = base64UrlEncode(secureRandomBytes(32))

    fun newState(): String = base64UrlEncode(secureRandomBytes(16))

    suspend fun challenge(verifier: String): String = base64UrlEncode(sha256(verifier.encodeToByteArray()))

    /** The state returned by the issuer must be the one we sent; anything else is a forged callback. */
    fun stateMatches(expected: String?, actual: String?): Boolean =
        !expected.isNullOrEmpty() && expected == actual
}
