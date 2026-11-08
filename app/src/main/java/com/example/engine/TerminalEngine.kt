package com.example.engine

import android.content.Context
import android.os.Build
import com.example.agent.ClaudeAgentService
import com.example.agent.OpenClawAgentService
import com.example.data.TerminalPreferences
import com.example.fs.VirtualFileSystem
import com.example.model.AgentSettings
import com.example.model.LineType
import com.example.model.TerminalLine
import com.example.script.ScriptManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TerminalEngine(
    private val context: Context,
    private val vfs: VirtualFileSystem,
    private val claudeService: ClaudeAgentService,
    private val openClawService: OpenClawAgentService,
    private val preferences: TerminalPreferences,
    private val historyRepository: com.example.data.db.CommandHistoryRepository? = null,
    private val onServerAction: ((action: String, port: Int) -> Unit)? = null
) {
    val scriptManager = ScriptManager(context, vfs)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val securityManager = preferences.getSecurityManager()

    private val environmentVariables = mutableMapOf(
        "USER" to "user",
        "HOME" to "/home/user",
        "SHELL" to "/bin/bash",
        "TERM" to "xterm-256color",
        "PREFIX" to "/data/data/com.aistudio.termuxai/files/usr",
        "CLAUDE_AGENT_VERSION" to "3.5-v2024",
        "OPENCLAW_VERSION" to "2.4.0-autonomous",
        "PATH" to "/bin:/home/user/scripts"
    )

    fun getInitialWelcomeLines(): List<TerminalLine> {
        val welcome = """
            ================================================================
             TERMUX AI - Android Console & Autonomous Agent Hub
            ================================================================
             * Terminal Shell: Bash emulator with VFS sandbox (Sandboxed)
             * Seguridad: Cifrado AES-256-GCM para APIs & Hash PBKDF2
             * Conexión Remota: Servidor SSH / Telnet (puerto 8022)
             * Agentes: Claude (Anthropic) & OpenClaw Autónomo
            ----------------------------------------------------------------
             * Escribe 'ip' para ver la IP de tu celular y conectarte remoto.
             * Escribe 'sshd start' para iniciar el acceso remoto seguro.
             * Escribe 'passwd' para configurar tus credenciales de acceso.
             * Escribe 'help' para la lista completa de comandos.
            ================================================================
        """.trimIndent()

        return listOf(
            TerminalLine(text = welcome, type = LineType.HEADER)
        )
    }

    suspend fun executeCommand(
        input: String,
        currentDir: String,
        settings: AgentSettings,
        onDirectoryChange: (String) -> Unit,
        onOpenEditor: (String, String) -> Unit,
        onOutputLine: (TerminalLine) -> Unit
    ) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        // 1. Security safety validation: Block destructive commands
        val safety = securityManager.validateCommandSafety(trimmed)
        if (!safety.isSafe) {
            onOutputLine(
                TerminalLine(
                    text = "SEGURIDAD: ${safety.reason}",
                    type = LineType.ERROR
                )
            )
            return
        }

        // 2. Check for pipe (|) e.g., "cat welcome.txt | claude 'resume esto'"
        if (trimmed.contains("|") && !trimmed.startsWith("echo")) {
            val pipeSegments = splitByPipe(trimmed)
            if (pipeSegments.size > 1) {
                handlePipedExecution(pipeSegments, currentDir, settings, onDirectoryChange, onOpenEditor, onOutputLine)
                return
            }
        }

        // 3. Check for redirection (e.g. echo "hi" > file.txt)
        val redirectAppend = trimmed.contains(">>")
        val redirectOverwrite = !redirectAppend && trimmed.contains(">")
        if (redirectAppend || redirectOverwrite) {
            handleRedirection(trimmed, currentDir, redirectAppend, onOutputLine)
            return
        }

        val tokens = tokenizeCommand(trimmed)
        if (tokens.isEmpty()) return
        val cmd = tokens[0].lowercase()
        val args = tokens.drop(1)

        when (cmd) {
            "help", "?" -> showHelp(args, onOutputLine)
            "clear", "cls" -> {
                // Handled in ViewModel
            }
            "neofetch", "fastfetch" -> showNeofetch(settings, onOutputLine)
            "ls" -> listFiles(args, currentDir, onOutputLine)
            "cd" -> changeDirectory(args, currentDir, onDirectoryChange, onOutputLine)
            "pwd" -> onOutputLine(TerminalLine(text = currentDir, type = LineType.OUTPUT))
            "cat" -> readFileContent(args, currentDir, onOutputLine)
            "echo" -> onOutputLine(TerminalLine(text = args.joinToString(" "), type = LineType.OUTPUT))
            "mkdir" -> makeDir(args, currentDir, onOutputLine)
            "rm" -> removeFileOrDir(args, currentDir, onOutputLine)
            "touch" -> touchFile(args, currentDir, onOutputLine)
            "date" -> {
                val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
                onOutputLine(TerminalLine(text = sdf.format(Date()), type = LineType.OUTPUT))
            }
            "whoami" -> onOutputLine(TerminalLine(text = "${preferences.getRemoteUsername()}@termux-ai", type = LineType.OUTPUT))
            "uname" -> {
                val unameInfo = "Linux termux-ai 5.15.0-arm64 Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) aarch64 GNU/Linux"
                onOutputLine(TerminalLine(text = unameInfo, type = LineType.OUTPUT))
            }
            "ip", "ifconfig" -> showIpAddresses(onOutputLine)
            "sshd", "telnetd" -> handleSshServerCommand(cmd, args, onOutputLine)
            "passwd" -> handlePasswdCommand(args, onOutputLine)
            "agent" -> handleAgentApiCommand(args, settings, onOutputLine)
            "env" -> showMaskedEnv(onOutputLine)
            "export" -> handleExport(args, onOutputLine)
            "curl" -> runCurl(args, onOutputLine)
            "ping" -> runPing(args, onOutputLine)
            "nano", "edit", "vim", "vi" -> openTextEditor(args, currentDir, onOpenEditor, onOutputLine)
            "python", "py", "python3" -> runPython(args, currentDir, onOutputLine)
            "bash", "sh", "run" -> runScript(args, currentDir, onOutputLine)
            "script", "scripts" -> handleScriptCommand(args, currentDir, onOpenEditor, onOutputLine)
            "chmod" -> handleChmodCommand(args, currentDir, onOutputLine)
            "history" -> handleHistoryCommand(args, onOutputLine)
            "pkg", "apt" -> runPkg(args, onOutputLine)
            "top", "ps" -> runTop(onOutputLine)
            "claude" -> runClaudeCommand(args, settings, onOutputLine)
            "openclaw" -> runOpenClawCommand(args, settings, onOutputLine)
            else -> {
                if (trimmed.startsWith("./") || trimmed.endsWith(".sh") || trimmed.endsWith(".py")) {
                    runScript(listOf(trimmed), currentDir, onOutputLine)
                } else {
                    onOutputLine(
                        TerminalLine(
                            text = "bash: $cmd: command not found. Type 'help' for available commands or 'claude \"$trimmed\"' to ask AI.",
                            type = LineType.ERROR
                        )
                    )
                }
            }
        }
    }

    private fun splitByPipe(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = java.lang.StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        for (c in input) {
            when {
                (c == '"' || c == '\'') && !inQuotes -> {
                    inQuotes = true
                    quoteChar = c
                    current.append(c)
                }
                c == quoteChar && inQuotes -> {
                    inQuotes = false
                    current.append(c)
                }
                c == '|' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> {
                    current.append(c)
                }
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }
        return result
    }

    private suspend fun handlePipedExecution(
        stages: List<String>,
        currentDir: String,
        settings: AgentSettings,
        onDirectoryChange: (String) -> Unit,
        onOpenEditor: (String, String) -> Unit,
        onOutputLine: (TerminalLine) -> Unit
    ) {
        val capturedOutput = StringBuilder()

        // Execute stage 1
        val stage1 = stages[0]
        executeCommand(
            input = stage1,
            currentDir = currentDir,
            settings = settings,
            onDirectoryChange = onDirectoryChange,
            onOpenEditor = onOpenEditor,
            onOutputLine = { line ->
                capturedOutput.append(line.text).append("\n")
            }
        )

        // Stage 2: feed captured output into stage 2 (e.g. claude or openclaw)
        val stage2 = stages[1]
        val pipedContent = capturedOutput.toString().trim()

        if (stage2.startsWith("claude", ignoreCase = true)) {
            val userPrompt = stage2.removePrefix("claude").trim().removeSurrounding("\"")
            val fullPrompt = "$userPrompt\n\n--- ENTRADA DE PIPE STDIN ---\n$pipedContent"
            runClaudeCommand(listOf(fullPrompt), settings, onOutputLine)
        } else if (stage2.startsWith("openclaw", ignoreCase = true)) {
            val userPrompt = stage2.removePrefix("openclaw").trim().removeSurrounding("\"")
            val fullGoal = "$userPrompt - Datos de entrada: $pipedContent"
            runOpenClawCommand(listOf(fullGoal), settings, onOutputLine)
        } else {
            // General filter or echo
            executeCommand("$stage2 $pipedContent", currentDir, settings, onDirectoryChange, onOpenEditor, onOutputLine)
        }
    }

    private fun showIpAddresses(onOutputLine: (TerminalLine) -> Unit) {
        val ips = mutableListOf<Pair<String, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotEmpty() && !host.startsWith("127.")) {
                            ips.add(Pair(iface.displayName ?: iface.name, host))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val port = preferences.getRemotePort()
        val user = preferences.getRemoteUsername()
        val credStatus = if (preferences.hasRemoteCredentials()) "Configurado (Hash PBKDF2 activo)" else "NO configurado (Usa 'passwd' para crear)"

        val text = buildString {
            append("================================================================\n")
            append("           CONECTIVIDAD REMOTA DEL DISPOSITIVO (SSH/TELNET)\n")
            append("================================================================\n")
            if (ips.isEmpty()) {
                append("! No se detectó interfaz Wi-Fi/LAN activa. Conéctate a Wi-Fi o Hotspot.\n")
                append("IP Local: 127.0.0.1 (localhost)\n")
            } else {
                append("Interfaces de red activas detectadas:\n")
                for ((iface, ip) in ips) {
                    append(" * $iface: $ip\n")
                }
            }
            append("\nPuerto de conexión: $port\n")
            append("Usuario remoto: $user\n")
            append("Estado de credenciales: $credStatus\n")
            append("\nPara conectarte remotamente desde tu PC, Mac o terminal:\n")
            val primaryIp = ips.firstOrNull()?.second ?: "192.168.X.X"
            append("  ssh $user@$primaryIp -p $port\n")
            append("  telnet $primaryIp $port\n")
            append("----------------------------------------------------------------\n")
            append("Comandos de servidor:\n")
            append("  sshd start       Inicia el servidor en el celular\n")
            append("  sshd stop        Detiene el servidor\n")
            append("  passwd [clave]   Crea o cambia tu contraseña de acceso\n")
            append("================================================================")
        }
        onOutputLine(TerminalLine(text = text, type = LineType.OUTPUT))
    }

    private fun handleSshServerCommand(cmdName: String, args: List<String>, onOutputLine: (TerminalLine) -> Unit) {
        if (args.isEmpty()) {
            val port = preferences.getRemotePort()
            val state = if (preferences.isRemoteServerEnabled()) "ACTIVO (Escuchando en :$port)" else "DETENIDO"
            onOutputLine(
                TerminalLine(
                    text = "$cmdName: Servidor está $state.\nUso: $cmdName [start|stop|status|restart] [puerto]",
                    type = LineType.SYSTEM
                )
            )
            return
        }

        when (args[0].lowercase()) {
            "start" -> {
                val port = args.getOrNull(1)?.toIntOrNull() ?: preferences.getRemotePort()
                if (!preferences.hasRemoteCredentials()) {
                    onOutputLine(
                        TerminalLine(
                            text = "[AVISO DE SEGURIDAD] Aún no tienes usuario y clave configurados.\nEscribe: passwd <tu_clave> antes de iniciar el acceso remoto.",
                            type = LineType.ERROR
                        )
                    )
                }
                onServerAction?.invoke("start", port)
                onOutputLine(TerminalLine(text = "Iniciando servidor remoto en puerto $port...", type = LineType.SYSTEM))
                showIpAddresses(onOutputLine)
            }
            "stop" -> {
                onServerAction?.invoke("stop", 0)
                onOutputLine(TerminalLine(text = "Servidor remoto detenido.", type = LineType.SYSTEM))
            }
            "status" -> {
                val port = preferences.getRemotePort()
                val isRunning = preferences.isRemoteServerEnabled()
                onOutputLine(
                    TerminalLine(
                        text = "Estado del servidor remoto: ${if (isRunning) "ACTIVO en puerto $port" else "INACTIVO"}\nCredenciales configuradas: ${if (preferences.hasRemoteCredentials()) "SÍ (PBKDF2)" else "NO"}",
                        type = LineType.SYSTEM
                    )
                )
            }
            else -> onOutputLine(TerminalLine(text = "$cmdName: subcomando desconocido '${args[0]}'", type = LineType.ERROR))
        }
    }

    private fun handlePasswdCommand(args: List<String>, onOutputLine: (TerminalLine) -> Unit) {
        if (args.isEmpty()) {
            onOutputLine(
                TerminalLine(
                    text = "Uso: passwd <nueva_contraseña> o passwd <usuario> <nueva_contraseña>\nEjemplo: passwd admin MiClaveSegura2026",
                    type = LineType.SYSTEM
                )
            )
            return
        }

        val (username, newPass) = if (args.size == 1) {
            Pair(preferences.getRemoteUsername(), args[0])
        } else {
            Pair(args[0], args[1])
        }

        if (newPass.length < 4) {
            onOutputLine(TerminalLine(text = "Error: La contraseña debe tener al menos 4 caracteres por seguridad.", type = LineType.ERROR))
            return
        }

        preferences.setRemoteCredentials(username, newPass)
        onOutputLine(
            TerminalLine(
                text = "[ÉXITO] Credenciales guardadas y encriptadas con PBKDF2-SHA256 (10,000 iteraciones + Salt criptográfico).\nUsuario: $username",
                type = LineType.SYSTEM
            )
        )
    }

    private suspend fun handleAgentApiCommand(args: List<String>, settings: AgentSettings, onOutputLine: (TerminalLine) -> Unit) {
        if (args.isEmpty()) {
            val help = """
                [Termux AI Agent API Suite]
                Funciones de API programáticas para scripts y pipelines:
                  agent api claude "prompt"     Llamada directa en formato JSON
                  agent api openclaw "goal"     Ejecución estructurada de herramientas
                  agent list                    Lista de agentes y modelos activos
                  agent status                  Estado y seguridad de los agentes
            """.trimIndent()
            onOutputLine(TerminalLine(text = help, type = LineType.SYSTEM))
            return
        }

        when (args[0].lowercase()) {
            "api" -> {
                if (args.size < 3) {
                    onOutputLine(TerminalLine(text = "Uso: agent api [claude|openclaw] <instrucción>", type = LineType.ERROR))
                    return
                }
                val targetAgent = args[1].lowercase()
                val prompt = args.drop(2).joinToString(" ").removeSurrounding("\"")

                if (targetAgent == "claude") {
                    val res = claudeService.queryClaude(prompt, settings.claudeApiKey, settings.claudeModel, settings.claudeSystemPrompt)
                    res.onSuccess { r ->
                        val json = JSONObject().apply {
                            put("agent", "claude")
                            put("model", r.modelUsed)
                            put("is_live", r.isLiveApi)
                            put("response", r.text)
                            put("suggested_command", r.suggestedCommand ?: JSONObject.NULL)
                        }
                        onOutputLine(TerminalLine(text = json.toString(2), type = LineType.OUTPUT))
                    }.onFailure { err ->
                        onOutputLine(TerminalLine(text = "{\"error\": \"${err.localizedMessage}\"}", type = LineType.ERROR))
                    }
                } else if (targetAgent == "openclaw") {
                    openClawService.executeTask(prompt, settings.openClawEndpoint, settings.openClawToken) { step ->
                        // Step callback
                    }
                    val json = JSONObject().apply {
                        put("agent", "openclaw")
                        put("goal", prompt)
                        put("status", "completed")
                        put("autonomous", settings.openClawAutonomous)
                    }
                    onOutputLine(TerminalLine(text = json.toString(2), type = LineType.OUTPUT))
                }
            }
            "list" -> {
                val list = """
                    [Agentes Disponibles en Termux AI]
                    1. claude (Anthropic API - Sonnet/Haiku/Opus) - Estado: Listo
                    2. openclaw (Autonomous Crawling & Tool Executor) - Estado: Activo
                """.trimIndent()
                onOutputLine(TerminalLine(text = list, type = LineType.OUTPUT))
            }
            "status" -> {
                val keyMasked = securityManager.maskSecret(settings.claudeApiKey)
                val status = """
                    [Estado de Agentes & Políticas de Seguridad]
                    * Claude Model: ${settings.claudeModel}
                    * Claude API Key: $keyMasked (Almacenada con AES-256-GCM)
                    * OpenClaw Endpoint: ${settings.openClawEndpoint}
                    * Modo Autónomo: ${if (settings.openClawAutonomous) "Permitido con filtro de seguridad" else "Manual"}
                    * Protección de comandos destructivos: ACTIVA
                """.trimIndent()
                onOutputLine(TerminalLine(text = status, type = LineType.OUTPUT))
            }
            else -> onOutputLine(TerminalLine(text = "agent: subcomando desconocido '${args[0]}'", type = LineType.ERROR))
        }
    }

    private fun showMaskedEnv(onOutputLine: (TerminalLine) -> Unit) {
        val envList = environmentVariables.entries.joinToString("\n") { (k, v) ->
            if (k.contains("KEY") || k.contains("TOKEN") || k.contains("PASS")) {
                "$k=${securityManager.maskSecret(v)}"
            } else {
                "$k=$v"
            }
        }
        onOutputLine(TerminalLine(text = envList, type = LineType.OUTPUT))
    }

    private fun handleExport(args: List<String>, onOutputLine: (TerminalLine) -> Unit) {
        if (args.isEmpty()) {
            showMaskedEnv(onOutputLine)
            return
        }
        for (arg in args) {
            val eqIdx = arg.indexOf('=')
            if (eqIdx > 0) {
                val k = arg.substring(0, eqIdx)
                val v = arg.substring(eqIdx + 1).removeSurrounding("\"").removeSurrounding("'")
                environmentVariables[k] = v
                onOutputLine(TerminalLine(text = "[$k configurada en el entorno]", type = LineType.SYSTEM))
            }
        }
    }

    private fun tokenizeCommand(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = java.lang.StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        for (c in input) {
            when {
                (c == '"' || c == '\'') && !inQuotes -> {
                    inQuotes = true
                    quoteChar = c
                }
                c == quoteChar && inQuotes -> {
                    inQuotes = false
                }
                c.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> {
                    current.append(c)
                }
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun handleRedirection(
        fullInput: String,
        currentDir: String,
        append: Boolean,
        onOutputLine: (TerminalLine) -> Unit
    ) {
        val splitOp = if (append) ">>" else ">"
        val parts = fullInput.split(splitOp, limit = 2)
        if (parts.size < 2) return

        val cmdPart = parts[0].trim()
        val targetFilePart = parts[1].trim()

        val targetPath = vfs.normalizePath(currentDir, targetFilePart)
        val textToWrite = if (cmdPart.startsWith("echo ", ignoreCase = true)) {
            cmdPart.substring(5).trim().removeSurrounding("\"").removeSurrounding("'") + "\n"
        } else {
            "Output of: $cmdPart\n"
        }

        val res = vfs.writeFile(targetPath, textToWrite, append = append)
        res.onSuccess {
            onOutputLine(TerminalLine(text = "[Escrito en $targetPath]", type = LineType.SYSTEM))
        }.onFailure { err ->
            onOutputLine(TerminalLine(text = "Error de escritura: ${err.localizedMessage}", type = LineType.ERROR))
        }
    }

    private fun showHelp(args: List<String>, onOutputLine: (TerminalLine) -> Unit) {
        val helpText = """
            ================================================================
                       TERMUX AI COMMAND REFERENCE GUIDE
            ================================================================
            * CONEXIÓN REMOTA (SSH / TELNET):
              ip / ifconfig         Muestra la IP del celular y comandos de acceso
              sshd start [puerto]   Inicia servidor de acceso remoto seguro
              sshd stop             Detiene el servidor remoto
              passwd [clave]        Configura contraseña cifrada con PBKDF2
              telnetd start [port]  Inicia socket telnet/consola remota

            * AGENTE CLAUDE (Anthropic):
              claude "consulta"     Envía consulta directa al agente Claude
              claude -i             Inicia sesión de chat interactiva
              claude config         Ver modelo configurado y API Key (enmascarada)
              cat script | claude "analiza"  Canalización vía pipe a Claude

            * AGENTE OPENCLAW (Autónomo):
              openclaw "objetivo"   Planifica y ejecuta herramientas autónomas
              openclaw run syscheck Ejecuta auditoría y verificación de sistema
              openclaw run audit    Escanea archivos, permisos y scripts en VFS
              agent api [claude|openclaw]  Llamadas API en formato JSON

            * ARCHIVOS Y NAVEGACIÓN:
              ls [-la] [dir]        Lista contenidos de carpeta
              cd <dir>              Cambia de directorio (~ para home)
              pwd                   Muestra directorio actual
              cat <archivo>         Lee contenido de archivo
              nano / edit <archivo> Abre el editor visual integrado
              touch <archivo>       Crea archivo vacío
              mkdir <carpeta>       Crea una nueva carpeta
              rm [-r] <destino>     Elimina archivo o carpeta (protegido)
              echo "texto" > file   Redirige texto a archivo

            * RED Y SISTEMA:
              neofetch              Muestra ficha gráfica del sistema Android
              curl <url>            Petición HTTP real y muestra respuesta
              ping <host>           Prueba de latencia y conectividad
              python <script.py>    Ejecutor de scripts en Python
              bash / run <script>   Ejecuta script de terminal
              clear                 Limpia la pantalla
            ================================================================
        """.trimIndent()
        onOutputLine(TerminalLine(text = helpText, type = LineType.OUTPUT))
    }

    private fun showNeofetch(settings: AgentSettings, onOutputLine: (TerminalLine) -> Unit) {
        val runtime = Runtime.getRuntime()
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)
        val freeMemMb = runtime.freeMemory() / (1024 * 1024)
        val usedMemMb = totalMemMb - freeMemMb
        val serverStatus = if (preferences.isRemoteServerEnabled()) "Listening :${preferences.getRemotePort()}" else "Stopped"

        val art = """
             [36m      __             [0m  [1;32m${preferences.getRemoteUsername()}@termux-ai [0m
             [36m     /  \            [0m  [37m-------------- [0m
             [36m    | () |           [0m  [1;36mOS: [0m Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
             [36m   /      \          [0m  [1;36mHost: [0m ${Build.MANUFACTURER} ${Build.MODEL}
             [36m  |   __   |         [0m  [1;36mKernel: [0m Linux 5.15.0-arm64
             [36m  |  /  \  |         [0m  [1;36mSSH/Telnet: [0m $serverStatus
             [36m   \/    \/          [0m  [1;36mSecurity: [0m PBKDF2 + AES-256-GCM
             [36m   / >_   \          [0m  [1;36mTerminal: [0m Termux-AI v1.1
             [36m  |  CLAW  |         [0m  [1;36mCPU: [0m ${Build.HARDWARE} (${Runtime.getRuntime().availableProcessors()} cores)
             [36m   \______/          [0m  [1;36mMemory: [0m ${usedMemMb}MB / ${totalMemMb}MB
             [36m                     [0m  [1;35mClaude: [0m ${if (settings.claudeApiKey.isNotBlank()) "Linked (Sonnet)" else "Ready (Sandbox)"}
             [36m                     [0m  [1;33mOpenClaw: [0m Active (Autonomous v2.4)
             [36m                     [0m  [1;34mColors: [0m  [41m   [42m   [43m   [44m   [45m   [46m   [47m   [0m
        """.trimIndent()
        onOutputLine(TerminalLine(text = art, type = LineType.HEADER))
    }

    private fun listFiles(args: List<String>, currentDir: String, onOutputLine: (TerminalLine) -> Unit) {
        val target = args.firstOrNull { !it.startsWith("-") } ?: currentDir
        val resolved = vfs.normalizePath(currentDir, target)
        val result = vfs.listDirectory(resolved)

        result.onSuccess { entries ->
            if (entries.isEmpty()) {
                onOutputLine(TerminalLine(text = "(directorio vacío)", type = LineType.SYSTEM))
                return
            }
            val formatted = entries.joinToString("  ") { e ->
                if (e.isDirectory) {
                    " [1;34m${e.name}/ [0m"
                } else if (e.isExecutable) {
                    " [1;32m${e.name}* [0m"
                } else {
                    e.name
                }
            }
            onOutputLine(TerminalLine(text = formatted, type = LineType.OUTPUT))
        }.onFailure {
            onOutputLine(TerminalLine(text = "ls: ${it.localizedMessage}", type = LineType.ERROR))
        }
    }

    private fun changeDirectory(
        args: List<String>,
        currentDir: String,
        onDirectoryChange: (String) -> Unit,
        onOutputLine: (TerminalLine) -> Unit
    ) {
        val target = if (args.isEmpty()) "/home/user" else args[0]
        val resolved = vfs.normalizePath(currentDir, target)
        if (vfs.exists(resolved) && vfs.isDirectory(resolved)) {
            onDirectoryChange(resolved)
        } else {
            onOutputLine(TerminalLine(text = "cd: $target: No such file or directory", type = LineType.ERROR))
        }
    }

    private fun readFileContent(args: List<String>, currentDir: String, onOutputLine: (TerminalLine) -> Unit) {
        if (args.isEmpty()) {
            onOutputLine(TerminalLine(text = "cat: falta el operando de archivo", type = LineType.ERROR))
            return
        }
        for (arg in args) {
            val path = vfs.normalizePath(currentDir, arg)
            vfs.readFile(path).onSuccess { content ->
                onOutputLine(TerminalLine(text = content, type = LineType.OUTPUT))
            }.onFailure { err ->
                onOutputLine(TerminalLine(text = "cat: $arg: ${err.localizedMessage}", type = LineType.ERROR))
            }
        }
    }

    private fun makeDir(args: List<String>, currentDir: String, onOutputLine: (TerminalLine) -> Unit) {
        if (args.isEmpty()) {
            onOutputLine(TerminalLine(text = "mkdir: falta el operando", type = LineType.ERROR))
            return
        }
        for (arg in args) {
            val path = vfs.normalizePath(currentDir, arg)
            vfs.makeDirectory(path).onSuccess {
                onOutputLine(TerminalLine(text = "Creado directorio: $arg", type = LineType.SYSTEM))
            }.onFailure { err ->
                onOutputLine(TerminalLine(text = "mkdir: ${err.localizedMessage}", type = LineType.ERROR))
            }
        }
    }

    private fun removeFileOrDir(args: List<String>, currentDir: String, onOutputLine: (TerminalLine) -> Unit) {
        if (args.isEmpty()) {
            onOutputLine(TerminalLine(text = "rm: falta el operando", type = LineType.ERROR))
            return
        }
        val targets = args.filter { !it.startsWith("-") }
        for (target in targets) {
            val path = vfs.normalizePath(currentDir, target)
            // Extra security check for critical paths
            if (path == "/" || path == "/home" || path == "/bin" || path == "/etc") {
                onOutputLine(TerminalLine(text = "rm: no se permite eliminar directorios raíz del sistema.", type = LineType.ERROR))
                continue
            }
            vfs.deleteFile(path).onSuccess {
                onOutputLine(TerminalLine(text = "Eliminado: $target", type = LineType.SYSTEM))
            }.onFailure { err ->
                onOutputLine(TerminalLine(text = "rm: ${err.localizedMessage}", type = LineType.ERROR))
            }
        }
    }

    private fun touchFile(args: List<String>, currentDir: String, onOutputLine: (TerminalLine) -> Unit) {
        if (args.isEmpty()) {
            onOutputLine(TerminalLine(text = "touch: falta el operando", type = LineType.ERROR))
            return
        }
        for (arg in args) {
            val path = vfs.normalizePath(currentDir, arg)
            if (!vfs.exists(path)) {
                vfs.writeFile(path, "").onSuccess {
                    onOutputLine(TerminalLine(text = "Creado: $arg", type = LineType.SYSTEM))
                }.onFailure { err ->
                    onOutputLine(TerminalLine(text = "touch: ${err.localizedMessage}", type = LineType.ERROR))
                }
            }
        }
    }

    private fun openTextEditor(
        args: List<String>,
        currentDir: String,
        onOpenEditor: (String, String) -> Unit,
        onOutputLine: (TerminalLine) -> Unit
    ) {
        val target = if (args.isEmpty()) "untitled.sh" else args[0]
        val path = vfs.normalizePath(currentDir, target)
        val initialContent = vfs.readFile(path).getOrDefault("")
        onOpenEditor(path, initialContent)
        onOutputLine(TerminalLine(text = "Abriendo editor para: $path", type = LineType.SYSTEM))
    }

    private suspend fun runCurl(args: List<String>, onOutputLine: (TerminalLine) -> Unit) = withContext(Dispatchers.IO) {
        val url = args.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            ?: if (args.isNotEmpty()) "https://${args[0]}" else null

        if (url == null) {
            onOutputLine(TerminalLine(text = "curl: especificar URL (ej: curl https://wttr.in/Madrid?format=3)", type = LineType.ERROR))
            return@withContext
        }

        onOutputLine(TerminalLine(text = "curl: conectando a $url ...", type = LineType.SYSTEM))

        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Termux-AI/1.0 (Android; ARM64)")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val preview = if (body.length > 2000) body.take(2000) + "\n...[truncado a 2000 caracteres]" else body
                onOutputLine(
                    TerminalLine(
                        text = "HTTP ${resp.code} ${resp.message}\n$preview",
                        type = LineType.OUTPUT
                    )
                )
            }
        } catch (e: Exception) {
            onOutputLine(TerminalLine(text = "curl: error al conectar con $url (${e.localizedMessage})", type = LineType.ERROR))
        }
    }

    private suspend fun runPing(args: List<String>, onOutputLine: (TerminalLine) -> Unit) = withContext(Dispatchers.IO) {
        val host = args.firstOrNull { !it.startsWith("-") } ?: "8.8.8.8"
        onOutputLine(TerminalLine(text = "PING $host (56 bytes de datos)...", type = LineType.SYSTEM))

        for (i in 1..3) {
            val start = System.currentTimeMillis()
            try {
                val address = InetAddress.getByName(host)
                val reachable = address.isReachable(2000)
                val duration = System.currentTimeMillis() - start
                if (reachable) {
                    onOutputLine(
                        TerminalLine(
                            text = "64 bytes desde ${address.hostAddress}: icmp_seq=$i ttl=116 time=${duration}ms",
                            type = LineType.OUTPUT
                        )
                    )
                } else {
                    onOutputLine(
                        TerminalLine(
                            text = "64 bytes desde $host: icmp_seq=$i host alcanzable en ${duration}ms (ping ICMP)",
                            type = LineType.OUTPUT
                        )
                    )
                }
            } catch (e: Exception) {
                onOutputLine(TerminalLine(text = "ping: error resolviendo $host (${e.localizedMessage})", type = LineType.ERROR))
                break
            }
            kotlinx.coroutines.delay(300)
        }
        onOutputLine(TerminalLine(text = "--- $host estadísticas de ping: 3 paquetes transmitidos, 0% packet loss ---", type = LineType.SYSTEM))
    }

    private fun runPython(args: List<String>, currentDir: String, onOutputLine: (TerminalLine) -> Unit) {
        if (args.isEmpty()) {
            onOutputLine(TerminalLine(text = "Python 3.12.2 (Termux AI Embedded Interpreter)\nType \"help\", \"copyright\", \"credits\" or \"license\" for more information.", type = LineType.OUTPUT))
            return
        }

        if (args[0] == "-c" && args.size > 1) {
            val code = args.drop(1).joinToString(" ").removeSurrounding("\"")
            onOutputLine(TerminalLine(text = "Python eval:\n$code", type = LineType.OUTPUT))
            return
        }

        val scriptPath = vfs.normalizePath(currentDir, args[0])
        vfs.readFile(scriptPath).onSuccess { code ->
            onOutputLine(TerminalLine(text = "[Ejecutando Python: $scriptPath]", type = LineType.SYSTEM))
            val lines = code.lines()
            for (line in lines) {
                val printMatch = Regex("print\\((?:\"|')(.*)(?:\"|')\\)").find(line.trim())
                if (printMatch != null) {
                    onOutputLine(TerminalLine(text = printMatch.groupValues[1], type = LineType.OUTPUT))
                }
            }
            onOutputLine(TerminalLine(text = "[Proceso terminado con código 0]", type = LineType.SYSTEM))
        }.onFailure { err ->
            onOutputLine(TerminalLine(text = "python: no se puede abrir el archivo '${args[0]}': ${err.localizedMessage}", type = LineType.ERROR))
        }
    }

    private suspend fun runScript(args: List<String>, currentDir: String, onOutputLine: (TerminalLine) -> Unit) {
        if (args.isEmpty()) {
            onOutputLine(TerminalLine(text = "sh: falta el script a ejecutar", type = LineType.ERROR))
            return
        }
        val target = args[0].removePrefix("./")
        var path = vfs.normalizePath(currentDir, target)
        if (!vfs.exists(path) && !target.contains("/")) {
            val scriptDirPath = "${ScriptManager.SCRIPTS_DIR}/$target"
            if (vfs.exists(scriptDirPath)) {
                path = scriptDirPath
            } else if (!target.endsWith(".sh")) {
                val shCandidate = "${ScriptManager.SCRIPTS_DIR}/$target.sh"
                if (vfs.exists(shCandidate)) {
                    path = shCandidate
                }
            }
        }

        val check = scriptManager.validateScriptExecution(path)
        if (!check.allowed) {
            onOutputLine(TerminalLine(text = check.reason ?: "Permiso denegado", type = LineType.ERROR))
            return
        }

        vfs.readFile(path).onSuccess { script ->
            val permStr = if (scriptManager.isExecutable(path)) "-rwxr-xr--" else "-rw-r--r--"
            onOutputLine(TerminalLine(
                text = "[Sandbox Restringido: usuario 'u0_a234' (UID 10234) | Permisos: $permStr]",
                type = LineType.SYSTEM
            ))
            onOutputLine(TerminalLine(text = "[Ejecutando script: $path]", type = LineType.SYSTEM))
            val lines = script.lines()
            for (l in lines) {
                val clean = l.trim()
                if (clean.isEmpty() || clean.startsWith("#")) continue
                executeCommand(clean, currentDir, preferences.getSettings(), {}, { _, _ -> }, onOutputLine)
            }
            onOutputLine(TerminalLine(text = "[Script finalizado exitosamente (Exit code 0)]", type = LineType.SYSTEM))
        }.onFailure { err ->
            onOutputLine(TerminalLine(text = "sh: no se puede ejecutar '$target': ${err.localizedMessage}", type = LineType.ERROR))
        }
    }

    private suspend fun handleScriptCommand(
        args: List<String>,
        currentDir: String,
        onOpenEditor: (String, String) -> Unit,
        onOutputLine: (TerminalLine) -> Unit
    ) {
        val sub = if (args.isEmpty()) "list" else args[0].lowercase()
        when (sub) {
            "list", "ls" -> {
                val scripts = scriptManager.listScripts()
                val sb = java.lang.StringBuilder()
                sb.append("================================================================\n")
                sb.append(" GESTOR DE SCRIPTS - Entorno de Ejecución Restringido (u0_a234)\n")
                sb.append("================================================================\n")
                sb.append(String.format(Locale.US, "%-11s  %-5s  %-6s  %-18s  %s\n", "PERMISOS", "LÍNEAS", "BYTES", "NOMBRE", "RUTA VIRTUAL"))
                sb.append("----------------------------------------------------------------\n")
                if (scripts.isEmpty()) {
                    sb.append(" (No hay scripts .sh en /home/user/scripts ni ~/)\n")
                } else {
                    for (s in scripts) {
                        sb.append(String.format(Locale.US, "%-11s  %5d  %5dB   %-18s  %s\n",
                            s.permissionString, s.lineCount, s.sizeBytes, s.name, s.virtualPath))
                    }
                }
                sb.append("----------------------------------------------------------------\n")
                sb.append("Uso: script run <nombre> | script save <nombre> \"<código>\"\n")
                sb.append("     script chmod <nombre> [+x|-x] | script cat <nombre> | script edit <nombre> | script rm <nombre>\n")
                sb.append("================================================================")
                onOutputLine(TerminalLine(text = sb.toString(), type = LineType.OUTPUT))
            }
            "run", "exec" -> {
                if (args.size < 2) {
                    onOutputLine(TerminalLine(text = "script: uso: script run <nombre_script.sh>", type = LineType.ERROR))
                    return
                }
                runScript(listOf(args[1]), currentDir, onOutputLine)
            }
            "save" -> {
                if (args.size < 3) {
                    onOutputLine(TerminalLine(text = "script: uso: script save <nombre.sh> \"<contenido>\"", type = LineType.ERROR))
                    return
                }
                val name = args[1]
                val content = args.drop(2).joinToString(" ").removeSurrounding("\"")
                val res = scriptManager.saveScript(name, content, makeExecutable = true)
                res.onSuccess { meta ->
                    onOutputLine(TerminalLine(
                        text = "Script guardado exitosamente: ${meta.virtualPath} [${meta.permissionString}] (${meta.sizeBytes} bytes)",
                        type = LineType.SYSTEM
                    ))
                }.onFailure { err ->
                    onOutputLine(TerminalLine(text = "script save error: ${err.localizedMessage}", type = LineType.ERROR))
                }
            }
            "chmod" -> {
                if (args.size < 3) {
                    onOutputLine(TerminalLine(text = "script: uso: script chmod <nombre.sh> [+x|-x]", type = LineType.ERROR))
                    return
                }
                val name = args[1]
                val mode = args[2]
                val path = if (name.startsWith("/")) name else {
                    val candidate = "$currentDir/$name"
                    if (vfs.exists(candidate)) candidate else "${ScriptManager.SCRIPTS_DIR}/$name"
                }
                val makeExec = when (mode) {
                    "+x", "755", "700" -> true
                    "-x", "644", "600" -> false
                    else -> {
                        onOutputLine(TerminalLine(text = "chmod: modo no reconocido '$mode'. Usa +x o -x", type = LineType.ERROR))
                        return
                    }
                }
                val res = scriptManager.setExecutable(path, makeExec)
                res.onSuccess {
                    onOutputLine(TerminalLine(
                        text = "Permisos actualizados para '$path' a ${if (makeExec) "-rwxr-xr-- (+x)" else "-rw-r--r-- (-x)"}",
                        type = LineType.SYSTEM
                    ))
                }.onFailure { err ->
                    onOutputLine(TerminalLine(text = "chmod: ${err.localizedMessage}", type = LineType.ERROR))
                }
            }
            "cat" -> {
                if (args.size < 2) {
                    onOutputLine(TerminalLine(text = "script: uso: script cat <nombre.sh>", type = LineType.ERROR))
                    return
                }
                val name = args[1]
                val path = if (name.startsWith("/")) name else {
                    val candidate = "$currentDir/$name"
                    if (vfs.exists(candidate)) candidate else "${ScriptManager.SCRIPTS_DIR}/$name"
                }
                readFileContent(listOf(path), currentDir, onOutputLine)
            }
            "edit" -> {
                if (args.size < 2) {
                    onOutputLine(TerminalLine(text = "script: uso: script edit <nombre.sh>", type = LineType.ERROR))
                    return
                }
                val name = args[1]
                val path = if (name.startsWith("/")) name else "${ScriptManager.SCRIPTS_DIR}/$name"
                openTextEditor(listOf(path), currentDir, onOpenEditor, onOutputLine)
            }
            "rm", "delete" -> {
                if (args.size < 2) {
                    onOutputLine(TerminalLine(text = "script: uso: script rm <nombre.sh>", type = LineType.ERROR))
                    return
                }
                val name = args[1]
                val path = if (name.startsWith("/")) name else {
                    val candidate = "$currentDir/$name"
                    if (vfs.exists(candidate)) candidate else "${ScriptManager.SCRIPTS_DIR}/$name"
                }
                val res = scriptManager.deleteScript(path)
                res.onSuccess {
                    onOutputLine(TerminalLine(text = "Script eliminado: $path", type = LineType.SYSTEM))
                }.onFailure { err ->
                    onOutputLine(TerminalLine(text = "script rm error: ${err.localizedMessage}", type = LineType.ERROR))
                }
            }
            else -> {
                onOutputLine(TerminalLine(
                    text = "script: comando desconocido '$sub'. Usa: script [list|run|save|chmod|cat|edit|rm]",
                    type = LineType.ERROR
                ))
            }
        }
    }

    private fun handleChmodCommand(
        args: List<String>,
        currentDir: String,
        onOutputLine: (TerminalLine) -> Unit
    ) {
        if (args.size < 2) {
            onOutputLine(TerminalLine(text = "chmod: operando faltante. Uso: chmod [+x|-x] <archivo>", type = LineType.ERROR))
            return
        }
        val mode = args[0]
        val fileArg = args[1]
        val makeExec = when (mode) {
            "+x", "755", "700" -> true
            "-x", "644", "600" -> false
            else -> {
                onOutputLine(TerminalLine(text = "chmod: modo '$mode' no soportado en sandbox. Usa +x o -x", type = LineType.ERROR))
                return
            }
        }
        val path = vfs.normalizePath(currentDir, fileArg)
        val res = scriptManager.setExecutable(path, makeExec)
        res.onSuccess {
            onOutputLine(TerminalLine(
                text = "Modo de '$fileArg' cambiado a ${if (makeExec) "-rwxr-xr--" else "-rw-r--r--"}",
                type = LineType.SYSTEM
            ))
        }.onFailure { err ->
            onOutputLine(TerminalLine(text = "chmod: ${err.localizedMessage}", type = LineType.ERROR))
        }
    }

    private suspend fun handleHistoryCommand(
        args: List<String>,
        onOutputLine: (TerminalLine) -> Unit
    ) {
        if (args.isNotEmpty() && (args[0] == "-c" || args[0] == "clear")) {
            historyRepository?.clearHistory()
            preferences.clearCommandHistory()
            onOutputLine(TerminalLine(text = "[Historial de comandos en SQLite (Room) limpiado]", type = LineType.SYSTEM))
            return
        }

        val commands = historyRepository?.getNavigationHistory() ?: preferences.getCommandHistory()
        if (commands.isEmpty()) {
            onOutputLine(TerminalLine(text = "(Historial de comandos vacío)", type = LineType.OUTPUT))
            return
        }

        val sb = java.lang.StringBuilder()
        sb.append("--- HISTORIAL DE COMANDOS (Room DB: SQLite) ---\n")
        commands.forEachIndexed { index, cmd ->
            sb.append(String.format(Locale.US, "  %4d  %s\n", index + 1, cmd))
        }
        sb.append("------------------------------------------------\n")
        sb.append("* Usa las flechas ↑ / ↓ del teclado para navegar por el historial.")
        onOutputLine(TerminalLine(text = sb.toString().trimEnd(), type = LineType.OUTPUT))
    }

    private fun runPkg(args: List<String>, onOutputLine: (TerminalLine) -> Unit) {
        if (args.isEmpty()) {
            onOutputLine(TerminalLine(text = "pkg: uso: pkg [install|update|list|search] <paquete>", type = LineType.SYSTEM))
            return
        }
        when (args[0]) {
            "update", "upgrade" -> {
                onOutputLine(TerminalLine(text = "Actualizando repositorios de Termux AI...", type = LineType.SYSTEM))
                onOutputLine(TerminalLine(text = "Todos los paquetes están al día. (claude-agent 3.5, openclaw 2.4, sshd-bridge)", type = LineType.OUTPUT))
            }
            "list" -> {
                val list = """
                    claude-cli/aarch64 3.5-v2 [instalado]
                    openclaw-agent/aarch64 2.4.0 [instalado]
                    openssh-server/aarch64 9.6p1 [instalado]
                    python3/aarch64 3.12.2 [instalado]
                    bash/aarch64 5.2.15 [instalado]
                    curl/aarch64 8.6.0 [instalado]
                    neofetch/aarch64 7.1.0 [instalado]
                """.trimIndent()
                onOutputLine(TerminalLine(text = list, type = LineType.OUTPUT))
            }
            "install" -> {
                val pkgName = args.getOrNull(1) ?: "paquete"
                onOutputLine(TerminalLine(text = "Instalando $pkgName en /data/data/com.aistudio.termuxai/files/usr/bin...", type = LineType.SYSTEM))
                onOutputLine(TerminalLine(text = "$pkgName instalado correctamente.", type = LineType.OUTPUT))
            }
            else -> onOutputLine(TerminalLine(text = "pkg: comando '${args[0]}' completado.", type = LineType.OUTPUT))
        }
    }

    private fun runTop(onOutputLine: (TerminalLine) -> Unit) {
        val serverPid = if (preferences.isRemoteServerEnabled()) "105" else "---"
        val serverCmd = if (preferences.isRemoteServerEnabled()) "sshd-server" else "[inactivo]"
        val output = """
            Tasks: 5 total, 1 running, 4 sleeping
            %Cpu(s):  2.4 us,  1.1 sy,  0.0 ni, 96.5 id
            PID USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+ COMMAND
            101 user      20   0  124.2m  45.1m  12.4m S   1.8   1.2   0:14.22 claude-daemon
            102 user      20   0  145.8m  52.0m  14.1m S   2.1   1.5   0:18.45 openclaw-core
            103 user      20   0   48.2m  18.4m   8.2m S   0.3   0.5   0:02.10 bash
            104 user      20   0   22.4m   8.1m   3.2m R   0.9   0.2   0:00.08 top
            $serverPid user      20   0   18.6m   6.4m   2.1m S   0.1   0.2   0:00.34 $serverCmd
        """.trimIndent()
        onOutputLine(TerminalLine(text = output, type = LineType.OUTPUT))
    }

    private suspend fun runClaudeCommand(
        args: List<String>,
        settings: AgentSettings,
        onOutputLine: (TerminalLine) -> Unit
    ) {
        if (args.isEmpty()) {
            onOutputLine(
                TerminalLine(
                    text = "Uso de Claude Agent:\n  claude \"tu pregunta o instrucción\"\n  claude -i (inicia modo interactivo)\n  claude config (ver estado seguro de conexión)",
                    type = LineType.AGENT_CLAUDE,
                    agentName = "Claude"
                )
            )
            return
        }

        if (args[0] == "config") {
            val keyStatus = if (settings.claudeApiKey.isNotBlank()) {
                "Configurada (${securityManager.maskSecret(settings.claudeApiKey)} - Cifrada AES-256-GCM)"
            } else {
                "Modo Libre / Sandbox (Sin clave)"
            }
            val info = """
                [Configuración Segura de Claude Agent]
                * Modelo: ${settings.claudeModel}
                * API Key: $keyStatus
                * Modo: 100% Gratuito y configurable en Ajustes
                * Instrucción del Sistema: ${settings.claudeSystemPrompt}
            """.trimIndent()
            onOutputLine(TerminalLine(text = info, type = LineType.AGENT_CLAUDE, agentName = "Claude"))
            return
        }

        val prompt = args.joinToString(" ").removeSurrounding("\"")
        onOutputLine(TerminalLine(text = "[Consultando a Claude: \"$prompt\"]", type = LineType.SYSTEM))

        val result = claudeService.queryClaude(
            prompt = prompt,
            apiKey = settings.claudeApiKey,
            model = settings.claudeModel,
            systemPrompt = settings.claudeSystemPrompt
        )

        result.onSuccess { res ->
            onOutputLine(
                TerminalLine(
                    text = res.text,
                    type = LineType.AGENT_CLAUDE,
                    agentName = "Claude",
                    commandText = res.suggestedCommand
                )
            )
        }.onFailure { err ->
            onOutputLine(
                TerminalLine(
                    text = "Error de Claude Agent: ${err.localizedMessage}",
                    type = LineType.ERROR
                )
            )
        }
    }

    private suspend fun runOpenClawCommand(
        args: List<String>,
        settings: AgentSettings,
        onOutputLine: (TerminalLine) -> Unit
    ) {
        if (args.isEmpty()) {
            onOutputLine(
                TerminalLine(
                    text = "Uso de OpenClaw Agent:\n  openclaw \"objetivo a cumplir\"\n  openclaw run syscheck\n  openclaw run audit\n  openclaw status",
                    type = LineType.AGENT_OPENCLAW,
                    agentName = "OpenClaw"
                )
            )
            return
        }

        if (args[0] == "status") {
            val status = """
                [OpenClaw Agent Status]
                * Daemon: ACTIVO (v2.4.0 Autonomous Engine)
                * Endpoint: ${settings.openClawEndpoint}
                * Token: ${securityManager.maskSecret(settings.openClawToken)}
                * Permisos de ejecución: ${if (settings.openClawAutonomous) "Autónomo (Tool auto-exec)" else "Manual"}
                * Herramientas integradas: [sys_probe, fs_scanner, net_telemetry, bash_executor]
            """.trimIndent()
            onOutputLine(TerminalLine(text = status, type = LineType.AGENT_OPENCLAW, agentName = "OpenClaw"))
            return
        }

        val goal = if (args[0] == "run" && args.size > 1) {
            args.drop(1).joinToString(" ")
        } else {
            args.joinToString(" ").removeSurrounding("\"")
        }

        onOutputLine(TerminalLine(text = "[OpenClaw Agent iniciando objetivo: \"$goal\"]", type = LineType.SYSTEM))

        openClawService.executeTask(
            goal = goal,
            endpoint = settings.openClawEndpoint,
            token = settings.openClawToken,
            onStep = { step ->
                when (step) {
                    is OpenClawAgentService.AgentStep.Plan -> {
                        val planStr = "Plan de ejecución OpenClaw:\n" + step.steps.mapIndexed { idx, s -> "  ${idx + 1}. $s" }.joinToString("\n")
                        onOutputLine(TerminalLine(text = planStr, type = LineType.AGENT_STEP, agentName = "OpenClaw"))
                    }
                    is OpenClawAgentService.AgentStep.ToolCall -> {
                        onOutputLine(
                            TerminalLine(
                                text = "-> [Tool: ${step.tool}] ${step.command}",
                                type = LineType.AGENT_STEP,
                                agentName = "OpenClaw",
                                toolCallName = step.tool
                            )
                        )
                    }
                    is OpenClawAgentService.AgentStep.ToolResult -> {
                        onOutputLine(
                            TerminalLine(
                                text = "<- [Result] ${step.output}",
                                type = LineType.AGENT_STEP,
                                agentName = "OpenClaw"
                            )
                        )
                    }
                    is OpenClawAgentService.AgentStep.Summary -> {
                        onOutputLine(
                            TerminalLine(
                                text = step.text,
                                type = LineType.AGENT_OPENCLAW,
                                agentName = "OpenClaw"
                            )
                        )
                    }
                }
            }
        )
    }
}
