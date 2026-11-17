package com.example.server

import android.content.Context
import com.example.data.TerminalPreferences
import com.example.model.LineType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

data class WebRealExecResult(
    val success: Boolean,
    val output: String,
    val currentDir: String,
    val error: String? = null,
    val realModeConnected: Boolean = true
)

interface RealTerminalExecutor {
    fun isRealModeConnected(): Boolean
    suspend fun executeCommand(command: String, currentDir: String): WebRealExecResult
}

class WebTerminalServer @JvmOverloads constructor(
    private val context: Context,
    private val preferences: TerminalPreferences,
    private val realExecutor: RealTerminalExecutor,
    private val onLogMessage: (String, LineType) -> Unit,
    private val sessionTtlMs: Long = DEFAULT_SESSION_TTL_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secureRandom = SecureRandom()

    private val activeSessions = ConcurrentHashMap<String, SessionInfo>()
    private var tlsCertFingerprintSha256: String = ""

    data class SessionInfo(
        val username: String,
        val createdAt: Long,
        var lastAccessedAt: Long,
        var currentDir: String = "~"
    )

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var port: Int = 8080
        private set

    @Volatile
    var isLanEnabled: Boolean = false
        private set

    @Volatile
    var isHttpsActive: Boolean = false
        private set

    companion object {
        const val DEFAULT_SESSION_TTL_MS = 30 * 60 * 1000L // 30 minutes
        private const val MAX_REQUEST_BODY_BYTES = 64 * 1024 // 64 KB

        fun escapeHtml(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    '"' -> sb.append("&quot;")
                    '\'' -> sb.append("&#39;")
                    '/' -> sb.append("&#x2F;")
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun toSafeJsStringLiteral(input: String): String {
            return JSONObject.quote(input)
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("/", "\\/")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029")
        }
    }

    fun getBoundHostAddress(): String? = serverSocket?.inetAddress?.hostAddress

    fun getLocalPort(): Int = serverSocket?.localPort ?: port

    fun getTlsCertificateFingerprintSha256(): String = tlsCertFingerprintSha256

    @JvmOverloads
    fun start(
        webPort: Int = 8080,
        allowLan: Boolean = preferences.isWebLanAccessEnabled(),
        useHttps: Boolean = preferences.isWebHttpsEnabled()
    ) {
        if (serverJob?.isActive == true) return
        launchServer(webPort, allowLan, useHttps, startedSignal = null)
    }

    suspend fun startAndAwait(
        webPort: Int = 8080,
        allowLan: Boolean = preferences.isWebLanAccessEnabled(),
        useHttps: Boolean = preferences.isWebHttpsEnabled()
    ): Boolean {
        if (serverJob?.isActive == true && isRunning) return true
        val signal = CompletableDeferred<Boolean>()
        launchServer(webPort, allowLan, useHttps, startedSignal = signal)
        return signal.await()
    }

    private fun launchServer(
        webPort: Int,
        allowLan: Boolean,
        useHttps: Boolean,
        startedSignal: CompletableDeferred<Boolean>?
    ) {
        this.isLanEnabled = allowLan
        this.isHttpsActive = useHttps

        serverJob = scope.launch {
            try {
                val bindAddress = if (allowLan) {
                    InetAddress.getByName("0.0.0.0")
                } else {
                    InetAddress.getByName("127.0.0.1")
                }

                val createdSocket = createBoundSocket(webPort, bindAddress, useHttps)
                serverSocket = createdSocket
                this@WebTerminalServer.port = createdSocket.localPort
                isRunning = true
                startedSignal?.complete(true)

                val scheme = if (useHttps) "HTTPS" else "HTTP"
                val scopeDesc = if (allowLan) "0.0.0.0 (red local LAN)" else "127.0.0.1 (solo este dispositivo)"
                onLogMessage(
                    "[Servidor Web Terminal iniciado en $scheme $scopeDesc:${createdSocket.localPort}]",
                    LineType.SYSTEM
                )

                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    scope.launch {
                        handleHttpRequest(client)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (startedSignal != null && !startedSignal.isCompleted) {
                    startedSignal.complete(false)
                }
                throw e
            } catch (t: Throwable) {
                if (startedSignal != null && !startedSignal.isCompleted) {
                    startedSignal.complete(false)
                }
                if (isActive) {
                    isRunning = false
                    onLogMessage("Error en servidor Web Terminal: ${t.localizedMessage ?: t.javaClass.simpleName}", LineType.ERROR)
                }
            } finally {
                isRunning = false
            }
        }
    }

    private fun createBoundSocket(webPort: Int, bindAddress: InetAddress, useHttps: Boolean): ServerSocket {
        if (!useHttps) {
            tlsCertFingerprintSha256 = ""
            return ServerSocket(webPort, 50, bindAddress)
        }

        val (sslContext, certFingerprint) = buildSelfSignedTlsContext()
        tlsCertFingerprintSha256 = certFingerprint
        val sslSocket = sslContext.serverSocketFactory.createServerSocket(webPort, 50, bindAddress) as SSLServerSocket
        val preferredProtocols = sslSocket.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }
        if (preferredProtocols.isNotEmpty()) {
            sslSocket.enabledProtocols = preferredProtocols.toTypedArray()
        }
        return sslSocket
    }

    private fun buildSelfSignedTlsContext(): Pair<SSLContext, String> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, secureRandom)
        val keyPair = kpg.generateKeyPair()

        val now = timeProvider()
        val subject = X500Name("CN=Terminal-IA Local Web Console")
        val tbsGen = V3TBSCertificateGenerator()
        tbsGen.setSerialNumber(ASN1Integer(BigInteger(64, secureRandom).abs().max(BigInteger.ONE)))
        val sigAlgId = AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE)
        tbsGen.setSignature(sigAlgId)
        tbsGen.setIssuer(subject)
        tbsGen.setStartDate(Time(Date(now - 60_000L)))
        tbsGen.setEndDate(Time(Date(now + 365L * 24 * 60 * 60 * 1000L)))
        tbsGen.setSubject(subject)
        tbsGen.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(keyPair.public.encoded))

        val extGen = ExtensionsGenerator()
        val san = GeneralNames(
            arrayOf(
                GeneralName(GeneralName.dNSName, "localhost"),
                GeneralName(GeneralName.iPAddress, "127.0.0.1")
            )
        )
        extGen.addExtension(Extension.subjectAlternativeName, false, san)
        tbsGen.setExtensions(extGen.generate())

        val tbsCert = tbsGen.generateTBSCertificate()
        val tbsBytes = tbsCert.getEncoded(ASN1Encoding.DER)

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private, secureRandom)
        signer.update(tbsBytes)
        val signatureBytes = signer.sign()

        val certVec = ASN1EncodableVector().apply {
            add(tbsCert)
            add(sigAlgId)
            add(DERBitString(signatureBytes))
        }
        val certDer = DERSequence(certVec).getEncoded(ASN1Encoding.DER)
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate

        val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        val fingerprint = "SHA256:" + sha256.joinToString(":") { "%02X".format(it) }

        val keyStore = KeyStore.getInstance("PKCS12")
        val ksPassword = CharArray(16) { 'a' + it }
        keyStore.load(null, ksPassword)
        keyStore.setKeyEntry("terminal-ia-web", keyPair.private, ksPassword, arrayOf(cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, ksPassword)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, secureRandom)
        return Pair(sslContext, fingerprint)
    }

    fun stop() {
        try {
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            serverJob = null
            isRunning = false
            activeSessions.clear()
            onLogMessage("[Servidor Web Terminal detenido]", LineType.SYSTEM)
        } catch (e: Exception) {
            onLogMessage("Error al detener servidor Web: ${e.localizedMessage}", LineType.ERROR)
        }
    }

    private suspend fun handleHttpRequest(socket: Socket) = withContext(Dispatchers.IO) {
        val clientIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"

        try {
            socket.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val out = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase()
            val pathWithQuery = parts[1]
            val path = pathWithQuery.substringBefore("?")

            val headers = mutableMapOf<String, String>()
            var contentLength = 0
            var cookieHeader = ""

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val colonIdx = line!!.indexOf(":")
                if (colonIdx > 0) {
                    val key = line!!.substring(0, colonIdx).trim().lowercase()
                    val value = line!!.substring(colonIdx + 1).trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    }
                    if (key == "cookie") {
                        cookieHeader = value
                    }
                }
            }

            if (contentLength > MAX_REQUEST_BODY_BYTES) {
                sendJsonResponse(out, 413, "{\"success\": false, \"error\": \"Cuerpo de petición demasiado grande.\"}")
                return@withContext
            }

            // Strict Origin / Sec-Fetch-Site validation (blocks cross-origin & CORS abuse)
            if (!isOriginAllowed(headers["origin"], headers["host"], headers["sec-fetch-site"])) {
                sendJsonResponse(
                    out,
                    403,
                    "{\"success\": false, \"error\": \"Origin HTTP rechazado por política de mismo origen.\"}"
                )
                return@withContext
            }

            // Read request body
            val bodyBuilder = StringBuilder()
            if (contentLength > 0) {
                val buffer = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(buffer, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                bodyBuilder.append(buffer, 0, totalRead)
            }
            val requestBody = bodyBuilder.toString()

            // Extract and validate session token (with expiration check)
            val sessionToken = extractSessionToken(cookieHeader)
            val sessionResolution = resolveSession(sessionToken)
            val session = sessionResolution.validSession

            when {
                // 1. API: Login (never creates credentials over network)
                method == "POST" && path == "/api/login" -> {
                    handleApiLogin(requestBody, clientIp, out)
                }

                // 2. API: Logout
                method == "POST" && path == "/api/logout" -> {
                    if (sessionToken != null) activeSessions.remove(sessionToken)
                    sendJsonResponse(
                        out = out,
                        code = 200,
                        json = "{\"success\": true}",
                        setCookie = buildClearedSessionCookie()
                    )
                }

                // 3. API: Session Status Check
                method == "GET" && path == "/api/session" -> {
                    if (session != null) {
                        val json = JSONObject().apply {
                            put("authenticated", true)
                            put("username", session.username)
                            put("currentDir", session.currentDir)
                            put("realModeConnected", realExecutor.isRealModeConnected())
                        }
                        sendJsonResponse(out, 200, json.toString())
                    } else if (sessionResolution.hadExpiredOrInvalidCookie) {
                        val json = JSONObject().apply {
                            put("authenticated", false)
                            put("expired", true)
                            put("hasCredentials", preferences.hasRemoteCredentials())
                            put("realModeConnected", realExecutor.isRealModeConnected())
                            put("error", "Sesión expirada o inválida.")
                        }
                        sendJsonResponse(
                            out = out,
                            code = 401,
                            json = json.toString(),
                            setCookie = buildClearedSessionCookie()
                        )
                    } else {
                        val json = JSONObject().apply {
                            put("authenticated", false)
                            put("hasCredentials", preferences.hasRemoteCredentials())
                            put("realModeConnected", realExecutor.isRealModeConnected())
                        }
                        sendJsonResponse(out, 200, json.toString())
                    }
                }

                // 4. API: Execute Command (Real Termux SSH mode ONLY)
                method == "POST" && path == "/api/exec" -> {
                    if (session == null) {
                        val clearCookie = if (sessionResolution.hadExpiredOrInvalidCookie) buildClearedSessionCookie() else null
                        sendJsonResponse(
                            out = out,
                            code = 401,
                            json = "{\"success\": false, \"error\": \"No autorizado o sesión expirada. Inicia sesión.\", \"authenticated\": false}",
                            setCookie = clearCookie
                        )
                        return@withContext
                    }
                    handleApiExec(session, requestBody, out)
                }

                // 5. Web UI: Terminal or Login (all interpolated values strictly escaped)
                method == "GET" && (path == "/" || path == "/index.html" || path == "/terminal") -> {
                    if (session != null) {
                        sendHtmlResponse(
                            out = out,
                            code = 200,
                            html = getWebTerminalHtml(
                                username = session.username,
                                currentDir = session.currentDir,
                                realModeConnected = realExecutor.isRealModeConnected()
                            )
                        )
                    } else {
                        val clearCookie = if (sessionResolution.hadExpiredOrInvalidCookie) buildClearedSessionCookie() else null
                        sendHtmlResponse(
                            out = out,
                            code = 200,
                            html = getWebLoginHtml(preferences.hasRemoteCredentials()),
                            setCookie = clearCookie
                        )
                    }
                }

                else -> {
                    sendHttpResponse(out, 404, "text/plain; charset=UTF-8", "404 Not Found")
                }
            }
        } catch (e: javax.net.ssl.SSLException) {
            onLogMessage("Error TLS Web: ${e.message}", LineType.ERROR)
        } catch (_: Exception) {
            // Socket closed or connection reset
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun isOriginAllowed(
        originHeader: String?,
        hostHeader: String?,
        secFetchSite: String?
    ): Boolean {
        if (secFetchSite != null && secFetchSite.equals("cross-site", ignoreCase = true)) {
            return false
        }
        val rawOrigin = originHeader?.trim() ?: return true
        if (rawOrigin.isEmpty() || rawOrigin.equals("null", ignoreCase = true)) {
            return false
        }

        val originUri = try {
            URI(rawOrigin)
        } catch (_: Exception) {
            return false
        }

        val originScheme = originUri.scheme?.lowercase() ?: return false
        val expectedScheme = if (isHttpsActive) "https" else "http"
        if (originScheme != expectedScheme) {
            return false
        }

        val originHost = originUri.host?.lowercase()?.trim('[', ']') ?: return false
        val originPort = if (originUri.port != -1) {
            originUri.port
        } else {
            if (originScheme == "https") 443 else 80
        }

        val listeningPort = getLocalPort()
        if (originPort != listeningPort) {
            return false
        }

        if (!isLanEnabled && originHost !in setOf("127.0.0.1", "localhost", "::1")) {
            return false
        }

        val rawHostHeader = hostHeader?.trim()
        if (!rawHostHeader.isNullOrEmpty()) {
            val (hdrHost, hdrPort) = parseHostHeader(rawHostHeader) ?: return false
            if (!originHost.equals(hdrHost, ignoreCase = true)) {
                return false
            }
            if (hdrPort != null && hdrPort != originPort) {
                return false
            }
        }

        return true
    }

    private fun parseHostHeader(hostHeader: String): Pair<String, Int?>? {
        val clean = hostHeader.trim()
        if (clean.isEmpty()) return null
        if (clean.startsWith("[")) {
            val closeIdx = clean.indexOf(']')
            if (closeIdx <= 1) return null
            val ipv6 = clean.substring(1, closeIdx).lowercase()
            val rest = clean.substring(closeIdx + 1)
            val portVal = if (rest.startsWith(":")) rest.substring(1).toIntOrNull() ?: return null else null
            return Pair(ipv6, portVal)
        }
        val colonIdx = clean.lastIndexOf(':')
        return if (colonIdx > 0) {
            val h = clean.substring(0, colonIdx).lowercase()
            val p = clean.substring(colonIdx + 1).toIntOrNull() ?: return null
            Pair(h, p)
        } else {
            Pair(clean.lowercase(), null)
        }
    }

    private data class SessionResolution(
        val validSession: SessionInfo?,
        val hadExpiredOrInvalidCookie: Boolean
    )

    private fun resolveSession(token: String?): SessionResolution {
        if (token.isNullOrBlank()) {
            return SessionResolution(validSession = null, hadExpiredOrInvalidCookie = false)
        }
        val info = activeSessions[token]
            ?: return SessionResolution(validSession = null, hadExpiredOrInvalidCookie = true)

        val now = timeProvider()
        val expiredByTotalAge = (now - info.createdAt) >= sessionTtlMs
        val expiredByIdle = (now - info.lastAccessedAt) >= sessionTtlMs
        if (expiredByTotalAge || expiredByIdle) {
            activeSessions.remove(token)
            return SessionResolution(validSession = null, hadExpiredOrInvalidCookie = true)
        }

        info.lastAccessedAt = now
        return SessionResolution(validSession = info, hadExpiredOrInvalidCookie = false)
    }

    private fun extractSessionToken(cookieHeader: String): String? {
        val cookies = cookieHeader.split(";").map { it.trim() }
        for (c in cookies) {
            if (c.startsWith("termux_session=")) {
                val value = c.removePrefix("termux_session=").trim()
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }

    private fun buildSessionCookie(token: String): String {
        val secureAttr = if (isHttpsActive) "; Secure" else ""
        return "termux_session=$token; Path=/; HttpOnly; SameSite=Strict$secureAttr"
    }

    private fun buildClearedSessionCookie(): String {
        val secureAttr = if (isHttpsActive) "; Secure" else ""
        return "termux_session=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict$secureAttr"
    }

    private suspend fun handleApiLogin(body: String, clientIp: String, out: OutputStream) {
        val security = preferences.getSecurityManager()

        if (security.isIpLockedOut(clientIp)) {
            val remain = security.getRemainingLockoutSeconds(clientIp)
            sendJsonResponse(
                out,
                403,
                "{\"success\": false, \"lockedOut\": true, \"error\": \"Demasiados intentos fallidos. Espera $remain segundos.\"}"
            )
            return
        }

        if (!preferences.hasRemoteCredentials()) {
            sendJsonResponse(
                out,
                403,
                "{\"success\": false, \"error\": \"Credenciales no configuradas. Créalas dentro de la app Terminal-IA (Acceso Remoto); no se permite el alta desde la red.\"}"
            )
            return
        }

        try {
            val json = JSONObject(body)
            val username = json.optString("username", "").trim()
            val password = json.optString("password", "")

            if (preferences.verifyRemoteCredentials(username, password)) {
                security.resetFailedAttempts(clientIp)
                val token = generateToken()
                val now = timeProvider()
                activeSessions[token] = SessionInfo(
                    username = username,
                    createdAt = now,
                    lastAccessedAt = now,
                    currentDir = "~"
                )

                val resJson = JSONObject().apply {
                    put("success", true)
                    put("username", username)
                    put("realModeConnected", realExecutor.isRealModeConnected())
                }
                sendJsonResponse(out, 200, resJson.toString(), setCookie = buildSessionCookie(token))
                onLogMessage("[Usuario '$username' autenticado vía Web Terminal desde $clientIp]", LineType.SYSTEM)
            } else {
                val attempts = security.recordFailedAttempt(clientIp)
                onLogMessage("[Fallo de inicio de sesión Web desde $clientIp ($attempts intentos)]", LineType.ERROR)
                sendJsonResponse(out, 401, "{\"success\": false, \"error\": \"Usuario o contraseña incorrectos.\"}")
            }
        } catch (_: Exception) {
            sendJsonResponse(out, 400, "{\"success\": false, \"error\": \"Formato JSON inválido.\"}")
        }
    }

    private suspend fun handleApiExec(session: SessionInfo, body: String, out: OutputStream) {
        try {
            val json = JSONObject(body)
            val command = json.optString("command", "").trim()

            if (!realExecutor.isRealModeConnected()) {
                val res = JSONObject().apply {
                    put("success", false)
                    put("realModeConnected", false)
                    put("currentDir", session.currentDir)
                    put(
                        "error",
                        "Modo Real (Termux SSH) desconectado. Conecta Termux desde la app para ejecutar comandos reales; el modo práctica simulado no está disponible en la terminal web."
                    )
                }
                sendJsonResponse(out, 409, res.toString())
                return
            }

            if (command.isEmpty()) {
                val res = JSONObject().apply {
                    put("success", true)
                    put("output", "")
                    put("currentDir", session.currentDir)
                    put("realModeConnected", true)
                }
                sendJsonResponse(out, 200, res.toString())
                return
            }

            val execResult = realExecutor.executeCommand(command, session.currentDir)
            session.currentDir = execResult.currentDir

            if (!execResult.realModeConnected) {
                val res = JSONObject().apply {
                    put("success", false)
                    put("realModeConnected", false)
                    put("currentDir", session.currentDir)
                    put(
                        "error",
                        execResult.error ?: "Modo Real (Termux SSH) desconectado."
                    )
                }
                sendJsonResponse(out, 409, res.toString())
                return
            }

            val res = JSONObject().apply {
                put("success", execResult.success)
                put("output", execResult.output)
                put("currentDir", execResult.currentDir)
                put("command", command)
                put("realModeConnected", true)
                if (!execResult.error.isNullOrBlank()) {
                    put("error", execResult.error)
                }
            }
            sendJsonResponse(out, 200, res.toString())
        } catch (e: Exception) {
            val errJson = JSONObject().apply {
                put("success", false)
                put("error", e.localizedMessage ?: "Error ejecutando comando SSH.")
            }
            sendJsonResponse(out, 500, errJson.toString())
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun statusReasonPhrase(code: Int): String = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        413 -> "Payload Too Large"
        500 -> "Internal Server Error"
        503 -> "Service Unavailable"
        else -> "Error"
    }

    private fun sendJsonResponse(out: OutputStream, code: Int, json: String, setCookie: String? = null) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out, false, Charsets.UTF_8)
        writer.print("HTTP/1.1 $code ${statusReasonPhrase(code)}\r\n")
        writer.print("Content-Type: application/json; charset=UTF-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Cache-Control: no-store\r\n")
        writer.print("Vary: Origin\r\n")
        writer.print("X-Content-Type-Options: nosniff\r\n")
        writer.print("X-Frame-Options: DENY\r\n")
        writer.print("Referrer-Policy: no-referrer\r\n")
        if (setCookie != null) {
            writer.print("Set-Cookie: $setCookie\r\n")
        }
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    private fun sendHtmlResponse(out: OutputStream, code: Int, html: String, setCookie: String? = null) {
        sendHttpResponse(out, code, "text/html; charset=UTF-8", html, setCookie)
    }

    private fun sendHttpResponse(
        out: OutputStream,
        code: Int,
        contentType: String,
        content: String,
        setCookie: String? = null
    ) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out, false, Charsets.UTF_8)
        writer.print("HTTP/1.1 $code ${statusReasonPhrase(code)}\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Cache-Control: no-store\r\n")
        writer.print("Vary: Origin\r\n")
        writer.print("X-Content-Type-Options: nosniff\r\n")
        writer.print("X-Frame-Options: DENY\r\n")
        writer.print("Referrer-Policy: no-referrer\r\n")
        writer.print("Content-Security-Policy: default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'\r\n")
        if (setCookie != null) {
            writer.print("Set-Cookie: $setCookie\r\n")
        }
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    // --- HTML TEMPLATES (STRICT ESCAPING, APP-ONLY CREDENTIALS, REAL MODE ONLY) ---

    private fun getWebLoginHtml(hasCredentials: Boolean): String {
        val statusBadgeText = if (hasCredentials) {
            "Seguridad: PBKDF2-SHA256 activo (solo inicio de sesión)"
        } else {
            "Acceso bloqueado: configura usuario y clave de 12+ caracteres en la app Android"
        }

        val formHtml = if (hasCredentials) {
            """
            <form id="loginForm">
                <div class="field">
                    <label for="username">Usuario:</label>
                    <input type="text" id="username" value="" autocomplete="username" required autofocus>
                </div>
                <div class="field">
                    <label for="password">Contraseña (mínimo 12 caracteres):</label>
                    <input type="password" id="password" autocomplete="current-password" required>
                </div>
                <button type="submit">Iniciar Sesión</button>
                <div id="errorMsg" class="error"></div>
            </form>
            """.trimIndent()
        } else {
            """
            <div class="notice">
                No existen credenciales web configuradas.<br><br>
                Por seguridad, las credenciales se crean únicamente desde la app <strong>Terminal-IA</strong> en <em>Acceso Remoto</em> (mínimo 12 caracteres).
            </div>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Terminal-IA - Acceso Web</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    background: #0A0F0D;
                    color: #00FF66;
                    font-family: 'Courier New', Courier, monospace;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    min-height: 100vh;
                    padding: 16px;
                }
                .card {
                    background: #111A16;
                    border: 1px solid #00FF66;
                    border-radius: 8px;
                    width: 100%;
                    max-width: 440px;
                    padding: 24px;
                    box-shadow: 0 0 20px rgba(0, 255, 102, 0.15);
                }
                .title {
                    font-size: 1.35rem;
                    font-weight: bold;
                    text-align: center;
                    margin-bottom: 8px;
                    color: #00FF66;
                }
                .subtitle {
                    font-size: 0.85rem;
                    text-align: center;
                    color: #80FFD4;
                    margin-bottom: 18px;
                }
                .field { margin-bottom: 14px; }
                label {
                    display: block;
                    font-size: 0.8rem;
                    margin-bottom: 6px;
                    color: #80FFD4;
                }
                input {
                    width: 100%;
                    padding: 10px 12px;
                    background: #0A0F0D;
                    border: 1px solid #33FFAA;
                    border-radius: 4px;
                    color: #00FF66;
                    font-family: monospace;
                    font-size: 0.95rem;
                }
                input:focus {
                    outline: none;
                    border-color: #00F5FF;
                }
                button {
                    width: 100%;
                    padding: 12px;
                    background: #00FF66;
                    color: #0A0F0D;
                    border: none;
                    border-radius: 4px;
                    font-family: monospace;
                    font-weight: bold;
                    font-size: 1rem;
                    cursor: pointer;
                    margin-top: 8px;
                }
                .badge {
                    display: block;
                    padding: 6px 10px;
                    border-radius: 4px;
                    font-size: 0.78rem;
                    margin-bottom: 16px;
                    background: rgba(0, 255, 102, 0.08);
                    border: 1px solid rgba(0, 255, 102, 0.35);
                    text-align: center;
                }
                .notice {
                    background: rgba(255, 179, 0, 0.1);
                    border: 1px solid #FFB300;
                    color: #FFD54F;
                    border-radius: 6px;
                    padding: 12px;
                    font-size: 0.85rem;
                    line-height: 1.4;
                }
                .error {
                    color: #FF5252;
                    font-size: 0.85rem;
                    margin-top: 10px;
                    text-align: center;
                }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="title">&gt;_ TERMINAL-IA WEB</div>
                <div class="subtitle">Puente autenticado hacia Termux Real (SSH local)</div>
                <div class="badge">${escapeHtml(statusBadgeText)}</div>
                $formHtml
            </div>
            <script>
                const form = document.getElementById('loginForm');
                if (form) {
                    form.addEventListener('submit', async (e) => {
                        e.preventDefault();
                        const err = document.getElementById('errorMsg');
                        err.textContent = '';
                        const username = document.getElementById('username').value.trim();
                        const password = document.getElementById('password').value;

                        try {
                            const res = await fetch('/api/login', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ username, password })
                            });
                            const data = await res.json();
                            if (data.success) {
                                window.location.reload();
                            } else {
                                err.textContent = data.error || 'Error de autenticación';
                            }
                        } catch (ex) {
                            err.textContent = 'Error al conectar con el teléfono: ' + ex.message;
                        }
                    });
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun getWebTerminalHtml(
        username: String,
        currentDir: String,
        realModeConnected: Boolean
    ): String {
        val safeUserHtml = escapeHtml(username)
        val safeDirHtml = escapeHtml(currentDir)
        val safeUserJs = toSafeJsStringLiteral(username)
        val safeDirJs = toSafeJsStringLiteral(currentDir)

        val statusBannerText = if (realModeConnected) {
            "MODO REAL CONECTADO (canal SSH local hacia Termux)"
        } else {
            "MODO REAL DESCONECTADO — Conecta la sesión SSH de Termux desde la app. El modo práctica simulado no se ejecuta en la terminal web."
        }
        val statusClass = if (realModeConnected) "status-ok" else "status-warn"

        return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Terminal-IA - Consola Web Real</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    background: #0A0F0D;
                    color: #00FF66;
                    font-family: 'Courier New', Courier, monospace;
                    display: flex;
                    flex-direction: column;
                    height: 100vh;
                    overflow: hidden;
                }
                .header {
                    background: #111A16;
                    padding: 10px 16px;
                    border-bottom: 1px solid #33FFAA;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    gap: 8px;
                }
                .header .brand {
                    font-weight: bold;
                    font-size: 1.05rem;
                }
                .header .brand span { color: #00F5FF; }
                .header .actions { display: flex; gap: 8px; }
                .btn {
                    background: #0A0F0D;
                    color: #00FF66;
                    border: 1px solid #33FFAA;
                    padding: 6px 12px;
                    border-radius: 4px;
                    font-family: monospace;
                    font-size: 0.8rem;
                    cursor: pointer;
                }
                .btn-danger {
                    border-color: #FF5252;
                    color: #FF5252;
                }
                .status-banner {
                    padding: 8px 16px;
                    font-size: 0.82rem;
                    font-weight: bold;
                    border-bottom: 1px solid #1a2a22;
                }
                .status-ok {
                    background: rgba(0, 230, 118, 0.12);
                    color: #00E676;
                }
                .status-warn {
                    background: rgba(255, 82, 82, 0.14);
                    color: #FF8A80;
                }
                .terminal-container {
                    flex: 1;
                    padding: 14px;
                    overflow-y: auto;
                    display: flex;
                    flex-direction: column;
                    gap: 4px;
                }
                .line {
                    white-space: pre-wrap;
                    word-break: break-word;
                    line-height: 1.35;
                }
                .line-cmd { color: #00E676; font-weight: bold; }
                .line-err { color: #FF5252; }
                .line-sys { color: #80FFD4; }
                .quick-bar {
                    background: #111A16;
                    padding: 6px 12px;
                    display: flex;
                    gap: 6px;
                    overflow-x: auto;
                    border-top: 1px solid #1a2a22;
                }
                .chip {
                    background: #0A0F0D;
                    border: 1px solid #33FFAA;
                    color: #80FFD4;
                    padding: 4px 10px;
                    font-size: 0.78rem;
                    border-radius: 4px;
                    cursor: pointer;
                    white-space: nowrap;
                    font-family: monospace;
                }
                .input-area {
                    background: #111A16;
                    padding: 10px 14px;
                    display: flex;
                    align-items: center;
                    border-top: 1px solid #33FFAA;
                }
                .prompt {
                    color: #00E676;
                    font-weight: bold;
                    margin-right: 8px;
                    white-space: nowrap;
                }
                #cmdInput {
                    flex: 1;
                    background: transparent;
                    border: none;
                    color: #00FF66;
                    font-family: monospace;
                    font-size: 1rem;
                    outline: none;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <div class="brand">&gt;_ TERMINAL-IA <span>[MODO REAL WEB]</span></div>
                <div class="actions">
                    <button class="btn" id="clearBtn" type="button">Limpiar</button>
                    <button class="btn btn-danger" id="logoutBtn" type="button">Salir</button>
                </div>
            </div>

            <div class="status-banner $statusClass" id="realStatusBanner">${escapeHtml(statusBannerText)}</div>

            <div class="quick-bar" id="quickBar">
                <button class="chip" type="button" data-cmd="uname -a">uname -a</button>
                <button class="chip" type="button" data-cmd="pwd">pwd</button>
                <button class="chip" type="button" data-cmd="ls -la">ls -la</button>
                <button class="chip" type="button" data-cmd="id">id</button>
                <button class="chip" type="button" data-cmd="uptime">uptime</button>
            </div>

            <div class="terminal-container" id="terminal">
                <div class="line line-sys">===============================================================</div>
                <div class="line line-sys">  TERMINAL-IA - CONSOLA WEB AUTENTICADA (SOLO MODO REAL SSH)</div>
                <div class="line line-sys">  Usuario web: $safeUserHtml | Directorio: $safeDirHtml</div>
                <div class="line line-sys">===============================================================</div>
            </div>

            <div class="input-area">
                <div class="prompt" id="promptLabel">$safeUserHtml@termux:$safeDirHtml$</div>
                <input type="text" id="cmdInput" autofocus autocomplete="off" placeholder="Escribe un comando Linux para Termux...">
            </div>

            <script>
                const term = document.getElementById('terminal');
                const input = document.getElementById('cmdInput');
                const promptLabel = document.getElementById('promptLabel');
                const statusBanner = document.getElementById('realStatusBanner');
                let history = [];
                let hIndex = -1;
                let currentDir = $safeDirJs;
                const user = $safeUserJs;

                document.getElementById('clearBtn').addEventListener('click', clearTerm);
                document.getElementById('logoutBtn').addEventListener('click', logout);
                document.querySelectorAll('#quickBar .chip').forEach((chip) => {
                    chip.addEventListener('click', () => {
                        input.value = chip.getAttribute('data-cmd') || '';
                        input.focus();
                    });
                });

                input.addEventListener('keydown', async (e) => {
                    if (e.key === 'Enter') {
                        const cmd = input.value.trim();
                        if (!cmd) return;
                        history.push(cmd);
                        hIndex = -1;
                        input.value = '';
                        await runCommand(cmd);
                    } else if (e.key === 'ArrowUp') {
                        if (history.length > 0) {
                            if (hIndex === -1) hIndex = history.length - 1;
                            else if (hIndex > 0) hIndex--;
                            input.value = history[hIndex];
                        }
                    } else if (e.key === 'ArrowDown') {
                        if (hIndex !== -1) {
                            if (hIndex < history.length - 1) {
                                hIndex++;
                                input.value = history[hIndex];
                            } else {
                                hIndex = -1;
                                input.value = '';
                            }
                        }
                    }
                });

                async function runCommand(cmd) {
                    addLine(user + '@termux:' + currentDir + '$ ' + cmd, 'line-cmd');

                    if (cmd.toLowerCase() === 'clear' || cmd.toLowerCase() === 'cls') {
                        clearTerm();
                        return;
                    }

                    try {
                        const res = await fetch('/api/exec', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ command: cmd })
                        });
                        const data = await res.json();
                        if (data.authenticated === false || res.status === 401) {
                            window.location.reload();
                            return;
                        }
                        if (data.realModeConnected === false) {
                            statusBanner.className = 'status-banner status-warn';
                            statusBanner.textContent = 'MODO REAL DESCONECTADO — Conecta la sesión SSH de Termux desde la app. El modo práctica simulado no se ejecuta en la terminal web.';
                        } else if (data.realModeConnected === true) {
                            statusBanner.className = 'status-banner status-ok';
                            statusBanner.textContent = 'MODO REAL CONECTADO (canal SSH local hacia Termux)';
                        }
                        if (data.error) {
                            addLine(data.error, 'line-err');
                        }
                        if (data.output) {
                            addLine(data.output, 'line');
                        }
                        if (data.currentDir) {
                            currentDir = data.currentDir;
                            promptLabel.textContent = user + '@termux:' + currentDir + '$';
                        }
                    } catch (err) {
                        addLine('Error de conexión con el teléfono: ' + err.message, 'line-err');
                    }
                }

                function addLine(text, className) {
                    const div = document.createElement('div');
                    div.className = 'line ' + (className || '');
                    div.textContent = text;
                    term.appendChild(div);
                    term.scrollTop = term.scrollHeight;
                }

                function clearTerm() {
                    term.replaceChildren();
                }

                async function logout() {
                    await fetch('/api/logout', { method: 'POST' });
                    window.location.reload();
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}
