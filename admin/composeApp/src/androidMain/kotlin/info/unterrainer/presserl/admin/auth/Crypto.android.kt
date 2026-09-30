package info.unterrainer.presserl.admin.auth

import java.security.MessageDigest
import java.security.SecureRandom

private val random = SecureRandom()

actual suspend fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
