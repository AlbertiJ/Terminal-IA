package com.example.security

import android.content.Context
import com.example.ssh.AndroidKeyStoreWrappingCipher
import com.example.ssh.KeyWrappingCipher
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SecurityManager @JvmOverloads constructor(
    @Suppress("UNUSED_PARAMETER") private val context: Context,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val secretCipher: KeyWrappingCipher = AndroidKeyStoreWrappingCipher(SECRETS_KEY_ALIAS)
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
        const val SECRETS_KEY_ALIAS = "terminal_ia_api_secrets_wrap"
        const val AKS_PREFIX = "aks_v1:"
        const val CURRENT_PBKDF2_ITERATIONS = 310_000
        const val LEGACY_PBKDF2_ITERATIONS = 10_000
        private const val HASH_PREFIX = "pbkdf2_sha256"
        private const val KEY_LENGTH = 256
        private const val SALT_BYTES = 16
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes
        private const val ATTEMPT_WINDOW_MS = 15 * 60 * 1000L // 15 minutes

        private val SECRET_PATTERNS = listOf(
            Regex("""sk-ant-[A-Za-z0-9_-]{8,}"""),
            Regex("""sk-[A-Za-z0-9_-]{12,}"""),
            Regex("""(?i)(x-api-key\s*[:=]\s*)\S+"""),
            Regex("""(?i)(authorization\s*[:=]\s*bearer\s+)\S+"""),
            Regex("""(?i)(bearer\s+)[A-Za-z0-9._-]{8,}""")
        )

        fun redactSecrets(text: String, knownSecrets: Collection<String> = emptyList()): String {
            if (text.isEmpty()) return text
            var sanitized = text
            for (secret in knownSecrets) {
                val clean = secret.trim()
                if (clean.length >= 6) {
                    sanitized = sanitized.replace(clean, "[REDACTED]")
                }
            }
            sanitized = SECRET_PATTERNS[0].replace(sanitized, "[REDACTED]")
            sanitized = SECRET_PATTERNS[1].replace(sanitized, "[REDACTED]")
            sanitized = SECRET_PATTERNS[2].replace(sanitized) { "${it.groupValues[1]}[REDACTED]" }
            sanitized = SECRET_PATTERNS[3].replace(sanitized) { "${it.groupValues[1]}[REDACTED]" }
            sanitized = SECRET_PATTERNS[4].replace(sanitized) { "${it.groupValues[1]}[REDACTED]" }
            return sanitized
        }
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

    // --- SECURE ENCRYPTION FOR API KEYS (ANDROID KEYSTORE AES-256-GCM) ---

    fun encryptSecret(plainText: String): String {
        val trimmed = plainText.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val plainBytes = trimmed.toByteArray(Charsets.UTF_8)
            try {
                AKS_PREFIX + secretCipher.encrypt(plainBytes)
            } finally {
                plainBytes.fill(0)
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun decryptSecret(storedCipher: String): String {
        val trimmed = storedCipher.trim()
        if (trimmed.isEmpty()) return ""
        // Reject legacy ANDROID_ID-encrypted or plaintext blobs
        if (!trimmed.startsWith(AKS_PREFIX)) return ""
        val payload = trimmed.removePrefix(AKS_PREFIX)
        if (payload.isBlank()) return ""
        return try {
            val plainBytes = secretCipher.decrypt(payload)
            try {
                String(plainBytes, Charsets.UTF_8)
            } finally {
                plainBytes.fill(0)
            }
        } catch (_: Exception) {
            ""
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
