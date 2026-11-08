package com.example.security

import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SecurityManager(private val context: Context) {

    private val secureRandom = SecureRandom()
    private val failedAttempts = ConcurrentHashMap<String, FailedAttempt>()

    data class FailedAttempt(var count: Int, var lockoutUntil: Long)

    companion object {
        private const val ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        private const val SALT_BYTES = 16
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }

    // --- SECURE PASSWORD HASHING (PBKDF2-HMAC-SHA256) ---

    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)

        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded

        val saltHex = bytesToHex(salt)
        val hashHex = bytesToHex(hash)
        return "$saltHex:$hashHex"
    }

    fun verifyPassword(password: String, storedHashWithSalt: String): Boolean {
        val parts = storedHashWithSalt.split(":")
        if (parts.size != 2) return false

        val salt = hexToBytes(parts[0])
        val expectedHash = hexToBytes(parts[1])

        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val computedHash = factory.generateSecret(spec).encoded

        // Constant time comparison to defeat timing attacks
        return MessageDigest.isEqual(expectedHash, computedHash)
    }

    // --- SECURE ENCRYPTION FOR API KEYS (AES-256-GCM) ---

    private fun getDeviceEncryptionKey(): SecretKeySpec {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "termux_ai_device_key"
        val seed = (context.packageName + ":" + androidId).toByteArray(Charsets.UTF_8)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(seed)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encryptSecret(plainText: String): String {
        if (plainText.isBlank()) return ""
        try {
            val key = getDeviceEncryptionKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(GCM_IV_LENGTH)
            secureRandom.nextBytes(iv)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            return ""
        }
    }

    fun decryptSecret(cipherBase64: String): String {
        if (cipherBase64.isBlank()) return ""
        try {
            val combined = Base64.decode(cipherBase64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return ""

            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

            val cipherTextSize = combined.size - GCM_IV_LENGTH
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherTextSize)

            val key = getDeviceEncryptionKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            val plainBytes = cipher.doFinal(cipherText)
            return String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // Fallback for transition from legacy plaintext if decryption fails
            return if (!cipherBase64.startsWith("eyJ") && cipherBase64.length < 150) cipherBase64 else ""
        }
    }

    // --- MASK SECRETS FOR DISPLAY ---

    fun maskSecret(secret: String): String {
        val trimmed = secret.trim()
        if (trimmed.isEmpty()) return "[NO CONFIGURADO]"
        if (trimmed.length <= 10) return "****"
        val prefix = trimmed.take(7)
        val suffix = trimmed.takeLast(4)
        return "$prefix***$suffix"
    }

    // --- BRUTE FORCE LOCKOUT PROTECTION ---

    fun isIpLockedOut(ip: String): Boolean {
        val record = failedAttempts[ip] ?: return false
        val now = System.currentTimeMillis()
        if (now > record.lockoutUntil) {
            failedAttempts.remove(ip)
            return false
        }
        return record.count >= MAX_FAILED_ATTEMPTS
    }

    fun getRemainingLockoutSeconds(ip: String): Long {
        val record = failedAttempts[ip] ?: return 0
        val remaining = record.lockoutUntil - System.currentTimeMillis()
        return if (remaining > 0) remaining / 1000 else 0
    }

    fun recordFailedAttempt(ip: String): Int {
        val now = System.currentTimeMillis()
        val record = failedAttempts.compute(ip) { _, existing ->
            if (existing == null || now > existing.lockoutUntil) {
                FailedAttempt(1, 0)
            } else {
                existing.count++
                if (existing.count >= MAX_FAILED_ATTEMPTS) {
                    existing.lockoutUntil = now + LOCKOUT_DURATION_MS
                }
                existing
            }
        }
        return record?.count ?: 1
    }

    fun resetFailedAttempts(ip: String) {
        failedAttempts.remove(ip)
    }

    // --- COMMAND SAFETY AUDIT ---

    fun validateCommandSafety(command: String): SafetyResult {
        val clean = command.trim().lowercase()

        val destructivePatterns = listOf(
            Regex("^rm\\s+-r?f?\\s+[/~\\*]+$"),
            Regex("rm\\s+-r?f?\\s+/(?:bin|etc|system|data|boot)"),
            Regex(":\\(\\)\\s*\\{\\s*:\\|:&\\s*\\};:"), // fork bomb
            Regex("mkfs\\."),
            Regex("dd\\s+if=/dev/zero"),
            Regex(">\\s*/dev/sd[a-z]")
        )

        for (pattern in destructivePatterns) {
            if (pattern.containsMatchIn(clean)) {
                return SafetyResult(
                    isSafe = false,
                    reason = "Comando destructivo bloqueado por política de seguridad del terminal."
                )
            }
        }

        return SafetyResult(isSafe = true, reason = null)
    }

    data class SafetyResult(val isSafe: Boolean, val reason: String?)

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = hex.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }
}
