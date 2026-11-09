package com.example.controller

import com.example.fs.VirtualFileSystem
import com.example.model.LineType
import com.example.model.TerminalLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log severity levels for internal terminal and security audit tracking.
 */
enum class TerminalLogLevel {
    DEBUG,
    INFO,
    COMMAND,
    OUTPUT,
    WARNING,
    ERROR,
    AUDIT_RED_TEAM,
    AUDIT_BLUE_TEAM
}

/**
 * Structured log record.
 */
data class TerminalLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: TerminalLogLevel,
    val tag: String,
    val message: String,
    val sessionId: String = "main"
) {
    fun toFormattedString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return "[${sdf.format(Date(timestamp))}] [${level.name}] [$tag] $message"
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("level", level.name)
            put("tag", tag)
            put("message", message)
            put("sessionId", sessionId)
        }
    }
}

/**
 * Descriptor for commands supported by the terminal controller.
 */
data class CommandDescriptor(
    val name: String,
    val description: String,
    val usage: String,
    val category: String,
    val subcommands: List<String> = emptyList(),
    val flags: List<String> = emptyList()
)

/**
 * Result returned by the autocomplete engine.
 */
data class AutocompleteResult(
    val originalInput: String,
    val suggestions: List<String>,
    val completedInput: String? = null,
    val matchedPrefix: String = ""
)

/**
 * TerminalController coordinates shell command dispatching, contextual autocompletion,
 * and structured security/telemetry log management within the Android UI.
 */
class TerminalController(
    private val vfs: VirtualFileSystem
) {
    private val maxLogCapacity = 1000
    private val _logs = MutableStateFlow<List<TerminalLogEntry>>(emptyList())
    val logs: StateFlow<List<TerminalLogEntry>> = _logs.asStateFlow()

    private val commandRegistry = mutableMapOf<String, CommandDescriptor>()

    init {
        registerBuiltInCommands()
        log(
            level = TerminalLogLevel.INFO,
            tag = "INIT",
            message = "TerminalController inicializado con soporte de autocompletado y logging criptográfico."
        )
    }

    private fun registerBuiltInCommands() {
        val list = listOf(
            CommandDescriptor("help", "Muestra la guía completa de comandos disponibles", "help [comando]", "Sistema"),
            CommandDescriptor("clear", "Limpia el buffer de la pantalla de terminal", "clear", "Sistema"),
            CommandDescriptor("neofetch", "Muestra información visual del hardware y sistema", "neofetch", "Sistema"),
            CommandDescriptor("ls", "Lista archivos y carpetas del directorio actual", "ls [-la] [directorio]", "Archivos", flags = listOf("-l", "-a", "-la")),
            CommandDescriptor("cd", "Cambia el directorio de trabajo actual", "cd <directorio>", "Archivos"),
            CommandDescriptor("pwd", "Imprime la ruta del directorio de trabajo actual", "pwd", "Archivos"),
            CommandDescriptor("cat", "Muestra el contenido de uno o más archivos", "cat <archivo>", "Archivos"),
            CommandDescriptor("echo", "Imprime texto en pantalla o redirige con > o >>", "echo \"texto\" [> archivo]", "Texto"),
            CommandDescriptor("mkdir", "Crea una nueva carpeta en el sistema virtual", "mkdir <carpeta>", "Archivos"),
            CommandDescriptor("rm", "Elimina un archivo o directorio de forma segura", "rm [-r] <archivo>", "Archivos", flags = listOf("-r", "-f")),
            CommandDescriptor("touch", "Crea un archivo vacío o actualiza su timestamp", "touch <archivo>", "Archivos"),
            CommandDescriptor("nano", "Abre el editor de texto visual integrado", "nano <archivo>", "Archivos"),
            CommandDescriptor("curl", "Realiza peticiones HTTP y muestra respuestas de red", "curl <url>", "Red"),
            CommandDescriptor("ping", "Prueba conectividad y latencia hacia un host de red", "ping <host>", "Red"),
            CommandDescriptor("python", "Intérprete básico embebido de scripts Python", "python <script.py> | -c <codigo>", "Ejecución", flags = listOf("-c")),
            CommandDescriptor(
                name = "script",
                description = "Gestor seguro de scripts .sh en sandbox restringido",
                usage = "script [list|run|save|chmod|cat|edit|rm] [argumentos]",
                category = "Seguridad",
                subcommands = listOf("list", "run", "save", "chmod", "cat", "edit", "rm")
            ),
            CommandDescriptor(
                name = "scripts",
                description = "Atajo para listar y gestionar scripts .sh en ~/scripts",
                usage = "scripts",
                category = "Seguridad",
                subcommands = listOf("list", "run")
            ),
            CommandDescriptor("chmod", "Modifica permisos de ejecución en scripts (+x / -x)", "chmod [+x|-x] <archivo>", "Seguridad", flags = listOf("+x", "-x", "755", "644")),
            CommandDescriptor("history", "Muestra o limpia el historial persistente de comandos", "history [-c|clear]", "Sistema", flags = listOf("-c", "clear")),
            CommandDescriptor("whoami", "Muestra el usuario y contexto actual no privilegiado", "whoami", "Sistema"),
            CommandDescriptor("uname", "Muestra especificaciones del kernel y arquitectura", "uname [-a]", "Sistema", flags = listOf("-a")),
            CommandDescriptor("ip", "Detecta y muestra IPs de interfaces de red activas", "ip", "Red"),
            CommandDescriptor("ifconfig", "Alias para detección de interfaces de red", "ifconfig", "Red"),
            CommandDescriptor(
                name = "sshd",
                description = "Controla el servidor de acceso remoto SSH seguro",
                usage = "sshd [start|stop|status] [puerto]",
                category = "Red",
                subcommands = listOf("start", "stop", "status")
            ),
            CommandDescriptor(
                name = "telnetd",
                description = "Controla la consola remota Telnet",
                usage = "telnetd [start|stop] [puerto]",
                category = "Red",
                subcommands = listOf("start", "stop")
            ),
            CommandDescriptor("passwd", "Configura credenciales cifradas con PBKDF2 y salt", "passwd [clave]", "Seguridad"),
            CommandDescriptor(
                name = "agent",
                description = "Interfaz programática API para agentes de IA",
                usage = "agent api [claude|openclaw] [prompt]",
                category = "Agentes",
                subcommands = listOf("api", "config", "status")
            ),
            CommandDescriptor("claude", "Interactúa directamente con el agente inteligente Claude", "claude <mensaje> | -i", "Agentes", flags = listOf("-i", "config")),
            CommandDescriptor("openclaw", "Ejecuta agente autónomo con workflows y auditorías", "openclaw <objetivo> | run <tarea>", "Agentes", subcommands = listOf("run syscheck", "run audit")),
            CommandDescriptor(
                name = "logs",
                description = "Visualiza o exporta logs estructurados de telemetría y seguridad",
                usage = "logs [clear|export|filter <nivel>]",
                category = "Telemetría",
                subcommands = listOf("clear", "export", "filter")
            )
        )

        for (desc in list) {
            commandRegistry[desc.name] = desc
        }
    }

    /**
     * Contextual autocomplete engine: analyzes the current line buffer and cursor context
     * to suggest commands, subcommands, flags, and filesystem paths.
     */
    fun computeCompletions(currentInput: String, currentDir: String): AutocompleteResult {
        val trimmedLeading = currentInput.trimStart()
        if (trimmedLeading.isEmpty()) {
            return AutocompleteResult(
                originalInput = currentInput,
                suggestions = commandRegistry.keys.sorted(),
                matchedPrefix = ""
            )
        }

        val words = trimmedLeading.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val isTrailingSpace = currentInput.endsWith(" ")

        // Case 1: Typing primary command name
        if (words.size == 1 && !isTrailingSpace) {
            val prefix = words[0].lowercase()
            val matches = commandRegistry.keys
                .filter { it.startsWith(prefix, ignoreCase = true) }
                .sorted()

            val completed = if (matches.size == 1) "${matches[0]} " else null
            return AutocompleteResult(
                originalInput = currentInput,
                suggestions = matches,
                completedInput = completed,
                matchedPrefix = prefix
            )
        }

        if (words.isEmpty()) {
            return AutocompleteResult(
                originalInput = currentInput,
                suggestions = commandRegistry.keys.sorted(),
                matchedPrefix = ""
            )
        }

        // Case 2: Command already entered, typing subcommands or flags
        val primaryCmd = words[0].lowercase()
        val descriptor = commandRegistry[primaryCmd]
        val currentArgIndex = if (isTrailingSpace) words.size else words.size - 1
        val lastToken = if (isTrailingSpace) "" else words.last()

        if (descriptor != null && currentArgIndex == 1) {
            val possibleSubs = descriptor.subcommands + descriptor.flags
            if (possibleSubs.isNotEmpty()) {
                val matches = possibleSubs.filter { it.startsWith(lastToken, ignoreCase = true) }
                if (matches.isNotEmpty()) {
                    val completed = if (matches.size == 1) {
                        val base = currentInput.substring(0, currentInput.length - lastToken.length)
                        "$base${matches[0]} "
                    } else null

                    return AutocompleteResult(
                        originalInput = currentInput,
                        suggestions = matches,
                        completedInput = completed,
                        matchedPrefix = lastToken
                    )
                }
            }
        }

        // Case 3: Filesystem path completion
        val fileSuggestions = getFilesystemSuggestions(lastToken, currentDir)
        if (fileSuggestions.isNotEmpty()) {
            val completed = if (fileSuggestions.size == 1) {
                val suggestion = fileSuggestions[0]
                val base = currentInput.substring(0, currentInput.length - lastToken.length)
                "$base$suggestion"
            } else null

            return AutocompleteResult(
                originalInput = currentInput,
                suggestions = fileSuggestions,
                completedInput = completed,
                matchedPrefix = lastToken
            )
        }

        return AutocompleteResult(
            originalInput = currentInput,
            suggestions = emptyList(),
            matchedPrefix = lastToken
        )
    }

    private fun getFilesystemSuggestions(partialPath: String, currentDir: String): List<String> {
        val searchDir: String
        val filePrefix: String

        if (partialPath.contains("/")) {
            val lastSlash = partialPath.lastIndexOf('/')
            val dirPart = partialPath.substring(0, lastSlash)
            filePrefix = partialPath.substring(lastSlash + 1)
            searchDir = vfs.normalizePath(currentDir, dirPart)
        } else {
            searchDir = currentDir
            filePrefix = partialPath
        }

        val entries = vfs.listDirectory(searchDir).getOrNull() ?: return emptyList()
        return entries
            .filter { it.name.startsWith(filePrefix, ignoreCase = true) }
            .map { entry ->
                val fullSuffix = if (entry.isDirectory) "${entry.name}/" else "${entry.name} "
                if (partialPath.contains("/")) {
                    val dirPrefix = partialPath.substring(0, partialPath.lastIndexOf('/') + 1)
                    "$dirPrefix$fullSuffix"
                } else {
                    fullSuffix
                }
            }
            .sorted()
    }

    // --- LOG MANAGEMENT & SECURITY AUDITING ---

    /**
     * Appends a log entry to the circular buffer.
     */
    fun log(
        level: TerminalLogLevel,
        tag: String,
        message: String,
        sessionId: String = "main"
    ) {
        val entry = TerminalLogEntry(
            level = level,
            tag = tag,
            message = message,
            sessionId = sessionId
        )

        _logs.update { current ->
            val updated = current + entry
            if (updated.size > maxLogCapacity) {
                updated.takeLast(maxLogCapacity)
            } else {
                updated
            }
        }
    }

    /**
     * Records a Red Team security audit event (e.g. offensive simulation, pipe payload execution).
     */
    fun logRedTeamEvent(tag: String, payload: String, sessionId: String = "red_team") {
        log(
            level = TerminalLogLevel.AUDIT_RED_TEAM,
            tag = "RedTeam:$tag",
            message = "[#RedTeam] Simulación de payload: $payload",
            sessionId = sessionId
        )
    }

    /**
     * Records a Blue Team defensive event (e.g. blocked root escalation, sandbox breach attempt).
     */
    fun logBlueTeamEvent(tag: String, detection: String, sessionId: String = "blue_team") {
        log(
            level = TerminalLogLevel.AUDIT_BLUE_TEAM,
            tag = "BlueTeam:$tag",
            message = "[#BlueTeam] Detección defensiva: $detection",
            sessionId = sessionId
        )
    }

    /**
     * Clears all buffered logs.
     */
    fun clearLogs() {
        _logs.value = emptyList()
        log(TerminalLogLevel.INFO, "LOGS", "Buffer de telemetría y logs vaciado.")
    }

    /**
     * Exports logs in formatted JSON representation.
     */
    fun exportLogsAsJson(): String {
        val array = JSONArray()
        for (log in _logs.value) {
            array.put(log.toJsonObject())
        }
        return array.toString(2)
    }

    /**
     * Filters logs by level.
     */
    fun getLogsByLevel(level: TerminalLogLevel): List<TerminalLogEntry> {
        return _logs.value.filter { it.level == level }
    }

    /**
     * Returns all registered command descriptors.
     */
    fun getRegisteredCommands(): List<CommandDescriptor> {
        return commandRegistry.values.sortedBy { it.name }
    }
}
