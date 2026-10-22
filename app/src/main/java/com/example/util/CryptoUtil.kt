package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom

object CryptoUtil {
    private val secureRandom = SecureRandom()

    fun generateSalt(length: Int = 16): String {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPassword(password: String, salt: String): String {
        val input = "$salt:$password:GeoTrack_Secure_V1"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(attempt: String, salt: String, expectedHash: String): Boolean {
        val hashedAttempt = hashPassword(attempt, salt)
        return MessageDigest.isEqual(
            hashedAttempt.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }
}
