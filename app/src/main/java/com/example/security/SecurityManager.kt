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

class SecurityManager @JvmOverloads constructor(
    private val context: Context,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    private val secureRandom = SecureRandom()
    private val failedAttempts = ConcurrentHashMap<String, FailedAttempt>()

    data class FailedAttempt(
        var count: Int,
        var lockoutUntil: Long,
        var lastAttemptAt: Long
    )

    private data class ParsedPasswordHash(
        val iterations: Int,
        val salt: ByteArray,
        val expectedHash: ByteArray
    )

    companion object {
        const val CURRENT_PBKDF2_ITERATIONS = 310_000
        const val LEGACY_PBKDF2_ITERATIONS = 10_000
        private const val HASH_PREFIX = "pbkdf2_sha256"
        private const val KEY_LENGTH = 256
        private const val SALT_BYTES = 16
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes
        private const val ATTEMPT_WINDOW_MS = 15 * 60 * 1000L // 15 minutes
    }

    // --- SECURE PASSWORD HASHING (PBKDF2-HMAC-SHA256) ---

    fun hashPassword(password: String): String {
        return hashPasswordWithIterations(password, CURRENT_PBKDF2_ITERATIONS)
    }

    fun hashPasswordWithIterations(password: String, iterations: Int): String {
        require(iterations >= 1) { "PBKDF2 iterations must be positive" }
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)

        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()

        val saltHex = bytesToHex(salt)
        val hashHex = bytesToHex(hash)
        return "$HASH_PREFIX\$$iterations\$$saltHex:$hashHex"
    }

    fun getHashIterations(storedHashWithSalt: String): Int? {
        return parseStoredPasswordHash(storedHashWithSalt)?.iterations
    }

    fun needsPasswordRehash(storedHashWithSalt: String): Boolean {
        val iters = getHashIterations(storedHashWithSalt) ?: return false
        return iters < CURRENT_PBKDF2_ITERATIONS
    }

    fun verifyPassword(password: String, storedHashWithSalt: String): Boolean {
        val parsed = parseStoredPasswordHash(storedHashWithSalt) ?: return false

        val spec = PBEKeySpec(password.toCharArray(), parsed.salt, parsed.iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val computedHash = factory.generateSecret(spec).encoded
        spec.clearPassword()

        // Constant time comparison to defeat timing attacks
        return MessageDigest.isEqual(parsed.expectedHash, computedHash)
    }

    private fun parseStoredPasswordHash(stored: String): ParsedPasswordHash? {
        val trimmed = stored.trim()
        if (trimmed.isEmpty()) return null

        val iterations: Int
        val saltAndHashPart: String

        if (trimmed.startsWith("$HASH_PREFIX\$")) {
            val headerSplit = trimmed.split("$", limit = 3)
            if (headerSplit.size != 3) return null
            iterations = headerSplit[1].toIntOrNull() ?: return null
            if (iterations !in 1..5_000_000) return null
            saltAndHashPart = headerSplit[2]
        } else {
            iterations = LEGACY_PBKDF2_ITERATIONS
            saltAndHashPart = trimmed
        }

        val parts = saltAndHashPart.split(":")
        if (parts.size != 2) return null
        val saltHex = parts[0]
        val hashHex = parts[1]
        if (saltHex.isEmpty() || hashHex.isEmpty() || saltHex.length % 2 != 0 || hashHex.length % 2 != 0) {
            return null
        }

        return try {
            ParsedPasswordHash(
                iterations = iterations,
                salt = hexToBytes(saltHex),
                expectedHash = hexToBytes(hashHex)
            )
        } catch (_: Exception) {
            null
        }
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
        val now = timeProvider()
        if (record.count >= MAX_FAILED_ATTEMPTS) {
            if (now <= record.lockoutUntil) {
                return true
            }
            failedAttempts.remove(ip)
            return false
        }
        return false
    }

    fun getRemainingLockoutSeconds(ip: String): Long {
        val record = failedAttempts[ip] ?: return 0
        if (record.count < MAX_FAILED_ATTEMPTS) return 0
        val remaining = record.lockoutUntil - timeProvider()
        return if (remaining > 0) (remaining + 999) / 1000 else 0
    }

    fun recordFailedAttempt(ip: String): Int {
        val now = timeProvider()
        val record = failedAttempts.compute(ip) { _, existing ->
            when {
                existing == null -> FailedAttempt(count = 1, lockoutUntil = 0L, lastAttemptAt = now)
                existing.count >= MAX_FAILED_ATTEMPTS && now > existing.lockoutUntil ->
                    FailedAttempt(count = 1, lockoutUntil = 0L, lastAttemptAt = now)
                existing.count < MAX_FAILED_ATTEMPTS && (now - existing.lastAttemptAt) > ATTEMPT_WINDOW_MS ->
                    FailedAttempt(count = 1, lockoutUntil = 0L, lastAttemptAt = now)
                else -> {
                    existing.count++
                    existing.lastAttemptAt = now
                    if (existing.count >= MAX_FAILED_ATTEMPTS) {
                        existing.lockoutUntil = now + LOCKOUT_DURATION_MS
                    }
                    existing
                }
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
