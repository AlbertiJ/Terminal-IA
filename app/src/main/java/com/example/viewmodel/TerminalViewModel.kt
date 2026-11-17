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
import com.example.boot.BootCompletedReceiver
import com.example.controller.TerminalController
import com.example.controller.TerminalLogLevel
import com.example.data.TerminalPreferences
import com.example.data.db.CommandHistoryRepository
import com.example.data.db.TermuxDatabase
import com.example.engine.TerminalEngine
import com.example.fs.VirtualFileSystem
import com.example.model.AgentSettings
import com.example.model.LineType
import com.example.model.SessionType
import com.example.model.TerminalLine
import com.example.model.TerminalSession
import com.example.script.ScriptManager
import com.example.server.RealTerminalExecutor
import com.example.server.WebRealExecResult
import com.example.server.WebTerminalServer
import com.example.ssh.ActiveSshShellSession
import com.example.ssh.SshConnectionError
import com.example.ssh.SshDiagnosticException
import com.example.ssh.SshDiagnosticResult
import com.example.ssh.SshKeyManager
import com.example.ssh.TermuxSetupHelper
import com.example.ssh.TermuxSshClient
import com.example.ui.RealSshConnectionState
import com.example.ui.RealTerminalByteSink
import com.example.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTerminalMode {
    REAL_TERMUX,
    PRACTICE_SIMULATED
}

data class TerminalUiState(
    val terminalMode: AppTerminalMode = AppTerminalMode.REAL_TERMUX,
    val isTermuxInstalled: Boolean = false,
    val termuxSshPort: Int = TermuxSetupHelper.DEFAULT_SSH_PORT,
    val termuxSshUsername: String = "termux",
    val sshPublicKeyFingerprint: String = "",
    val setupCommandCopied: Boolean = false,
    val showSetupWizard: Boolean = false,
    val isTestingSshConnection: Boolean = false,
    val sshDiagnosticResult: SshDiagnosticResult? = null,
    val realSshConnectionState: RealSshConnectionState = RealSshConnectionState.Disconnected,
    val pendingHostKeyMismatch: SshConnectionError.HostKeyChanged? = null,
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
    val isWebTerminalRunning: Boolean = false,
    val webTerminalPort: Int = 8080,
    val isWebLanAccessEnabled: Boolean = false,
    val isWebHttpsEnabled: Boolean = false,
    val deviceIps: List<String> = emptyList(),
    val hasRemoteCredentials: Boolean = false,
    val remoteUsername: String = "",
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

    private val sshKeyManager = SshKeyManager(application)
    private val termuxSshClient = TermuxSshClient(application, sshKeyManager)

    private lateinit var webServer: WebTerminalServer
    private lateinit var engine: TerminalEngine

    private var activeShellSession: ActiveSshShellSession? = null
    private var realByteSink: RealTerminalByteSink? = null
    private val sinkLock = Any()

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
            onServerAction = { action, _ ->
                when (action) {
                    "start" -> startWebTerminal(8080)
                    "stop" -> stopWebTerminal()
                }
            }
        )

        val realExecutor = object : RealTerminalExecutor {
            override fun isRealModeConnected(): Boolean {
                return _uiState.value.terminalMode == AppTerminalMode.REAL_TERMUX &&
                    activeShellSession?.isConnected() == true
            }

            override suspend fun executeCommand(command: String, currentDir: String): WebRealExecResult {
                val session = activeShellSession
                if (_uiState.value.terminalMode != AppTerminalMode.REAL_TERMUX || session == null || !session.isConnected()) {
                    return WebRealExecResult(
                        success = false,
                        output = "",
                        currentDir = currentDir,
                        error = "Modo Real (Termux SSH) desconectado.",
                        realModeConnected = false
                    )
                }

                val execRes = session.executeExecCommand(command, currentDir)
                return execRes.fold(
                    onSuccess = { out ->
                        terminalController.log(
                            level = TerminalLogLevel.COMMAND,
                            tag = "WEB_SSH_EXEC",
                            message = "$command (exit ${out.exitCode}, dir ${out.currentDir})"
                        )
                        WebRealExecResult(
                            success = out.exitCode == 0,
                            output = out.output,
                            currentDir = out.currentDir,
                            error = if (out.exitCode != 0 && out.output.isBlank()) "Código de salida: ${out.exitCode}" else null,
                            realModeConnected = true
                        )
                    },
                    onFailure = { err ->
                        WebRealExecResult(
                            success = false,
                            output = "",
                            currentDir = currentDir,
                            error = err.localizedMessage ?: "Error ejecutando comando SSH.",
                            realModeConnected = session.isConnected()
                        )
                    }
                )
            }
        }

        webServer = WebTerminalServer(
            context = application,
            preferences = preferences,
            realExecutor = realExecutor,
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
            title = "1: práctica",
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

        val bootLogLines = if (vfs.exists(BootCompletedReceiver.BOOT_LOG_PATH)) {
            val content = vfs.readFile(BootCompletedReceiver.BOOT_LOG_PATH).getOrDefault("").trim()
            if (content.isNotEmpty()) {
                listOf(
                    TerminalLine(
                        text = "[Log del último auto-arranque (${BootCompletedReceiver.BOOT_LOG_PATH})]\n$content",
                        type = LineType.SYSTEM
                    )
                )
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        val initialLines = engine.getInitialWelcomeLines() + bootLogLines
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

        val ips = NetworkUtils.getDeviceIpAddresses()
        val hasCreds = preferences.hasRemoteCredentials()
        val lanAccess = preferences.isWebLanAccessEnabled()
        val httpsEnabled = preferences.isWebHttpsEnabled()
        val shouldStartWeb = (preferences.isWebServerEnabled() || loadedSettings.autoStartRemoteServersOnBoot) && hasCreds
        val termuxInstalled = TermuxSetupHelper.isTermuxInstalled(application)
        val savedSshPort = preferences.getTermuxSshPort()
        val savedSshUser = preferences.getTermuxSshUser()
        val practicePreferred = preferences.isPracticeModePreferred()
        val initialMode = if (practicePreferred) AppTerminalMode.PRACTICE_SIMULATED else AppTerminalMode.REAL_TERMUX

        _uiState.value = TerminalUiState(
            terminalMode = initialMode,
            isTermuxInstalled = termuxInstalled,
            termuxSshPort = savedSshPort,
            termuxSshUsername = savedSshUser,
            showSetupWizard = !practicePreferred && !termuxInstalled,
            sessions = listOf(mainSession, claudeSession, openClawSession),
            activeSessionId = mainSession.id,
            sessionLines = mapOf(
                mainSession.id to initialLines,
                claudeSession.id to claudeIntro,
                openClawSession.id to openClawIntro
            ),
            settings = loadedSettings,
            isWebTerminalRunning = shouldStartWeb,
            webTerminalPort = 8080,
            isWebLanAccessEnabled = lanAccess,
            isWebHttpsEnabled = httpsEnabled,
            deviceIps = ips,
            hasRemoteCredentials = hasCreds,
            remoteUsername = preferences.getRemoteUsername()
        )

        // Generate/load non-exportable Ed25519 identity off the main thread
        viewModelScope.launch(Dispatchers.IO) {
            val fp = runCatching { sshKeyManager.getPublicKeyFingerprintSha256() }.getOrDefault("")
            _uiState.update { it.copy(sshPublicKeyFingerprint = fp) }
        }

        if (shouldStartWeb) {
            webServer.start(8080, allowLan = lanAccess, useHttps = httpsEnabled)
        }
    }

    // --- MODE & TERMUX REAL SSH TERMINAL ---

    fun setTerminalMode(mode: AppTerminalMode) {
        vibrateKey()
        preferences.setPracticeModePreferred(mode == AppTerminalMode.PRACTICE_SIMULATED)
        _uiState.update {
            it.copy(
                terminalMode = mode,
                isTermuxInstalled = TermuxSetupHelper.isTermuxInstalled(getApplication())
            )
        }
    }

    fun openSetupWizard() {
        vibrateKey()
        refreshTermuxInstallStatus()
        _uiState.update { it.copy(showSetupWizard = true) }
    }

    fun closeSetupWizard() {
        _uiState.update { it.copy(showSetupWizard = false) }
    }

    fun refreshTermuxInstallStatus() {
        val installed = TermuxSetupHelper.isTermuxInstalled(getApplication())
        _uiState.update { it.copy(isTermuxInstalled = installed) }
    }

    fun openTermuxDownloadPage() {
        vibrateKey()
        TermuxSetupHelper.openTermuxDownloadPage(getApplication())
    }

    fun launchTermuxApp() {
        vibrateKey()
        TermuxSetupHelper.launchTermuxApp(getApplication())
    }

    fun updateTermuxPortAndUser(port: Int, username: String) {
        if (port in 1024..65535) {
            preferences.setTermuxSshPort(port)
        }
        val cleanUser = username.trim().ifBlank { "termux" }
        preferences.setTermuxSshUser(cleanUser)
        _uiState.update {
            it.copy(
                termuxSshPort = if (port in 1024..65535) port else it.termuxSshPort,
                termuxSshUsername = cleanUser
            )
        }
    }

    fun buildAndTrackSetupCommand(port: Int): String {
        vibrateKey()
        val validPort = if (port in 1024..65535) port else _uiState.value.termuxSshPort
        preferences.setTermuxSshPort(validPort)
        val pubKey = sshKeyManager.getOrCreatePublicKey()
        val fp = sshKeyManager.getPublicKeyFingerprintSha256()
        val cmd = TermuxSetupHelper.buildSetupCommand(pubKey, validPort)
        _uiState.update {
            it.copy(
                termuxSshPort = validPort,
                sshPublicKeyFingerprint = fp,
                setupCommandCopied = true
            )
        }
        terminalController.log(
            level = TerminalLogLevel.INFO,
            tag = "TERMUX_WIZARD",
            message = "Comando de configuración generado para 127.0.0.1:$validPort (solo clave pública $fp)"
        )
        return cmd
    }

    fun testTermuxConnection(port: Int = _uiState.value.termuxSshPort, username: String = _uiState.value.termuxSshUsername) {
        vibrateKey()
        refreshTermuxInstallStatus()
        updateTermuxPortAndUser(port, username)
        _uiState.update {
            it.copy(
                isTestingSshConnection = true,
                sshDiagnosticResult = null,
                pendingHostKeyMismatch = null
            )
        }
        viewModelScope.launch {
            val result = termuxSshClient.testConnection(port = port, username = username)
            val mismatch = (result as? SshDiagnosticResult.Failure)?.error as? SshConnectionError.HostKeyChanged
            _uiState.update {
                it.copy(
                    isTestingSshConnection = false,
                    sshDiagnosticResult = result,
                    pendingHostKeyMismatch = mismatch,
                    isTermuxInstalled = TermuxSetupHelper.isTermuxInstalled(getApplication())
                )
            }
            when (result) {
                is SshDiagnosticResult.Success -> terminalController.log(
                    level = TerminalLogLevel.INFO,
                    tag = "SSH_TEST",
                    message = "Handshake SSH OK con ${result.host}:${result.port} (${result.hostFingerprintSha256})"
                )
                is SshDiagnosticResult.Failure -> terminalController.log(
                    level = TerminalLogLevel.WARNING,
                    tag = "SSH_TEST",
                    message = result.error.toUserMessage()
                )
            }
        }
    }

    fun registerRealByteSink(sink: RealTerminalByteSink?) {
        synchronized(sinkLock) {
            realByteSink = sink
        }
    }

    fun connectRealTerminal(cols: Int = 80, rows: Int = 24) {
        vibrateKey()
        disconnectRealTerminal(silent = true)
        val state = _uiState.value
        val port = state.termuxSshPort
        val user = state.termuxSshUsername

        _uiState.update {
            it.copy(
                terminalMode = AppTerminalMode.REAL_TERMUX,
                showSetupWizard = false,
                realSshConnectionState = RealSshConnectionState.Connecting,
                pendingHostKeyMismatch = null
            )
        }

        viewModelScope.launch {
            val res = termuxSshClient.openShellSession(
                port = port,
                username = user,
                initialCols = cols,
                initialRows = rows,
                onOutputBytes = { bytes, len ->
                    synchronized(sinkLock) {
                        realByteSink?.onBytesReceived(bytes, len)
                    }
                },
                onSessionClosed = { reason ->
                    activeShellSession = null
                    _uiState.update { current ->
                        if (current.realSshConnectionState is RealSshConnectionState.Connected) {
                            current.copy(
                                realSshConnectionState = if (reason.isNullOrBlank()) {
                                    RealSshConnectionState.Disconnected
                                } else {
                                    RealSshConnectionState.Error(SshConnectionError.OtherError("Sesión cerrada: $reason"))
                                }
                            )
                        } else {
                            current
                        }
                    }
                }
            )

            res.onSuccess { session ->
                activeShellSession = session
                _uiState.update {
                    it.copy(
                        realSshConnectionState = RealSshConnectionState.Connected(
                            host = TermuxSshClient.LOCALHOST,
                            port = port,
                            hostFingerprint = session.hostFingerprintSha256
                        ),
                        sshDiagnosticResult = SshDiagnosticResult.Success(
                            host = TermuxSshClient.LOCALHOST,
                            port = port,
                            hostFingerprintSha256 = session.hostFingerprintSha256,
                            hostAlgorithm = sshKeyManager.getTrustedHostAlgorithm(TermuxSshClient.LOCALHOST, port) ?: "ssh-ed25519",
                            clientKeyFingerprintSha256 = it.sshPublicKeyFingerprint,
                            firstTimeTrusted = session.firstTimeTrusted
                        )
                    )
                }
                terminalController.log(
                    level = TerminalLogLevel.INFO,
                    tag = "SSH_SHELL",
                    message = "Sesión Shell PTY activa en 127.0.0.1:$port"
                )
            }.onFailure { throwable ->
                val sshError = (throwable as? SshDiagnosticException)?.connectionError
                    ?: SshConnectionError.OtherError(throwable.message ?: "Error desconocido")
                val mismatch = sshError as? SshConnectionError.HostKeyChanged
                _uiState.update {
                    it.copy(
                        realSshConnectionState = RealSshConnectionState.Error(sshError),
                        pendingHostKeyMismatch = mismatch
                    )
                }
                terminalController.log(
                    level = TerminalLogLevel.ERROR,
                    tag = "SSH_SHELL",
                    message = sshError.toUserMessage()
                )
            }
        }
    }

    fun disconnectRealTerminal(silent: Boolean = false) {
        if (!silent) vibrateKey()
        val prev = activeShellSession
        activeShellSession = null
        prev?.disconnect()
        _uiState.update { it.copy(realSshConnectionState = RealSshConnectionState.Disconnected) }
    }

    fun sendRealKeyboardBytes(bytes: ByteArray) {
        activeShellSession?.sendBytes(bytes)
    }

    fun resizeRealTerminal(cols: Int, rows: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            activeShellSession?.resizePty(cols, rows)
        }
    }

    fun acceptChangedHostKey(mismatch: SshConnectionError.HostKeyChanged, reconnectAfter: Boolean = false) {
        vibrateKey()
        sshKeyManager.acceptChangedHostKey(
            host = mismatch.host,
            port = mismatch.port,
            algorithm = mismatch.algorithm,
            hostKeyBytes = mismatch.hostKeyBytes
        )
        _uiState.update { it.copy(pendingHostKeyMismatch = null, sshDiagnosticResult = null) }
        if (reconnectAfter) {
            connectRealTerminal()
        } else {
            testTermuxConnection(mismatch.port, _uiState.value.termuxSshUsername)
        }
    }

    fun dismissHostKeyMismatch() {
        _uiState.update { it.copy(pendingHostKeyMismatch = null) }
    }

    // --- PRACTICE MODE (ISOLATED SIMULATED ENGINE) ---

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
            SessionType.SHELL -> "$count: práctica"
            SessionType.CLAUDE -> "$count: claude"
            SessionType.OPENCLAW -> "$count: openclaw"
        }
        val newSession = TerminalSession(title = name, type = type)
        val introLines = when (type) {
            SessionType.SHELL -> listOf(
                TerminalLine(
                    text = "[MODO PRÁCTICA] Nueva sesión de shell simulada creada.",
                    type = LineType.SYSTEM
                )
            )
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
            tag = "PRACTICE_EXEC",
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

        val promptHeader = "practica@sandbox:${session.currentDirectory}$ $rawInput"
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
        if (_uiState.value.terminalMode == AppTerminalMode.REAL_TERMUX) {
            when (key) {
                "ESC" -> sendRealKeyboardBytes(byteArrayOf(0x1B))
                "TAB" -> sendRealKeyboardBytes("\t".toByteArray())
                "CTRL" -> sendRealKeyboardBytes(byteArrayOf(0x03)) // Ctrl+C
                "ALT" -> sendRealKeyboardBytes(byteArrayOf(0x1B))
                "↑" -> sendRealKeyboardBytes("\u001b[A".toByteArray())
                "↓" -> sendRealKeyboardBytes("\u001b[B".toByteArray())
                "|", "/", "-", "~", "\"", "$" -> sendRealKeyboardBytes(key.toByteArray())
                "clear" -> sendRealKeyboardBytes("clear\n".toByteArray())
                "history" -> sendRealKeyboardBytes("history\n".toByteArray())
                "ip" -> sendRealKeyboardBytes("ip addr || ifconfig\n".toByteArray())
                "neofetch" -> sendRealKeyboardBytes("uname -a\n".toByteArray())
                "ssh" -> openSetupWizard()
                "scripts" -> openScriptManager()
                "logs" -> openLogsDialog()
                else -> {}
            }
            return
        }

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

    // --- WEB TERMINAL SERVER ---

    fun startWebTerminal(port: Int = 8080) {
        if (!preferences.hasRemoteCredentials()) {
            val sessionId = _uiState.value.activeSessionId
            appendLine(
                sessionId,
                TerminalLine(
                    text = "[SEGURIDAD] Configura usuario y contraseña (mín. 12 caracteres) en Acceso Remoto antes de iniciar la Terminal Web.",
                    type = LineType.ERROR
                )
            )
            preferences.setWebServerEnabled(false)
            _uiState.update { it.copy(isWebTerminalRunning = false) }
            return
        }
        val allowLan = preferences.isWebLanAccessEnabled()
        val useHttps = preferences.isWebHttpsEnabled()
        webServer.start(port, allowLan = allowLan, useHttps = useHttps)
        preferences.setWebServerEnabled(true)
        _uiState.update {
            it.copy(
                isWebTerminalRunning = true,
                webTerminalPort = port,
                isWebLanAccessEnabled = allowLan,
                isWebHttpsEnabled = useHttps,
                deviceIps = NetworkUtils.getDeviceIpAddresses()
            )
        }
    }

    fun stopWebTerminal() {
        webServer.stop()
        preferences.setWebServerEnabled(false)
        _uiState.update { it.copy(isWebTerminalRunning = false) }
    }

    fun toggleWebTerminal(enabled: Boolean, port: Int) {
        vibrateKey()
        if (enabled) {
            startWebTerminal(port)
        } else {
            stopWebTerminal()
        }
    }

    fun setWebLanAccessEnabled(enabled: Boolean) {
        vibrateKey()
        preferences.setWebLanAccessEnabled(enabled)
        _uiState.update {
            it.copy(
                isWebLanAccessEnabled = enabled,
                deviceIps = NetworkUtils.getDeviceIpAddresses()
            )
        }
        if (_uiState.value.isWebTerminalRunning && preferences.hasRemoteCredentials()) {
            val port = _uiState.value.webTerminalPort
            webServer.stop()
            webServer.start(port, allowLan = enabled, useHttps = preferences.isWebHttpsEnabled())
        }
    }

    fun setWebHttpsEnabled(enabled: Boolean) {
        vibrateKey()
        preferences.setWebHttpsEnabled(enabled)
        _uiState.update { it.copy(isWebHttpsEnabled = enabled) }
        if (_uiState.value.isWebTerminalRunning && preferences.hasRemoteCredentials()) {
            val port = _uiState.value.webTerminalPort
            webServer.stop()
            webServer.start(port, allowLan = preferences.isWebLanAccessEnabled(), useHttps = enabled)
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
        val ips = NetworkUtils.getDeviceIpAddresses()
        _uiState.update {
            it.copy(
                showRemoteAccessDialog = true,
                deviceIps = ips,
                hasRemoteCredentials = preferences.hasRemoteCredentials(),
                remoteUsername = preferences.getRemoteUsername(),
                isWebLanAccessEnabled = preferences.isWebLanAccessEnabled(),
                isWebHttpsEnabled = preferences.isWebHttpsEnabled()
            )
        }
    }

    fun closeRemoteAccessDialog() {
        _uiState.update { it.copy(showRemoteAccessDialog = false) }
    }

    fun saveRemoteCredentials(username: String, pass: String) {
        val cleanUser = username.trim()
        preferences.setRemoteCredentials(cleanUser, pass)
        _uiState.update {
            it.copy(
                hasRemoteCredentials = preferences.hasRemoteCredentials(),
                remoteUsername = preferences.getRemoteUsername()
            )
        }
        val sessionId = _uiState.value.activeSessionId
        appendLine(
            sessionId,
            TerminalLine(
                text = "[Credenciales web configuradas para '$cleanUser' con PBKDF2-HMAC-SHA256 (${com.example.security.SecurityManager.CURRENT_PBKDF2_ITERATIONS} iter.)]",
                type = LineType.SYSTEM
            )
        )
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
        disconnectRealTerminal(silent = true)
        webServer.stop()
    }
}
