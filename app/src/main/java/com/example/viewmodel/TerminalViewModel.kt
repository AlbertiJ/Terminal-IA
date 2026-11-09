package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.ClaudeAgentService
import com.example.agent.OpenClawAgentService
import com.example.data.TerminalPreferences
import com.example.fs.VirtualFileSystem
import com.example.engine.TerminalEngine
import com.example.model.AgentSettings
import com.example.model.LineType
import com.example.model.SessionType
import com.example.model.TerminalLine
import com.example.model.TerminalSession
import com.example.server.RemoteConsoleServer
import com.example.server.WebTerminalServer
import com.example.controller.TerminalController
import com.example.controller.TerminalLogLevel
import com.example.data.db.CommandHistoryRepository
import com.example.data.db.TermuxDatabase
import com.example.script.ScriptManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TerminalUiState(
    val sessions: List<TerminalSession> = emptyList(),
    val activeSessionId: String = "",
    val sessionLines: Map<String, List<TerminalLine>> = emptyMap(),
    val input: String = "",
    val settings: AgentSettings = AgentSettings(),
    val isExecuting: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showQuickActions: Boolean = false,
    val showRemoteAccessDialog: Boolean = false,
    val showFileManagerDialog: Boolean = false,
    val isSplitScreenMode: Boolean = false,
    val isRemoteServerRunning: Boolean = false,
    val remoteServerPort: Int = 8022,
    val isWebTerminalRunning: Boolean = false,
    val webTerminalPort: Int = 8080,
    val deviceIps: List<String> = emptyList(),
    val hasRemoteCredentials: Boolean = false,
    val remoteUsername: String = "user",
    val showScriptManagerDialog: Boolean = false,
    val showLogsDialog: Boolean = false,
    val autocompleteSuggestions: List<String> = emptyList(),
    val editorState: EditorState? = null
)

data class EditorState(
    val filePath: String,
    val content: String
)

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = TerminalPreferences(application)
    val vfs = VirtualFileSystem(application)
    private val claudeService = ClaudeAgentService()
    private val openClawService = OpenClawAgentService()

    private val database = TermuxDatabase.getDatabase(application)
    val historyRepository = CommandHistoryRepository(database.commandHistoryDao())
    val scriptManager: ScriptManager get() = engine.scriptManager
    val terminalController = TerminalController(vfs)

    private lateinit var remoteServer: RemoteConsoleServer
    private lateinit var webServer: WebTerminalServer
    private lateinit var engine: TerminalEngine

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var historyIndex = -1
    private var commandHistory: List<String> = emptyList()

    init {
        engine = TerminalEngine(
            context = application,
            vfs = vfs,
            claudeService = claudeService,
            openClawService = openClawService,
            preferences = preferences,
            historyRepository = historyRepository,
            onServerAction = { action, port ->
                when (action) {
                    "start" -> {
                        startRemoteServer(port)
                        startWebTerminal(8080)
                    }
                    "stop" -> {
                        stopRemoteServer()
                        stopWebTerminal()
                    }
                }
            }
        )

        remoteServer = RemoteConsoleServer(
            context = application,
            preferences = preferences,
            engine = engine,
            onLogMessage = { text, lineType ->
                val activeId = _uiState.value.activeSessionId
                appendLine(activeId, TerminalLine(text = text, type = lineType))
            }
        )

        webServer = WebTerminalServer(
            context = application,
            preferences = preferences,
            engine = engine,
            onLogMessage = { text, lineType ->
                val activeId = _uiState.value.activeSessionId
                appendLine(activeId, TerminalLine(text = text, type = lineType))
            }
        )

        val loadedSettings = preferences.getSettings()
        viewModelScope.launch {
            val dbHistory = historyRepository.getNavigationHistory()
            if (dbHistory.isNotEmpty()) {
                commandHistory = dbHistory
            } else {
                commandHistory = preferences.getCommandHistory()
                for (cmd in commandHistory) {
                    historyRepository.recordCommand(cmd)
                }
            }
        }

        val mainSession = TerminalSession(
            title = "1: bash",
            type = SessionType.SHELL,
            currentDirectory = "/home/user"
        )
        val claudeSession = TerminalSession(
            title = "2: claude",
            type = SessionType.CLAUDE,
            currentDirectory = "/home/user"
        )
        val openClawSession = TerminalSession(
            title = "3: openclaw",
            type = SessionType.OPENCLAW,
            currentDirectory = "/home/user"
        )

        val initialLines = engine.getInitialWelcomeLines()
        val claudeIntro = listOf(
            TerminalLine(
                text = "Sesión de Claude Agent iniciada.\nEscribe directamente aquí tus dudas, código o tareas.",
                type = LineType.AGENT_CLAUDE,
                agentName = "Claude"
            )
        )
        val openClawIntro = listOf(
            TerminalLine(
                text = "Sesión de OpenClaw Agent iniciada.\nIngresa objetivos o workflows de automatización autónoma.",
                type = LineType.AGENT_OPENCLAW,
                agentName = "OpenClaw"
            )
        )

        val ips = remoteServer.getDeviceIpAddresses()
        val port = preferences.getRemotePort()

        _uiState.value = TerminalUiState(
            sessions = listOf(mainSession, claudeSession, openClawSession),
            activeSessionId = mainSession.id,
            sessionLines = mapOf(
                mainSession.id to initialLines,
                claudeSession.id to claudeIntro,
                openClawSession.id to openClawIntro
            ),
            settings = loadedSettings,
            isRemoteServerRunning = preferences.isRemoteServerEnabled(),
            remoteServerPort = port,
            isWebTerminalRunning = preferences.isRemoteServerEnabled(),
            webTerminalPort = 8080,
            deviceIps = ips,
            hasRemoteCredentials = preferences.hasRemoteCredentials(),
            remoteUsername = preferences.getRemoteUsername()
        )

        if (preferences.isRemoteServerEnabled()) {
            remoteServer.start(port)
            webServer.start(8080)
        }
    }

    fun onInputChange(newInput: String) {
        val sessionId = _uiState.value.activeSessionId
        val session = _uiState.value.sessions.firstOrNull { it.id == sessionId }
        val dir = session?.currentDirectory ?: "/home/user"
        val comp = if (newInput.isNotBlank()) terminalController.computeCompletions(newInput, dir) else null
        _uiState.update {
            it.copy(
                input = newInput,
                autocompleteSuggestions = if (comp != null && comp.suggestions.size in 1..8) comp.suggestions else emptyList()
            )
        }
        historyIndex = -1
    }

    fun applySuggestion(suggestion: String) {
        val current = _uiState.value.input
        val sessionId = _uiState.value.activeSessionId
        val session = _uiState.value.sessions.firstOrNull { it.id == sessionId }
        val dir = session?.currentDirectory ?: "/home/user"
        val comp = terminalController.computeCompletions(current, dir)
        val lastToken = comp.matchedPrefix
        val base = current.substring(0, current.length - lastToken.length)
        onInputChange("$base$suggestion")
    }

    fun selectSession(sessionId: String) {
        vibrateKey()
        _uiState.update { it.copy(activeSessionId = sessionId) }
        historyIndex = -1
    }

    fun createSession(type: SessionType) {
        vibrateKey()
        val count = _uiState.value.sessions.size + 1
        val name = when (type) {
            SessionType.SHELL -> "$count: bash"
            SessionType.CLAUDE -> "$count: claude"
            SessionType.OPENCLAW -> "$count: openclaw"
        }
        val newSession = TerminalSession(title = name, type = type)
        val introLines = when (type) {
            SessionType.SHELL -> listOf(TerminalLine(text = "Nueva sesión de shell creada.", type = LineType.SYSTEM))
            SessionType.CLAUDE -> listOf(TerminalLine(text = "Sesión Claude lista.", type = LineType.AGENT_CLAUDE, agentName = "Claude"))
            SessionType.OPENCLAW -> listOf(TerminalLine(text = "Sesión OpenClaw lista.", type = LineType.AGENT_OPENCLAW, agentName = "OpenClaw"))
        }

        _uiState.update { state ->
            state.copy(
                sessions = state.sessions + newSession,
                activeSessionId = newSession.id,
                sessionLines = state.sessionLines + (newSession.id to introLines)
            )
        }
    }

    fun closeSession(sessionId: String) {
        val state = _uiState.value
        if (state.sessions.size <= 1) return
        val remaining = state.sessions.filter { it.id != sessionId }
        val newActive = if (state.activeSessionId == sessionId) remaining.first().id else state.activeSessionId
        _uiState.update {
            it.copy(
                sessions = remaining,
                activeSessionId = newActive,
                sessionLines = it.sessionLines - sessionId
            )
        }
    }

    fun executeCurrentCommand() {
        val rawInput = _uiState.value.input
        if (rawInput.isBlank()) return

        vibrateKey()
        val sessionId = _uiState.value.activeSessionId
        val session = _uiState.value.sessions.firstOrNull { it.id == sessionId } ?: return

        val commandToRun = when (session.type) {
            SessionType.CLAUDE -> if (!rawInput.trim().startsWith("claude")) "claude \"$rawInput\"" else rawInput
            SessionType.OPENCLAW -> if (!rawInput.trim().startsWith("openclaw")) "openclaw \"$rawInput\"" else rawInput
            SessionType.SHELL -> rawInput
        }

        val trimmedCmd = rawInput.trim()
        preferences.appendCommandToHistory(trimmedCmd)
        historyIndex = -1

        terminalController.log(
            level = TerminalLogLevel.COMMAND,
            tag = "EXEC",
            message = "$trimmedCmd (dir: ${session.currentDirectory})",
            sessionId = sessionId
        )

        viewModelScope.launch {
            historyRepository.recordCommand(
                command = trimmedCmd,
                sessionId = sessionId,
                directory = session.currentDirectory
            )
            commandHistory = historyRepository.getNavigationHistory()
        }

        val promptHeader = "${preferences.getRemoteUsername()}@termux:${session.currentDirectory}$ $rawInput"
        appendLine(sessionId, TerminalLine(text = promptHeader, type = LineType.COMMAND))
        _uiState.update { it.copy(input = "", isExecuting = true) }

        if (commandToRun.trim().lowercase() in listOf("clear", "cls")) {
            _uiState.update { state ->
                val lines = state.sessionLines.toMutableMap()
                lines[sessionId] = emptyList()
                state.copy(sessionLines = lines, isExecuting = false)
            }
            return
        }

        viewModelScope.launch {
            engine.executeCommand(
                input = commandToRun,
                currentDir = session.currentDirectory,
                settings = _uiState.value.settings,
                onDirectoryChange = { newDir ->
                    _uiState.update { state ->
                        val updatedSessions = state.sessions.map { s ->
                            if (s.id == sessionId) s.copy(currentDirectory = newDir) else s
                        }
                        state.copy(sessions = updatedSessions)
                    }
                },
                onOpenEditor = { path, content ->
                    _uiState.update { it.copy(editorState = EditorState(path, content)) }
                },
                onOutputLine = { line ->
                    appendLine(sessionId, line)
                }
            )
            _uiState.update { it.copy(isExecuting = false) }
        }
    }

    fun onExtraKeyPressed(key: String) {
        vibrateKey()
        when (key) {
            "TAB" -> handleTabCompletion()
            "ESC" -> onInputChange("")
            "CTRL" -> onInputChange(_uiState.value.input + "^C")
            "ALT" -> onInputChange(_uiState.value.input + " ")
            "↑" -> navigateHistory(-1)
            "↓" -> navigateHistory(1)
            "|" -> onInputChange(_uiState.value.input + " | ")
            "/" -> onInputChange(_uiState.value.input + "/")
            "-" -> onInputChange(_uiState.value.input + "-")
            "~" -> onInputChange(_uiState.value.input + "~")
            "\"" -> onInputChange(_uiState.value.input + "\"")
            "$" -> onInputChange(_uiState.value.input + "$")
            "clear" -> {
                val sessionId = _uiState.value.activeSessionId
                _uiState.update { state ->
                    val lines = state.sessionLines.toMutableMap()
                    lines[sessionId] = emptyList()
                    state.copy(sessionLines = lines)
                }
            }
            "claude" -> onInputChange("claude ")
            "openclaw" -> onInputChange("openclaw ")
            "neofetch" -> onInputChange("neofetch")
            "ip" -> {
                onInputChange("ip")
                executeCurrentCommand()
            }
            "ssh" -> openRemoteAccessDialog()
            "scripts" -> openScriptManager()
            "history" -> {
                onInputChange("history")
                executeCurrentCommand()
            }
            "logs" -> openLogsDialog()
        }
    }

    private fun handleTabCompletion() {
        val current = _uiState.value.input
        if (current.isBlank()) return

        val sessionId = _uiState.value.activeSessionId
        val session = _uiState.value.sessions.firstOrNull { it.id == sessionId }
        val dir = session?.currentDirectory ?: "/home/user"

        val result = terminalController.computeCompletions(current, dir)
        if (result.completedInput != null) {
            onInputChange(result.completedInput)
        } else if (result.suggestions.size == 1) {
            val single = result.suggestions[0]
            val lastToken = result.matchedPrefix
            val base = current.substring(0, current.length - lastToken.length)
            onInputChange("$base$single")
        } else if (result.suggestions.isNotEmpty()) {
            val suggestionsLine = result.suggestions.joinToString("   ")
            appendLine(sessionId, TerminalLine(text = suggestionsLine, type = LineType.SYSTEM))
        }
    }

    private fun navigateHistory(direction: Int) {
        if (commandHistory.isEmpty()) return

        if (direction < 0) {
            if (historyIndex == -1) {
                historyIndex = commandHistory.size - 1
            } else if (historyIndex > 0) {
                historyIndex--
            }
            _uiState.update { it.copy(input = commandHistory[historyIndex]) }
        } else {
            if (historyIndex != -1) {
                if (historyIndex < commandHistory.size - 1) {
                    historyIndex++
                    _uiState.update { it.copy(input = commandHistory[historyIndex]) }
                } else {
                    historyIndex = -1
                    _uiState.update { it.copy(input = "") }
                }
            }
        }
    }

    private fun appendLine(sessionId: String, line: TerminalLine) {
        _uiState.update { state ->
            val existing = state.sessionLines[sessionId] ?: emptyList()
            val updated = existing + line
            state.copy(sessionLines = state.sessionLines + (sessionId to updated))
        }
    }

    // --- REMOTE SERVERS (SSH / TELNET & WEB TERMINAL) ---

    fun startRemoteServer(port: Int = _uiState.value.remoteServerPort) {
        remoteServer.start(port)
        _uiState.update {
            it.copy(
                isRemoteServerRunning = true,
                remoteServerPort = port,
                deviceIps = remoteServer.getDeviceIpAddresses()
            )
        }
    }

    fun stopRemoteServer() {
        remoteServer.stop()
        _uiState.update { it.copy(isRemoteServerRunning = false) }
    }

    fun startWebTerminal(port: Int = 8080) {
        webServer.start(port)
        _uiState.update {
            it.copy(
                isWebTerminalRunning = true,
                webTerminalPort = port,
                deviceIps = remoteServer.getDeviceIpAddresses()
            )
        }
    }

    fun stopWebTerminal() {
        webServer.stop()
        _uiState.update { it.copy(isWebTerminalRunning = false) }
    }

    fun toggleRemoteServer(enabled: Boolean, port: Int) {
        vibrateKey()
        if (enabled) {
            startRemoteServer(port)
        } else {
            stopRemoteServer()
        }
    }

    fun toggleWebTerminal(enabled: Boolean, port: Int) {
        vibrateKey()
        if (enabled) {
            startWebTerminal(port)
        } else {
            stopWebTerminal()
        }
    }

    fun toggleSplitScreen() {
        vibrateKey()
        _uiState.update { it.copy(isSplitScreenMode = !it.isSplitScreenMode) }
    }

    fun openFileManager() {
        vibrateKey()
        _uiState.update { it.copy(showFileManagerDialog = true) }
    }

    fun closeFileManager() {
        _uiState.update { it.copy(showFileManagerDialog = false) }
    }

    fun openScriptManager() {
        vibrateKey()
        _uiState.update { it.copy(showScriptManagerDialog = true) }
    }

    fun closeScriptManager() {
        _uiState.update { it.copy(showScriptManagerDialog = false) }
    }

    fun openLogsDialog() {
        vibrateKey()
        _uiState.update { it.copy(showLogsDialog = true) }
    }

    fun closeLogsDialog() {
        _uiState.update { it.copy(showLogsDialog = false) }
    }

    fun openEditorForPath(path: String, content: String) {
        _uiState.update { it.copy(editorState = EditorState(path, content)) }
    }

    fun openRemoteAccessDialog() {
        vibrateKey()
        val ips = remoteServer.getDeviceIpAddresses()
        _uiState.update {
            it.copy(
                showRemoteAccessDialog = true,
                deviceIps = ips,
                hasRemoteCredentials = preferences.hasRemoteCredentials(),
                remoteUsername = preferences.getRemoteUsername()
            )
        }
    }

    fun closeRemoteAccessDialog() {
        _uiState.update { it.copy(showRemoteAccessDialog = false) }
    }

    fun saveRemoteCredentials(username: String, pass: String) {
        preferences.setRemoteCredentials(username, pass)
        _uiState.update {
            it.copy(
                hasRemoteCredentials = true,
                remoteUsername = username
            )
        }
        val sessionId = _uiState.value.activeSessionId
        appendLine(sessionId, TerminalLine(text = "[Credenciales remotas configuradas para '$username']", type = LineType.SYSTEM))
    }

    // --- DIALOGS & ACTIONS ---

    fun openSettingsDialog() {
        vibrateKey()
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun closeSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun saveSettings(newSettings: AgentSettings) {
        preferences.saveSettings(newSettings)
        _uiState.update { it.copy(settings = newSettings, showSettingsDialog = false) }
    }

    fun openQuickActions() {
        vibrateKey()
        _uiState.update { it.copy(showQuickActions = true) }
    }

    fun closeQuickActions() {
        _uiState.update { it.copy(showQuickActions = false) }
    }

    fun closeEditor() {
        _uiState.update { it.copy(editorState = null) }
    }

    fun saveEditorContent(path: String, content: String) {
        vfs.writeFile(path, content)
        val sessionId = _uiState.value.activeSessionId
        appendLine(sessionId, TerminalLine(text = "[Archivo guardado en $path]", type = LineType.SYSTEM))
        closeEditor()
    }

    fun runEditorScript(path: String) {
        closeEditor()
        onInputChange("bash $path")
        executeCurrentCommand()
    }

    private fun vibrateKey() {
        if (!_uiState.value.settings.enableVibration) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(12)
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        remoteServer.stop()
        webServer.stop()
    }
}
