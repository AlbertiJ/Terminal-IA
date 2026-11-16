package com.example.ssh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface KeyWrappingCipher {
    fun encrypt(plainBytes: ByteArray): String
    fun decrypt(encoded: String): ByteArray
}

/**
 * Encrypts sensitive key material with a non-exportable AES-256-GCM key stored in AndroidKeyStore.
 */
class AndroidKeyStoreWrappingCipher(
    private val keyAlias: String = DEFAULT_ALIAS
) : KeyWrappingCipher {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val DEFAULT_ALIAS = "terminal_ia_ssh_ed25519_wrap"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    override fun encrypt(plainBytes: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainBytes)

        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): ByteArray {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH) { "Datos cifrados inválidos." }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val cipherBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherBytes)
    }
}

sealed class HostKeyCheckResult {
    data class TrustedFirstUse(val fingerprintSha256: String, val algorithm: String) : HostKeyCheckResult()
    data class Matched(val fingerprintSha256: String, val algorithm: String) : HostKeyCheckResult()
    data class Mismatch(
        val expectedFingerprint: String,
        val actualFingerprint: String,
        val algorithm: String
    ) : HostKeyCheckResult()
}

/**
 * Manages the app-only Ed25519 identity (private key encrypted in AndroidKeyStore and never exported)
 * as well as TOFU (Trust-On-First-Use) SSH host key verification.
 */
class SshKeyManager(
    context: Context,
    private val keyCipher: KeyWrappingCipher = AndroidKeyStoreWrappingCipher()
) {
    private val keyPrefs = context.getSharedPreferences(PREFS_KEYS, Context.MODE_PRIVATE)
    private val hostPrefs = context.getSharedPreferences(PREFS_HOSTS, Context.MODE_PRIVATE)
    private val lock = Any()

    companion object {
        private const val PREFS_KEYS = "terminal_ia_ssh_keys"
        private const val PREFS_HOSTS = "terminal_ia_ssh_hosts"
        private const val KEY_PRIV_ENC = "ed25519_priv_enc"
        private const val KEY_PUB_OPENSSH = "ed25519_pub_openssh"
        private const val KEY_COMMENT = "terminal-ia@android"
    }

    fun hasIdentity(): Boolean {
        return !keyPrefs.getString(KEY_PRIV_ENC, null).isNullOrBlank() &&
            !keyPrefs.getString(KEY_PUB_OPENSSH, null).isNullOrBlank()
    }

    fun getOrCreatePublicKey(): String = synchronized(lock) {
        val existing = keyPrefs.getString(KEY_PUB_OPENSSH, null)
        val existingPriv = keyPrefs.getString(KEY_PRIV_ENC, null)
        if (!existing.isNullOrBlank() && !existingPriv.isNullOrBlank()) {
            return existing
        }
        return generateAndStoreNewIdentityLocked()
    }

    fun regenerateIdentity(): String = synchronized(lock) {
        generateAndStoreNewIdentityLocked()
    }

    fun getPublicKeyFingerprintSha256(): String {
        val pubLine = getOrCreatePublicKey()
        val base64Blob = pubLine.trim().split(Regex("\\s+")).getOrNull(1) ?: return ""
        val decoded = Base64.decode(base64Blob, Base64.DEFAULT)
        return computeFingerprintSha256(decoded)
    }

    /**
     * Loads decrypted private key bytes ONLY for in-memory JSch authentication.
     * Callers must zero the returned ByteArray after passing it to JSch.
     */
    internal fun loadPrivateKeyBytesForSsh(): ByteArray = synchronized(lock) {
        if (!hasIdentity()) {
            generateAndStoreNewIdentityLocked()
        }
        val enc = keyPrefs.getString(KEY_PRIV_ENC, null)
            ?: throw IllegalStateException("No existe clave privada SSH almacenada.")
        return keyCipher.decrypt(enc)
    }

    private fun generateAndStoreNewIdentityLocked(): String {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val pair = generator.generateKeyPair()

        val privParams = pair.private as Ed25519PrivateKeyParameters
        val pubParams = pair.public as Ed25519PublicKeyParameters

        val pubWireBytes = OpenSSHPublicKeyUtil.encodePublicKey(pubParams)
        val pubBase64 = Base64.encodeToString(pubWireBytes, Base64.NO_WRAP)
        val openSshPub = "ssh-ed25519 $pubBase64 $KEY_COMMENT"

        val openSshPrivBytes = OpenSSHPrivateKeyUtil.encodePrivateKey(privParams)
        val pemOut = ByteArrayOutputStream()
        PemWriter(OutputStreamWriter(pemOut, Charsets.UTF_8)).use { writer ->
            writer.writeObject(PemObject("OPENSSH PRIVATE KEY", openSshPrivBytes))
        }
        val privBytes = pemOut.toByteArray()

        try {
            val encryptedPriv = keyCipher.encrypt(privBytes)
            keyPrefs.edit()
                .putString(KEY_PRIV_ENC, encryptedPriv)
                .putString(KEY_PUB_OPENSSH, openSshPub)
                .apply()
        } finally {
            privBytes.fill(0)
            openSshPrivBytes.fill(0)
        }

        return openSshPub
    }

    // --- TOFU HOST KEY VERIFICATION ---

    fun getTrustedHostFingerprint(host: String, port: Int): String? {
        return hostPrefs.getString(hostKeyPrefId(host, port) + "_fp", null)
    }

    fun getTrustedHostAlgorithm(host: String, port: Int): String? {
        return hostPrefs.getString(hostKeyPrefId(host, port) + "_alg", null)
    }

    fun verifyOrTrustHostKey(
        host: String,
        port: Int,
        algorithm: String,
        hostKeyBytes: ByteArray
    ): HostKeyCheckResult = synchronized(lock) {
        val id = hostKeyPrefId(host, port)
        val actualB64 = Base64.encodeToString(hostKeyBytes, Base64.NO_WRAP)
        val actualFp = computeFingerprintSha256(hostKeyBytes)

        val storedB64 = hostPrefs.getString("${id}_b64", null)
        val storedFp = hostPrefs.getString("${id}_fp", null)

        if (storedB64 == null || storedFp == null) {
            hostPrefs.edit()
                .putString("${id}_b64", actualB64)
                .putString("${id}_fp", actualFp)
                .putString("${id}_alg", algorithm)
                .apply()
            return HostKeyCheckResult.TrustedFirstUse(actualFp, algorithm)
        }

        if (storedB64 == actualB64) {
            return HostKeyCheckResult.Matched(actualFp, algorithm)
        }

        return HostKeyCheckResult.Mismatch(
            expectedFingerprint = storedFp,
            actualFingerprint = actualFp,
            algorithm = algorithm
        )
    }

    fun acceptChangedHostKey(
        host: String,
        port: Int,
        algorithm: String,
        hostKeyBytes: ByteArray
    ): String = synchronized(lock) {
        val id = hostKeyPrefId(host, port)
        val actualB64 = Base64.encodeToString(hostKeyBytes, Base64.NO_WRAP)
        val actualFp = computeFingerprintSha256(hostKeyBytes)
        hostPrefs.edit()
            .putString("${id}_b64", actualB64)
            .putString("${id}_fp", actualFp)
            .putString("${id}_alg", algorithm)
            .apply()
        actualFp
    }

    fun clearTrustedHostKey(host: String, port: Int) = synchronized(lock) {
        val id = hostKeyPrefId(host, port)
        hostPrefs.edit()
            .remove("${id}_b64")
            .remove("${id}_fp")
            .remove("${id}_alg")
            .apply()
    }

    private fun hostKeyPrefId(host: String, port: Int): String = "host_${host}_$port"

    fun computeFingerprintSha256(keyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        val b64 = Base64.encodeToString(digest, Base64.NO_WRAP).trimEnd('=')
        return "SHA256:$b64"
    }
}
