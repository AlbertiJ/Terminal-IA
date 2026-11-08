package com.example.server

import android.content.Context
import com.example.data.TerminalPreferences
import com.example.engine.TerminalEngine
import com.example.model.LineType
import com.example.model.TerminalLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class WebTerminalServer(
    private val context: Context,
    private val preferences: TerminalPreferences,
    private val engine: TerminalEngine,
    private val onLogMessage: (String, LineType) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val secureRandom = SecureRandom()

    // Session token -> username (valid for 24 hours)
    private val activeSessions = ConcurrentHashMap<String, SessionInfo>()

    data class SessionInfo(val username: String, val createdAt: Long, var currentDir: String = "/home/user")

    var isRunning: Boolean = false
        private set

    var port: Int = 8080
        private set

    fun start(webPort: Int = 8080) {
        if (serverJob?.isActive == true) return
        this.port = webPort

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(webPort)
                isRunning = true
                onLogMessage("[Servidor Web Terminal iniciado en puerto :$webPort]", LineType.SYSTEM)

                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    scope.launch {
                        handleHttpRequest(client)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    isRunning = false
                    onLogMessage("Error en servidor Web Terminal: ${e.localizedMessage}", LineType.ERROR)
                }
            } finally {
                isRunning = false
            }
        }
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
        val clientIp = socket.inetAddress.hostAddress ?: "127.0.0.1"
        val security = preferences.getSecurityManager()

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
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
                        contentLength = value.toIntOrNull() ?: 0
                    }
                    if (key == "cookie") {
                        cookieHeader = value
                    }
                }
            }

            // Read Body if present
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

            // Extract session token
            val sessionToken = extractSessionToken(cookieHeader)
            val session = if (sessionToken != null) activeSessions[sessionToken] else null

            when {
                // 1. API: Login
                method == "POST" && path == "/api/login" -> {
                    handleApiLogin(requestBody, clientIp, out)
                }

                // 2. API: Logout
                method == "POST" && path == "/api/logout" -> {
                    if (sessionToken != null) activeSessions.remove(sessionToken)
                    sendJsonResponse(out, 200, "{\"success\": true}")
                }

                // 3. API: Session Check
                method == "GET" && path == "/api/session" -> {
                    if (session != null) {
                        val json = JSONObject().apply {
                            put("authenticated", true)
                            put("username", session.username)
                            put("currentDir", session.currentDir)
                        }
                        sendJsonResponse(out, 200, json.toString())
                    } else {
                        val json = JSONObject().apply {
                            put("authenticated", false)
                            put("hasCredentials", preferences.hasRemoteCredentials())
                            put("defaultUser", preferences.getRemoteUsername())
                        }
                        sendJsonResponse(out, 200, json.toString())
                    }
                }

                // 4. API: Execute Command
                method == "POST" && path == "/api/exec" -> {
                    if (session == null) {
                        sendJsonResponse(out, 401, "{\"error\": \"No autorizado. Inicia sesión.\", \"authenticated\": false}")
                        return@withContext
                    }
                    handleApiExec(session, requestBody, out)
                }

                // 5. Web UI: Terminal or Login
                method == "GET" && (path == "/" || path == "/index.html" || path == "/terminal") -> {
                    if (session != null) {
                        sendHtmlResponse(out, 200, getWebTerminalHtml(session.username, session.currentDir))
                    } else {
                        sendHtmlResponse(out, 200, getWebLoginHtml(preferences.hasRemoteCredentials(), preferences.getRemoteUsername()))
                    }
                }

                else -> {
                    sendHttpResponse(out, 404, "text/plain", "404 Not Found")
                }
            }
        } catch (e: Exception) {
            // Socket closed or connection reset
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun extractSessionToken(cookieHeader: String): String? {
        val cookies = cookieHeader.split(";").map { it.trim() }
        for (c in cookies) {
            if (c.startsWith("termux_session=")) {
                return c.removePrefix("termux_session=").trim()
            }
        }
        return null
    }

    private suspend fun handleApiLogin(body: String, clientIp: String, out: OutputStream) {
        val security = preferences.getSecurityManager()

        if (security.isIpLockedOut(clientIp)) {
            val remain = security.getRemainingLockoutSeconds(clientIp)
            sendJsonResponse(out, 403, "{\"success\": false, \"error\": \"Demasiados intentos. Espera $remain segundos.\"}")
            return
        }

        try {
            val json = JSONObject(body)
            val username = json.optString("username", "").trim()
            val password = json.optString("password", "").trim()

            // If no credentials configured yet, allow creating them on first web login!
            if (!preferences.hasRemoteCredentials()) {
                if (password.length < 4) {
                    sendJsonResponse(out, 400, "{\"success\": false, \"error\": \"La contraseña debe tener al menos 4 caracteres.\"}")
                    return
                }
                preferences.setRemoteCredentials(username.ifBlank { "user" }, password)
                onLogMessage("[Credenciales creadas desde interfaz Web por $username]", LineType.SYSTEM)
            }

            if (preferences.verifyRemoteCredentials(username, password)) {
                security.resetFailedAttempts(clientIp)
                val token = generateToken()
                activeSessions[token] = SessionInfo(username, System.currentTimeMillis())

                val resJson = JSONObject().apply {
                    put("success", true)
                    put("token", token)
                    put("username", username)
                }
                val setCookie = "termux_session=$token; Path=/; HttpOnly; SameSite=Lax"
                sendJsonResponse(out, 200, resJson.toString(), setCookie)
                onLogMessage("[Usuario '$username' autenticado vía Web Terminal desde $clientIp]", LineType.SYSTEM)
            } else {
                val attempts = security.recordFailedAttempt(clientIp)
                onLogMessage("[Fallo de inicio de sesión Web desde $clientIp ($attempts intentos)]", LineType.ERROR)
                sendJsonResponse(out, 401, "{\"success\": false, \"error\": \"Usuario o contraseña incorrectos.\"}")
            }
        } catch (e: Exception) {
            sendJsonResponse(out, 400, "{\"success\": false, \"error\": \"Formato JSON inválido.\"}")
        }
    }

    private suspend fun handleApiExec(session: SessionInfo, body: String, out: OutputStream) {
        try {
            val json = JSONObject(body)
            val command = json.optString("command", "").trim()
            if (command.isEmpty()) {
                sendJsonResponse(out, 200, "{\"output\": \"\", \"currentDir\": \"${session.currentDir}\"}")
                return
            }

            val outputBuffer = StringBuilder()
            engine.executeCommand(
                input = command,
                currentDir = session.currentDir,
                settings = preferences.getSettings(),
                onDirectoryChange = { newDir ->
                    session.currentDir = newDir
                },
                onOpenEditor = { path, _ ->
                    outputBuffer.append("[Editor visual no disponible en Web básica. Usa cat o echo]\n")
                },
                onOutputLine = { line ->
                    outputBuffer.append(line.text).append("\n")
                }
            )

            val res = JSONObject().apply {
                put("output", outputBuffer.toString().trimEnd())
                put("currentDir", session.currentDir)
                put("command", command)
            }
            sendJsonResponse(out, 200, res.toString())
        } catch (e: Exception) {
            sendJsonResponse(out, 500, "{\"error\": \"${e.localizedMessage}\"}")
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sendJsonResponse(out: OutputStream, code: Int, json: String, setCookie: String? = null) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out, true)
        writer.print("HTTP/1.1 $code ${if (code == 200) "OK" else if (code == 401) "Unauthorized" else "Error"}\r\n")
        writer.print("Content-Type: application/json; charset=UTF-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        if (setCookie != null) {
            writer.print("Set-Cookie: $setCookie\r\n")
        }
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    private fun sendHtmlResponse(out: OutputStream, code: Int, html: String) {
        sendHttpResponse(out, code, "text/html; charset=UTF-8", html)
    }

    private fun sendHttpResponse(out: OutputStream, code: Int, contentType: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out, true)
        writer.print("HTTP/1.1 $code OK\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    // --- HTML TEMPLATES ---

    private fun getWebLoginHtml(hasCredentials: Boolean, defaultUser: String): String {
        return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Termux AI - Web Terminal Login</title>
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
                    max-width: 420px;
                    padding: 24px;
                    box-shadow: 0 0 20px rgba(0, 255, 102, 0.15);
                }
                .title {
                    font-size: 1.4rem;
                    font-weight: bold;
                    text-align: center;
                    margin-bottom: 8px;
                    color: #00FF66;
                }
                .subtitle {
                    font-size: 0.85rem;
                    text-align: center;
                    color: #80FFD4;
                    margin-bottom: 20px;
                }
                .field {
                    margin-bottom: 14px;
                }
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
                    box-shadow: 0 0 8px rgba(0, 245, 255, 0.4);
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
                    transition: 0.2s;
                    margin-top: 8px;
                }
                button:hover {
                    background: #33FFAA;
                }
                .badge {
                    display: inline-block;
                    padding: 4px 8px;
                    border-radius: 4px;
                    font-size: 0.75rem;
                    margin-bottom: 16px;
                    background: rgba(0, 255, 102, 0.1);
                    border: 1px solid rgba(0, 255, 102, 0.3);
                    text-align: center;
                    width: 100%;
                }
                .error {
                    color: #FF4444;
                    font-size: 0.85rem;
                    margin-top: 10px;
                    text-align: center;
                }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="title">&gt;_ TERMUX AI CONSOLE</div>
                <div class="subtitle">Acceso Remoto al Teléfono Android (Web Socket)</div>
                <div class="badge">
                    ${if (hasCredentials) "Seguridad: PBKDF2 Criptográfico Activo" else "¡Primer Acceso! Crea tu usuario y clave"}
                </div>
                <form id="loginForm">
                    <div class="field">
                        <label for="username">Usuario:</label>
                        <input type="text" id="username" value="${defaultUser}" required autofocus>
                    </div>
                    <div class="field">
                        <label for="password">${if (hasCredentials) "Contraseña:" else "Crear Contraseña (mínimo 4 caracteres):"}</label>
                        <input type="password" id="password" required>
                    </div>
                    <button type="submit">${if (hasCredentials) "Iniciar Sesión" else "Crear Credenciales y Entrar"}</button>
                    <div id="errorMsg" class="error"></div>
                </form>
            </div>
            <script>
                document.getElementById('loginForm').addEventListener('submit', async (e) => {
                    e.preventDefault();
                    const err = document.getElementById('errorMsg');
                    err.innerText = '';
                    const username = document.getElementById('username').value.trim();
                    const password = document.getElementById('password').value.trim();

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
                            err.innerText = data.error || 'Error de autenticación';
                        }
                    } catch (ex) {
                        err.innerText = 'Error al conectar con el celular: ' + ex.message;
                    }
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun getWebTerminalHtml(username: String, currentDir: String): String {
        return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Termux AI - Web Terminal Console</title>
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
                }
                .header .brand {
                    font-weight: bold;
                    font-size: 1.1rem;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
                .header .brand span {
                    color: #00F5FF;
                }
                .header .actions {
                    display: flex;
                    gap: 8px;
                }
                .btn {
                    background: #0A0F0D;
                    color: #00FF66;
                    border: 1px solid #33FFAA;
                    padding: 5px 12px;
                    border-radius: 4px;
                    font-family: monospace;
                    font-size: 0.8rem;
                    cursor: pointer;
                    transition: 0.2s;
                }
                .btn:hover {
                    background: #00FF66;
                    color: #0A0F0D;
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
                    word-break: break-all;
                    line-height: 1.35;
                }
                .line-cmd {
                    color: #00E676;
                    font-weight: bold;
                }
                .line-claude {
                    color: #D4A373;
                    background: rgba(212, 163, 115, 0.08);
                    border-left: 3px solid #D4A373;
                    padding: 6px 10px;
                    border-radius: 4px;
                    margin: 4px 0;
                }
                .line-openclaw {
                    color: #00E5FF;
                    background: rgba(0, 229, 255, 0.08);
                    border-left: 3px solid #00E5FF;
                    padding: 6px 10px;
                    border-radius: 4px;
                    margin: 4px 0;
                }
                .line-err {
                    color: #FF4444;
                }
                .line-sys {
                    color: #80FFD4;
                }
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
                    padding: 3px 8px;
                    font-size: 0.75rem;
                    border-radius: 4px;
                    cursor: pointer;
                    white-space: nowrap;
                }
                .chip:hover {
                    background: #33FFAA;
                    color: #0A0F0D;
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
                <div class="brand">
                    &gt;_ TERMUX AI <span>[WEB CONSOLE]</span>
                </div>
                <div class="actions">
                    <button class="btn" onclick="runCommand('neofetch')">Neofetch</button>
                    <button class="btn" onclick="runCommand('ip')">IP Info</button>
                    <button class="btn" onclick="clearTerm()">Limpiar</button>
                    <button class="btn" style="border-color:#FF4444; color:#FF4444" onclick="logout()">Salir</button>
                </div>
            </div>

            <div class="quick-bar">
                <span class="chip" onclick="setInput('help')">help</span>
                <span class="chip" onclick="setInput('neofetch')">neofetch</span>
                <span class="chip" onclick="setInput('ls -la')">ls -la</span>
                <span class="chip" onclick="setInput('claude &quot;analiza el sistema&quot;')">claude</span>
                <span class="chip" onclick="setInput('openclaw run syscheck')">openclaw</span>
                <span class="chip" onclick="setInput('cat ~/scripts/syscheck.sh | claude &quot;explica&quot;')">pipe claude</span>
                <span class="chip" onclick="setInput('ping 8.8.8.8')">ping</span>
            </div>

            <div class="terminal-container" id="terminal">
                <div class="line line-sys">===============================================================</div>
                <div class="line line-sys">  TERMUX AI - CONSOLA WEB REMOTA AUTENTICADA</div>
                <div class="line line-sys">  Conectado como: $username | Android Remote Shell</div>
                <div class="line line-sys">  Escribe 'help' para referencia o usa los botones rápidos.</div>
                <div class="line line-sys">===============================================================</div>
            </div>

            <div class="input-area">
                <div class="prompt" id="promptLabel">$username@termux:$currentDir$</div>
                <input type="text" id="cmdInput" autofocus autocomplete="off" placeholder="Escribe un comando...">
            </div>

            <script>
                const term = document.getElementById('terminal');
                const input = document.getElementById('cmdInput');
                const promptLabel = document.getElementById('promptLabel');
                let history = [];
                let hIndex = -1;
                let currentDir = '$currentDir';
                const user = '$username';

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
                        if (data.authenticated === false) {
                            window.location.reload();
                            return;
                        }
                        if (data.output) {
                            let type = 'line';
                            if (cmd.startsWith('claude')) type = 'line-claude';
                            else if (cmd.startsWith('openclaw')) type = 'line-openclaw';
                            else if (data.output.includes('Error') || data.output.includes('error')) type = 'line-err';
                            addLine(data.output, type);
                        }
                        if (data.currentDir) {
                            currentDir = data.currentDir;
                            promptLabel.innerText = user + '@termux:' + currentDir + '$';
                        }
                    } catch (err) {
                        addLine('Error de conexión con el celular: ' + err.message, 'line-err');
                    }
                }

                function addLine(text, className) {
                    const div = document.createElement('div');
                    div.className = 'line ' + (className || '');
                    div.innerText = text;
                    term.appendChild(div);
                    term.scrollTop = term.scrollHeight;
                }

                function setInput(val) {
                    input.value = val;
                    input.focus();
                }

                function clearTerm() {
                    term.innerHTML = '';
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
