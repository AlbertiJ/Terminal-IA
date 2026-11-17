package com.example.ssh

import android.content.Context
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class SshConnectionError {
    data object TermuxNotInstalled : SshConnectionError()
    data class InvalidPort(val port: Int) : SshConnectionError()
    data class SshdNotRunning(val host: String, val port: Int, val detail: String) : SshConnectionError()
    data class KeyNotAuthorized(val detail: String) : SshConnectionError()
    data class WrongPortOrTimeout(val host: String, val port: Int, val detail: String) : SshConnectionError()
    data class HostKeyChanged(
        val host: String,
        val port: Int,
        val expectedFingerprint: String,
        val actualFingerprint: String,
        val algorithm: String,
        val hostKeyBytes: ByteArray
    ) : SshConnectionError()
    data class OtherError(val message: String) : SshConnectionError()

    fun toUserMessage(): String = when (this) {
        is TermuxNotInstalled ->
            "Falta instalar Termux (F-Droid / GitHub). Instálalo primero en el Paso 1."
        is InvalidPort ->
            "Puerto inválido ($port). Usa un puerto sin root entre 1024 y 65535 (ej. 8022)."
        is SshdNotRunning ->
            "No hay servidor SSH escuchando en $host:$port. Termux está instalado, pero sshd está apagado o usa otro puerto. Pega el comando del Paso 2 en Termux."
        is KeyNotAuthorized ->
            "sshd respondió, pero rechazó la clave pública Ed25519 de Terminal-IA. Ejecutá de nuevo el comando del Paso 2 en Termux para autorizarla en ~/.ssh/authorized_keys (chmod 600)."
        is WrongPortOrTimeout ->
            "Tiempo de espera agotado o puerto incorrecto ($host:$port). Verifica que sshd escuche en ese puerto."
        is HostKeyChanged ->
            "¡ALERTA DE SEGURIDAD! La clave del servidor SSH en $host:$port cambió.\nEsperada: $expectedFingerprint\nRecibida: $actualFingerprint\nSi reinstalaste Termux, podés aceptar la nueva clave manualmente."
        is OtherError ->
            "Error de conexión SSH: $message"
    }
}

sealed class SshDiagnosticResult {
    data class Success(
        val host: String,
        val port: Int,
        val hostFingerprintSha256: String,
        val hostAlgorithm: String,
        val clientKeyFingerprintSha256: String,
        val firstTimeTrusted: Boolean
    ) : SshDiagnosticResult()

    data class Failure(val error: SshConnectionError) : SshDiagnosticResult()
}

data class SshExecOutput(
    val output: String,
    val exitCode: Int,
    val currentDir: String
)

class ActiveSshShellSession(
    private val session: Session,
    private val channel: ChannelShell,
    private val outputStream: OutputStream,
    private val readerScope: CoroutineScope,
    val hostFingerprintSha256: String,
    val firstTimeTrusted: Boolean
) {
    private val writeLock = Any()

    fun isConnected(): Boolean = session.isConnected && channel.isConnected

    fun sendBytes(data: ByteArray) {
        if (!isConnected() || data.isEmpty()) return
        readerScope.launch(Dispatchers.IO) {
            try {
                synchronized(writeLock) {
                    outputStream.write(data)
                    outputStream.flush()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun resizePty(cols: Int, rows: Int) {
        if (!isConnected()) return
        val safeCols = cols.coerceIn(20, 400)
        val safeRows = rows.coerceIn(5, 200)
        try {
            channel.setPtySize(safeCols, safeRows, safeCols * 16, safeRows * 32)
        } catch (_: Exception) {
        }
    }

    suspend fun executeExecCommand(
        command: String,
        currentDir: String = "~",
        timeoutMs: Int = 15_000
    ): Result<SshExecOutput> = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext Result.failure(IllegalStateException("La sesión SSH con Termux no está conectada."))
        }
        var execChannel: ChannelExec? = null
        val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        val cancelHandle = job?.invokeOnCompletion {
            try {
                execChannel?.disconnect()
            } catch (_: Exception) {
            }
        }
        try {
            execChannel = session.openChannel("exec") as ChannelExec
            val marker = "__TERMUX_IA_PWD__"
            val cdPrefix = if (currentDir.isBlank() || currentDir == "~") {
                "cd \"\$HOME\""
            } else {
                "cd -- '${currentDir.replace("'", "'\"'\"'")}' 2>/dev/null || cd \"\$HOME\""
            }
            val script = "$cdPrefix\n$command\n__ec=\$?\nprintf '\\n${marker}:%s\\n' \"\$PWD\"\nexit \$__ec"
            execChannel.setCommand(script)
            execChannel.setInputStream(null)

            val stdout = execChannel.inputStream
            val stderr = execChannel.errStream
            execChannel.connect(TermuxSshClient.CONNECT_TIMEOUT_MS)

            val outBuf = ByteArrayOutputStream()
            val errBuf = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            val deadline = System.currentTimeMillis() + timeoutMs

            while (true) {
                if (job?.isCancelled == true) {
                    throw kotlinx.coroutines.CancellationException("Ejecución SSH cancelada por el usuario.")
                }
                while (stdout.available() > 0) {
                    val r = stdout.read(buf)
                    if (r <= 0) break
                    outBuf.write(buf, 0, r)
                }
                while (stderr.available() > 0) {
                    val r = stderr.read(buf)
                    if (r <= 0) break
                    errBuf.write(buf, 0, r)
                }
                if (execChannel.isClosed && stdout.available() == 0 && stderr.available() == 0) {
                    break
                }
                if (System.currentTimeMillis() > deadline) {
                    return@withContext Result.failure(SocketTimeoutException("Tiempo de espera agotado al ejecutar comando en Termux SSH."))
                }
                Thread.sleep(25)
            }

            val rawOut = outBuf.toString(Charsets.UTF_8.name())
            val rawErr = errBuf.toString(Charsets.UTF_8.name()).trimEnd()
            var resolvedDir = currentDir
            val filteredOutLines = mutableListOf<String>()
            for (line in rawOut.lines()) {
                if (line.startsWith("$marker:")) {
                    val candidate = line.removePrefix("$marker:").trim()
                    if (candidate.isNotEmpty()) resolvedDir = candidate
                } else {
                    filteredOutLines.add(line)
                }
            }

            val combined = buildString {
                val cleanOut = filteredOutLines.joinToString("\n").trimEnd()
                if (cleanOut.isNotEmpty()) append(cleanOut)
                if (rawErr.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(rawErr)
                }
            }

            Result.success(
                SshExecOutput(
                    output = combined,
                    exitCode = execChannel.exitStatus,
                    currentDir = resolvedDir
                )
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            cancelHandle?.dispose()
            try {
                execChannel?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    fun disconnect() {
        try {
            readerScope.cancel()
        } catch (_: Exception) {
        }
        try {
            if (channel.isConnected) channel.disconnect()
        } catch (_: Exception) {
        }
        try {
            if (session.isConnected) session.disconnect()
        } catch (_: Exception) {
        }
    }
}

/**
 * Pure SSH2 client connecting to Termux sshd on 127.0.0.1 using key-only Ed25519 authentication
 * and strict TOFU (Trust-On-First-Use) host key verification.
 */
class TermuxSshClient(
    private val context: Context,
    private val keyManager: SshKeyManager,
    private val isTermuxInstalledProvider: () -> Boolean = { TermuxSetupHelper.isTermuxInstalled(context) }
) {
    companion object {
        const val LOCALHOST = "127.0.0.1"
        const val CONNECT_TIMEOUT_MS = 6000

        fun classifySshException(
            t: Throwable,
            host: String = LOCALHOST,
            port: Int = TermuxSetupHelper.DEFAULT_SSH_PORT
        ): SshConnectionError {
            val chain = generateSequence(t) { it.cause }.toList()
            val messages = chain.mapNotNull { it.message }.joinToString(" | ").lowercase()

            if (messages.contains("hostkey has been changed") ||
                messages.contains("remote host identification has changed")
            ) {
                return SshConnectionError.OtherError("La clave de host SSH cambió.")
            }

            if (chain.any { it is ConnectException || it is NoRouteToHostException } ||
                messages.contains("connection refused") ||
                messages.contains("econnrefused")
            ) {
                return SshConnectionError.SshdNotRunning(host, port, t.message ?: "Connection refused")
            }

            if (messages.contains("auth fail") ||
                messages.contains("auth cancel") ||
                messages.contains("userauth") ||
                messages.contains("exhausted") ||
                messages.contains("no more authentication methods")
            ) {
                return SshConnectionError.KeyNotAuthorized(t.message ?: "Authentication failed")
            }

            if (chain.any { it is SocketTimeoutException || it is UnknownHostException } ||
                messages.contains("timed out") ||
                messages.contains("timeout")
            ) {
                return SshConnectionError.WrongPortOrTimeout(host, port, t.message ?: "Timeout")
            }

            if (t is JSchException && messages.contains("reject hostkey")) {
                return SshConnectionError.OtherError("Host key rechazada por seguridad.")
            }

            return SshConnectionError.OtherError(t.message ?: t.javaClass.simpleName)
        }
    }

    suspend fun testConnection(
        port: Int = TermuxSetupHelper.DEFAULT_SSH_PORT,
        username: String = "termux"
    ): SshDiagnosticResult = withContext(Dispatchers.IO) {
        if (!isTermuxInstalledProvider()) {
            return@withContext SshDiagnosticResult.Failure(SshConnectionError.TermuxNotInstalled)
        }
        if (port !in 1024..65535) {
            return@withContext SshDiagnosticResult.Failure(SshConnectionError.InvalidPort(port))
        }

        val probe = establishAuthenticatedSession(LOCALHOST, port, username)
        return@withContext when (probe) {
            is SessionAttempt.Established -> {
                val fp = probe.hostFingerprint
                val alg = probe.hostAlgorithm
                val first = probe.firstTimeTrusted
                try {
                    probe.session.disconnect()
                } catch (_: Exception) {
                }
                SshDiagnosticResult.Success(
                    host = LOCALHOST,
                    port = port,
                    hostFingerprintSha256 = fp,
                    hostAlgorithm = alg,
                    clientKeyFingerprintSha256 = keyManager.getPublicKeyFingerprintSha256(),
                    firstTimeTrusted = first
                )
            }
            is SessionAttempt.Failed -> SshDiagnosticResult.Failure(probe.error)
        }
    }

    suspend fun openShellSession(
        port: Int = TermuxSetupHelper.DEFAULT_SSH_PORT,
        username: String = "termux",
        initialCols: Int = 80,
        initialRows: Int = 24,
        onOutputBytes: (ByteArray, Int) -> Unit,
        onSessionClosed: (reason: String?) -> Unit
    ): Result<ActiveSshShellSession> = withContext(Dispatchers.IO) {
        if (!isTermuxInstalledProvider()) {
            return@withContext Result.failure(SshDiagnosticException(SshConnectionError.TermuxNotInstalled))
        }
        if (port !in 1024..65535) {
            return@withContext Result.failure(SshDiagnosticException(SshConnectionError.InvalidPort(port)))
        }

        when (val attempt = establishAuthenticatedSession(LOCALHOST, port, username)) {
            is SessionAttempt.Failed -> {
                Result.failure(SshDiagnosticException(attempt.error))
            }
            is SessionAttempt.Established -> {
                try {
                    val channel = attempt.session.openChannel("shell") as ChannelShell
                    val safeCols = initialCols.coerceIn(20, 400)
                    val safeRows = initialRows.coerceIn(5, 200)
                    channel.setPty(true)
                    channel.setPtyType("xterm-256color", safeCols, safeRows, safeCols * 16, safeRows * 32)
                    val inputStream: InputStream = channel.inputStream
                    val outputStream: OutputStream = channel.outputStream
                    channel.connect(CONNECT_TIMEOUT_MS)

                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    val active = ActiveSshShellSession(
                        session = attempt.session,
                        channel = channel,
                        outputStream = outputStream,
                        readerScope = scope,
                        hostFingerprintSha256 = attempt.hostFingerprint,
                        firstTimeTrusted = attempt.firstTimeTrusted
                    )

                    scope.launch {
                        val buffer = ByteArray(8192)
                        var closeReason: String? = null
                        try {
                            while (isActive && channel.isConnected) {
                                val read = inputStream.read(buffer)
                                if (read == -1) break
                                if (read > 0) {
                                    onOutputBytes(buffer.copyOf(read), read)
                                }
                            }
                        } catch (e: Exception) {
                            closeReason = e.localizedMessage
                        } finally {
                            active.disconnect()
                            onSessionClosed(closeReason)
                        }
                    }

                    Result.success(active)
                } catch (e: Exception) {
                    try {
                        attempt.session.disconnect()
                    } catch (_: Exception) {
                    }
                    Result.failure(SshDiagnosticException(classifyThrowable(e, LOCALHOST, port, null)))
                }
            }
        }
    }

    private sealed class SessionAttempt {
        data class Established(
            val session: Session,
            val hostFingerprint: String,
            val hostAlgorithm: String,
            val firstTimeTrusted: Boolean
        ) : SessionAttempt()

        data class Failed(val error: SshConnectionError) : SessionAttempt()
    }

    private fun establishAuthenticatedSession(
        host: String,
        port: Int,
        username: String
    ): SessionAttempt {
        var privBytes: ByteArray? = null
        var mismatchCaptured: MismatchCapture? = null
        var verifiedFp = ""
        var verifiedAlg = ""
        var firstTimeTrusted = false

        return try {
            val jsch = JSch()
            privBytes = keyManager.loadPrivateKeyBytesForSsh()
            jsch.addIdentity("terminal-ia-ed25519", privBytes, null, null)

            jsch.hostKeyRepository = object : HostKeyRepository {
                override fun check(hostName: String?, key: ByteArray?): Int {
                    if (key == null) return HostKeyRepository.NOT_INCLUDED
                    val alg = extractKeyAlgorithm(key)
                    return when (val res = keyManager.verifyOrTrustHostKey(host, port, alg, key)) {
                        is HostKeyCheckResult.TrustedFirstUse -> {
                            verifiedFp = res.fingerprintSha256
                            verifiedAlg = res.algorithm
                            firstTimeTrusted = true
                            HostKeyRepository.OK
                        }
                        is HostKeyCheckResult.Matched -> {
                            verifiedFp = res.fingerprintSha256
                            verifiedAlg = res.algorithm
                            firstTimeTrusted = false
                            HostKeyRepository.OK
                        }
                        is HostKeyCheckResult.Mismatch -> {
                            mismatchCaptured = MismatchCapture(
                                expectedFingerprint = res.expectedFingerprint,
                                actualFingerprint = res.actualFingerprint,
                                algorithm = res.algorithm,
                                keyBytes = key.copyOf()
                            )
                            HostKeyRepository.CHANGED
                        }
                    }
                }

                override fun add(hostkey: HostKey?, ui: UserInfo?) {}
                override fun remove(hostName: String?, type: String?) {}
                override fun remove(hostName: String?, type: String?, key: ByteArray?) {}
                override fun getKnownHostsRepositoryID(): String = "terminal-ia-tofu"
                override fun getHostKey(): Array<HostKey> = emptyArray()
                override fun getHostKey(hostName: String?, type: String?): Array<HostKey> = emptyArray()
            }

            val cleanUser = username.trim().ifBlank { "termux" }
            val session = jsch.getSession(cleanUser, host, port)
            session.setConfig("StrictHostKeyChecking", "yes")
            session.setConfig("PreferredAuthentications", "publickey")
            session.setConfig("PubkeyAuthentication", "yes")
            session.setConfig("PasswordAuthentication", "no")
            session.setConfig("KbdInteractiveAuthentication", "no")
            session.timeout = CONNECT_TIMEOUT_MS
            session.connect(CONNECT_TIMEOUT_MS)

            SessionAttempt.Established(
                session = session,
                hostFingerprint = verifiedFp,
                hostAlgorithm = verifiedAlg,
                firstTimeTrusted = firstTimeTrusted
            )
        } catch (t: Throwable) {
            SessionAttempt.Failed(classifyThrowable(t, host, port, mismatchCaptured))
        } finally {
            privBytes?.fill(0)
        }
    }

    private data class MismatchCapture(
        val expectedFingerprint: String,
        val actualFingerprint: String,
        val algorithm: String,
        val keyBytes: ByteArray
    )

    private fun extractKeyAlgorithm(wireBytes: ByteArray): String {
        if (wireBytes.size < 4) return "ssh-unknown"
        val len = ((wireBytes[0].toInt() and 0xFF) shl 24) or
            ((wireBytes[1].toInt() and 0xFF) shl 16) or
            ((wireBytes[2].toInt() and 0xFF) shl 8) or
            (wireBytes[3].toInt() and 0xFF)
        if (len <= 0 || len > 64 || 4 + len > wireBytes.size) return "ssh-unknown"
        return String(wireBytes, 4, len, Charsets.US_ASCII)
    }

    private fun classifyThrowable(
        t: Throwable,
        host: String,
        port: Int,
        mismatch: MismatchCapture?
    ): SshConnectionError {
        if (mismatch != null) {
            return SshConnectionError.HostKeyChanged(
                host = host,
                port = port,
                expectedFingerprint = mismatch.expectedFingerprint,
                actualFingerprint = mismatch.actualFingerprint,
                algorithm = mismatch.algorithm,
                hostKeyBytes = mismatch.keyBytes
            )
        }
        return classifySshException(t, host, port)
    }
}

class SshDiagnosticException(val connectionError: SshConnectionError) : Exception(connectionError.toUserMessage())
