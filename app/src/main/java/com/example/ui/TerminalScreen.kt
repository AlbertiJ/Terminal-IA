package com.example.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LineType
import com.example.model.SessionType
import com.example.viewmodel.AppTerminalMode
import com.example.viewmodel.TerminalViewModel

@Composable
fun TerminalScreen(viewModel: TerminalViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val theme = uiState.settings.terminalTheme
    val listState = rememberLazyListState()
    val agentListState = rememberLazyListState()

    val currentSession = uiState.sessions.firstOrNull { it.id == uiState.activeSessionId }
    val currentLines = uiState.sessionLines[uiState.activeSessionId] ?: emptyList()

    val agentLines = currentLines.filter {
        it.type == LineType.AGENT_CLAUDE || it.type == LineType.AGENT_OPENCLAW || it.type == LineType.AGENT_STEP
    }

    LaunchedEffect(currentLines.size, uiState.isExecuting) {
        if (currentLines.isNotEmpty()) {
            listState.animateScrollToItem(currentLines.size - 1)
        }
        if (agentLines.isNotEmpty()) {
            agentListState.animateScrollToItem(agentLines.size - 1)
        }
    }

    val transition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaAnim"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = theme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(theme.background)
        ) {
            // Top App Bar
            TerminalTopBar(
                currentPath = currentSession?.currentDirectory ?: "/home/user",
                isExecuting = uiState.isExecuting,
                isRealSshConnected = uiState.realSshConnectionState is RealSshConnectionState.Connected,
                isWebRunning = uiState.isWebTerminalRunning,
                isSplitScreenMode = uiState.isSplitScreenMode,
                pulseAlpha = alphaAnim,
                theme = theme,
                onOpenWizard = { viewModel.openSetupWizard() },
                onOpenSettings = { viewModel.openSettingsDialog() },
                onOpenRemoteAccess = { viewModel.openRemoteAccessDialog() },
                onOpenFileManager = { viewModel.openFileManager() },
                onOpenScriptManager = { viewModel.openScriptManager() },
                onOpenLogs = { viewModel.openLogsDialog() },
                onToggleSplitScreen = { viewModel.toggleSplitScreen() },
                onOpenQuickActions = { viewModel.openQuickActions() },
                onClear = { viewModel.onExtraKeyPressed("clear") }
            )

            // Strict Mode Switcher: REAL (Termux SSH) vs PRACTICE (Simulated Sandbox)
            ModeSelectorBar(
                currentMode = uiState.terminalMode,
                isRealConnected = uiState.realSshConnectionState is RealSshConnectionState.Connected,
                theme = theme,
                onSelectMode = { viewModel.setTerminalMode(it) }
            )

            if (uiState.terminalMode == AppTerminalMode.REAL_TERMUX) {
                // REAL MODE: pure VT100/xterm PTY over Termux SSH
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    RealTerminalPane(
                        theme = theme,
                        fontSizeSp = uiState.settings.fontSizeSp,
                        sshPort = uiState.termuxSshPort,
                        isTermuxInstalled = uiState.isTermuxInstalled,
                        connectionState = uiState.realSshConnectionState,
                        pendingHostKeyMismatch = uiState.pendingHostKeyMismatch,
                        onRegisterByteSink = { viewModel.registerRealByteSink(it) },
                        onKeyboardInputBytes = { viewModel.sendRealKeyboardBytes(it) },
                        onViewportResize = { cols, rows -> viewModel.resizeRealTerminal(cols, rows) },
                        onConnect = { cols, rows -> viewModel.connectRealTerminal(cols, rows) },
                        onDisconnect = { viewModel.disconnectRealTerminal() },
                        onOpenWizard = { viewModel.openSetupWizard() },
                        onSwitchToPracticeMode = { viewModel.setTerminalMode(AppTerminalMode.PRACTICE_SIMULATED) },
                        onAcceptChangedHostKey = { mismatch -> viewModel.acceptChangedHostKey(mismatch, reconnectAfter = true) },
                        onDismissHostKeyMismatch = { viewModel.dismissHostKeyMismatch() }
                    )
                }

                // Extra Keys Bar sends ANSI/control bytes to SSH PTY in Real Mode
                ExtraKeysBar(
                    theme = theme,
                    onKeyPressed = { viewModel.onExtraKeyPressed(it) }
                )
            } else {
                // PRACTICE MODE: strictly labeled and isolated simulated TerminalEngine
                PracticeModeWarningBanner(
                    theme = theme,
                    onSwitchToReal = { viewModel.setTerminalMode(AppTerminalMode.REAL_TERMUX) }
                )

                // Multi-tab Session Bar (Practice / Simulated Agents)
                SessionTabsBar(
                    sessions = uiState.sessions,
                    activeSessionId = uiState.activeSessionId,
                    theme = theme,
                    onSelectSession = { viewModel.selectSession(it) },
                    onCreateSession = { viewModel.createSession(it) },
                    onCloseSession = { viewModel.closeSession(it) }
                )

                // Main Console Area (Single or Split Screen)
                if (uiState.isSplitScreenMode) {
                    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(currentLines, key = { it.id }) { line ->
                                TerminalLineView(
                                    line = line,
                                    theme = theme,
                                    fontSizeSp = uiState.settings.fontSizeSp,
                                    onRunSuggestedCommand = { cmd ->
                                        viewModel.onInputChange(cmd)
                                        viewModel.executeCurrentCommand()
                                    }
                                )
                            }
                        }

                        Surface(
                            color = theme.surface,
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerticalSplit,
                                    contentDescription = null,
                                    tint = theme.openClawColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MONITOR DIVIDIDO: AGENTES CLAUDE & OPENCLAW (PRÁCTICA)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.openClawColor
                                )
                            }
                        }

                        LazyColumn(
                            state = agentListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(theme.surface.copy(alpha = 0.4f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (agentLines.isEmpty()) {
                                item {
                                    Text(
                                        text = "(Esperando ejecuciones de agentes. Ejecuta: claude o openclaw)",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = theme.primaryText.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            } else {
                                items(agentLines, key = { "split_" + it.id }) { line ->
                                    TerminalLineView(
                                        line = line,
                                        theme = theme,
                                        fontSizeSp = uiState.settings.fontSizeSp,
                                        onRunSuggestedCommand = { cmd ->
                                            viewModel.onInputChange(cmd)
                                            viewModel.executeCurrentCommand()
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("terminal_output_list"),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(currentLines, key = { it.id }) { line ->
                            TerminalLineView(
                                line = line,
                                theme = theme,
                                fontSizeSp = uiState.settings.fontSizeSp,
                                onRunSuggestedCommand = { cmd ->
                                    viewModel.onInputChange(cmd)
                                    viewModel.executeCurrentCommand()
                                }
                            )
                        }

                        if (uiState.isExecuting) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(theme.primaryText)
                                            .alpha(alphaAnim)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Procesando respuesta del entorno simulado...",
                                        color = theme.primaryText.copy(alpha = 0.8f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic Autocomplete Suggestions Bar (TerminalController)
                if (uiState.autocompleteSuggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.surface.copy(alpha = 0.95f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TAB:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = theme.accentText,
                            fontWeight = FontWeight.Bold
                        )
                        for (sug in uiState.autocompleteSuggestions) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = theme.background,
                                border = BorderStroke(1.dp, theme.accentText.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clickable { viewModel.applySuggestion(sug) }
                                    .testTag("suggestion_$sug")
                            ) {
                                Text(
                                    text = sug,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.primaryText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Command Input Row (Practice Mode only)
                TerminalInputBar(
                    input = uiState.input,
                    sessionType = currentSession?.type ?: SessionType.SHELL,
                    theme = theme,
                    fontSizeSp = uiState.settings.fontSizeSp,
                    isExecuting = uiState.isExecuting,
                    onInputChange = { viewModel.onInputChange(it) },
                    onExecute = { viewModel.executeCurrentCommand() }
                )

                // Extra Keys Bar
                ExtraKeysBar(
                    theme = theme,
                    onKeyPressed = { viewModel.onExtraKeyPressed(it) }
                )
            }
        }

        // Dialogs
        if (uiState.showSetupWizard) {
            TermuxSetupWizardDialog(
                theme = theme,
                isTermuxInstalled = uiState.isTermuxInstalled,
                setupCommandCopied = uiState.setupCommandCopied,
                sshPort = uiState.termuxSshPort,
                sshUsername = uiState.termuxSshUsername,
                publicKeyFingerprint = uiState.sshPublicKeyFingerprint.ifBlank { "Generando clave Ed25519..." },
                isTestingConnection = uiState.isTestingSshConnection,
                diagnosticResult = uiState.sshDiagnosticResult,
                pendingHostKeyMismatch = uiState.pendingHostKeyMismatch,
                onRefreshTermuxStatus = { viewModel.refreshTermuxInstallStatus() },
                onOpenTermuxDownload = { viewModel.openTermuxDownloadPage() },
                onLaunchTermux = { viewModel.launchTermuxApp() },
                onUpdatePortAndUser = { p, u -> viewModel.updateTermuxPortAndUser(p, u) },
                onCopySetupCommand = { p -> viewModel.buildAndTrackSetupCommand(p) },
                onTestConnection = { p, u -> viewModel.testTermuxConnection(p, u) },
                onAcceptChangedHostKey = { mismatch -> viewModel.acceptChangedHostKey(mismatch, reconnectAfter = false) },
                onDismissHostKeyMismatch = { viewModel.dismissHostKeyMismatch() },
                onConnectShellAndClose = { viewModel.connectRealTerminal() },
                onDismiss = { viewModel.closeSetupWizard() }
            )
        }

        if (uiState.showSettingsDialog) {
            AgentSettingsDialog(
                currentSettings = uiState.settings,
                onDismiss = { viewModel.closeSettingsDialog() },
                onSave = { updated -> viewModel.saveSettings(updated) }
            )
        }

        if (uiState.editorState != null) {
            FileEditorDialog(
                filePath = uiState.editorState!!.filePath,
                initialContent = uiState.editorState!!.content,
                theme = theme,
                onDismiss = { viewModel.closeEditor() },
                onSave = { newContent ->
                    viewModel.saveEditorContent(uiState.editorState!!.filePath, newContent)
                },
                onRun = {
                    viewModel.runEditorScript(uiState.editorState!!.filePath)
                }
            )
        }

        if (uiState.showFileManagerDialog) {
            FileManagerDialog(
                vfs = viewModel.vfs,
                initialPath = currentSession?.currentDirectory ?: "/home/user",
                theme = theme,
                onDismiss = { viewModel.closeFileManager() },
                onEditFile = { path ->
                    val content = viewModel.vfs.readFile(path).getOrDefault("")
                    viewModel.closeFileManager()
                    viewModel.openEditorForPath(path, content)
                },
                onRunFile = { path ->
                    viewModel.closeFileManager()
                    viewModel.setTerminalMode(AppTerminalMode.PRACTICE_SIMULATED)
                    viewModel.onInputChange("bash $path")
                    viewModel.executeCurrentCommand()
                }
            )
        }

        if (uiState.showScriptManagerDialog) {
            ScriptManagerDialog(
                scriptManager = viewModel.scriptManager,
                theme = theme,
                onDismiss = { viewModel.closeScriptManager() },
                onEditScript = { path ->
                    val content = viewModel.vfs.readFile(path).getOrDefault("")
                    viewModel.openEditorForPath(path, content)
                },
                onRunScript = { path ->
                    viewModel.setTerminalMode(AppTerminalMode.PRACTICE_SIMULATED)
                    viewModel.onInputChange("bash $path")
                    viewModel.executeCurrentCommand()
                }
            )
        }

        if (uiState.showLogsDialog) {
            TerminalLogsDialog(
                controller = viewModel.terminalController,
                theme = theme,
                onDismiss = { viewModel.closeLogsDialog() }
            )
        }

        if (uiState.showRemoteAccessDialog) {
            RemoteAccessDialog(
                theme = theme,
                isWebRunning = uiState.isWebTerminalRunning,
                webPort = uiState.webTerminalPort,
                isLanAccessEnabled = uiState.isWebLanAccessEnabled,
                isHttpsEnabled = uiState.isWebHttpsEnabled,
                isRealSshConnected = uiState.terminalMode == AppTerminalMode.REAL_TERMUX &&
                    uiState.realSshConnectionState is RealSshConnectionState.Connected,
                deviceIps = uiState.deviceIps,
                currentUsername = uiState.remoteUsername,
                hasCredentials = uiState.hasRemoteCredentials,
                onToggleWebServer = { enabled, port -> viewModel.toggleWebTerminal(enabled, port) },
                onToggleLanAccess = { enabled -> viewModel.setWebLanAccessEnabled(enabled) },
                onToggleHttps = { enabled -> viewModel.setWebHttpsEnabled(enabled) },
                onSaveCredentials = { user, pass -> viewModel.saveRemoteCredentials(user, pass) },
                onDismiss = { viewModel.closeRemoteAccessDialog() }
            )
        }

        if (uiState.showQuickActions) {
            QuickActionsDialog(
                theme = theme,
                onDismiss = { viewModel.closeQuickActions() },
                onSelectAction = { cmd ->
                    if (uiState.terminalMode == AppTerminalMode.REAL_TERMUX &&
                        uiState.realSshConnectionState is RealSshConnectionState.Connected
                    ) {
                        viewModel.sendRealKeyboardBytes("$cmd\n".toByteArray())
                    } else {
                        viewModel.setTerminalMode(AppTerminalMode.PRACTICE_SIMULATED)
                        viewModel.onInputChange(cmd)
                        viewModel.executeCurrentCommand()
                    }
                }
            )
        }
    }
}

@Composable
private fun ModeSelectorBar(
    currentMode: AppTerminalMode,
    isRealConnected: Boolean,
    theme: com.example.model.TerminalThemePreset,
    onSelectMode: (AppTerminalMode) -> Unit
) {
    Surface(
        color = theme.surface.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val realSelected = currentMode == AppTerminalMode.REAL_TERMUX
            Surface(
                color = if (realSelected) Color(0xFF00E676).copy(alpha = 0.16f) else theme.background,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(
                    1.dp,
                    if (realSelected) Color(0xFF00E676) else theme.primaryText.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectMode(AppTerminalMode.REAL_TERMUX) }
                    .testTag("mode_tab_real")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isRealConnected) Color(0xFF00E676) else Color(0xFFFFB300))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Modo Real · Termux",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = if (realSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (realSelected) Color(0xFF00E676) else theme.primaryText.copy(alpha = 0.7f)
                    )
                }
            }

            val practiceSelected = currentMode == AppTerminalMode.PRACTICE_SIMULATED
            Surface(
                color = if (practiceSelected) Color(0xFFFFB300).copy(alpha = 0.16f) else theme.background,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(
                    1.dp,
                    if (practiceSelected) Color(0xFFFFB300) else theme.primaryText.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectMode(AppTerminalMode.PRACTICE_SIMULATED) }
                    .testTag("mode_tab_practice")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Modo Práctica · Simulado",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = if (practiceSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (practiceSelected) Color(0xFFFFB300) else theme.primaryText.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeModeWarningBanner(
    theme: com.example.model.TerminalThemePreset,
    onSwitchToReal: () -> Unit
) {
    Surface(
        color = Color(0xFFFFB300).copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("practice_mode_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Advertencia Modo Práctica",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MODO PRÁCTICA (SIMULADO): NO ejecuta Linux real ni paquetes externos.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD54F)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Ir a Real",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E676),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(theme.background)
                    .clickable { onSwitchToReal() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun TerminalTopBar(
    currentPath: String,
    isExecuting: Boolean,
    isRealSshConnected: Boolean,
    isWebRunning: Boolean,
    isSplitScreenMode: Boolean,
    pulseAlpha: Float,
    theme: com.example.model.TerminalThemePreset,
    onOpenWizard: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRemoteAccess: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenScriptManager: () -> Unit,
    onOpenLogs: () -> Unit,
    onToggleSplitScreen: () -> Unit,
    onOpenQuickActions: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        color = theme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Terminal-IA",
                    tint = theme.primaryText,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TERMINAL-IA",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = theme.primaryText
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(theme.background)
                        .clickable { onOpenFileManager() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = theme.promptPath,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (currentPath.startsWith("/home/user")) currentPath.replaceFirst("/home/user", "~") else currentPath,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = theme.promptPath
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isExecuting) theme.accentText else if (isRealSshConnected) Color(0xFF00E676) else Color(0xFFFFB300))
                        .alpha(if (isExecuting) pulseAlpha else 1.0f)
                )
                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onOpenWizard,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("wizard_top_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Asistente de Conexión Termux",
                        tint = if (isRealSshConnected) Color(0xFF00E676) else Color(0xFFFFB300),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onOpenFileManager,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Explorador de Archivos",
                        tint = theme.promptPath,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onOpenScriptManager,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("script_manager_top_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Gestor de Scripts (.sh)",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onToggleSplitScreen,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerticalSplit,
                        contentDescription = "Pantalla Dividida",
                        tint = if (isSplitScreenMode) theme.openClawColor else theme.primaryText.copy(alpha = 0.6f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onOpenRemoteAccess,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("remote_access_button")
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = Icons.Default.Lan,
                            contentDescription = "Consola Terminal Web",
                            tint = if (isWebRunning) Color(0xFF00E676) else theme.primaryText.copy(alpha = 0.7f),
                            modifier = Modifier.size(17.dp)
                        )
                        if (isWebRunning) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onOpenLogs,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("logs_top_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Logs y Auditoría de Seguridad",
                        tint = theme.promptUser,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onOpenQuickActions,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("quick_actions_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Acciones Rápidas",
                        tint = theme.accentText,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Limpiar pantalla",
                        tint = theme.primaryText.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = theme.primaryText,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TerminalInputBar(
    input: String,
    sessionType: SessionType,
    theme: com.example.model.TerminalThemePreset,
    fontSizeSp: Int,
    isExecuting: Boolean,
    onInputChange: (String) -> Unit,
    onExecute: () -> Unit
) {
    val promptPrefix = when (sessionType) {
        SessionType.SHELL -> "práctica$"
        SessionType.CLAUDE -> "claude>"
        SessionType.OPENCLAW -> "openclaw>"
    }

    val promptColor = when (sessionType) {
        SessionType.SHELL -> Color(0xFFFFB300)
        SessionType.CLAUDE -> theme.claudeColor
        SessionType.OPENCLAW -> theme.openClawColor
    }

    Surface(
        color = theme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$promptPrefix ",
                color = promptColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = fontSizeSp.sp
            )

            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                textStyle = TextStyle(
                    color = theme.primaryText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp
                ),
                cursorBrush = SolidColor(theme.primaryText),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onExecute() }
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .testTag("terminal_input_field")
            )

            IconButton(
                onClick = onExecute,
                enabled = input.isNotBlank() && !isExecuting,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Ejecutar comando",
                    tint = if (input.isNotBlank() && !isExecuting) theme.primaryText else theme.primaryText.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
