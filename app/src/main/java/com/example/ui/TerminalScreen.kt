package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.viewmodel.TerminalViewModel

@Composable
fun TerminalScreen(viewModel: TerminalViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val theme = uiState.settings.terminalTheme
    val listState = rememberLazyListState()
    val agentListState = rememberLazyListState()

    val currentSession = uiState.sessions.firstOrNull { it.id == uiState.activeSessionId }
    val currentLines = uiState.sessionLines[uiState.activeSessionId] ?: emptyList()

    // Filter agent-specific lines for Split Screen Monitor
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
                currentSessionTitle = currentSession?.title ?: "bash",
                currentPath = currentSession?.currentDirectory ?: "/home/user",
                isExecuting = uiState.isExecuting,
                isRemoteServerRunning = uiState.isRemoteServerRunning,
                isWebRunning = uiState.isWebTerminalRunning,
                isSplitScreenMode = uiState.isSplitScreenMode,
                pulseAlpha = alphaAnim,
                theme = theme,
                onOpenSettings = { viewModel.openSettingsDialog() },
                onOpenRemoteAccess = { viewModel.openRemoteAccessDialog() },
                onOpenFileManager = { viewModel.openFileManager() },
                onOpenScriptManager = { viewModel.openScriptManager() },
                onToggleSplitScreen = { viewModel.toggleSplitScreen() },
                onOpenQuickActions = { viewModel.openQuickActions() },
                onClear = { viewModel.onExtraKeyPressed("clear") }
            )

            // Multi-tab Session Bar
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
                // Split Screen Mode: Upper half is shell, lower half is Agent Monitor
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Top Half: Shell Buffer
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

                    // Split Divider with title
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
                                text = "MONITOR DIVIDIDO: AGENTES CLAUDE & OPENCLAW EN VIVO",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.openClawColor
                            )
                        }
                    }

                    // Bottom Half: Agent Telemetry & Reasoning Inspector
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
                // Standard Single Console View
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
                                    text = "Procesando respuesta del agente / shell...",
                                    color = theme.primaryText.copy(alpha = 0.8f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Command Input Row
            TerminalInputBar(
                input = uiState.input,
                currentDir = currentSession?.currentDirectory ?: "/home/user",
                sessionType = currentSession?.type ?: SessionType.SHELL,
                theme = theme,
                fontSizeSp = uiState.settings.fontSizeSp,
                isExecuting = uiState.isExecuting,
                onInputChange = { viewModel.onInputChange(it) },
                onExecute = { viewModel.executeCurrentCommand() }
            )

            // Termux Extra Keys Bar (Virtual row above keyboard)
            ExtraKeysBar(
                theme = theme,
                onKeyPressed = { viewModel.onExtraKeyPressed(it) }
            )
        }

        // Dialogs
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
                    viewModel.onInputChange("bash $path")
                    viewModel.executeCurrentCommand()
                }
            )
        }

        if (uiState.showRemoteAccessDialog) {
            RemoteAccessDialog(
                theme = theme,
                isServerRunning = uiState.isRemoteServerRunning,
                serverPort = uiState.remoteServerPort,
                isWebRunning = uiState.isWebTerminalRunning,
                webPort = uiState.webTerminalPort,
                deviceIps = uiState.deviceIps,
                currentUsername = uiState.remoteUsername,
                hasCredentials = uiState.hasRemoteCredentials,
                onToggleServer = { enabled, port -> viewModel.toggleRemoteServer(enabled, port) },
                onToggleWebServer = { enabled, port -> viewModel.toggleWebTerminal(enabled, port) },
                onSaveCredentials = { user, pass -> viewModel.saveRemoteCredentials(user, pass) },
                onDismiss = { viewModel.closeRemoteAccessDialog() }
            )
        }

        if (uiState.showQuickActions) {
            QuickActionsDialog(
                theme = theme,
                onDismiss = { viewModel.closeQuickActions() },
                onSelectAction = { cmd ->
                    viewModel.onInputChange(cmd)
                    viewModel.executeCurrentCommand()
                }
            )
        }
    }
}

@Composable
fun TerminalTopBar(
    currentSessionTitle: String,
    currentPath: String,
    isExecuting: Boolean,
    isRemoteServerRunning: Boolean,
    isWebRunning: Boolean,
    isSplitScreenMode: Boolean,
    pulseAlpha: Float,
    theme: com.example.model.TerminalThemePreset,
    onOpenSettings: () -> Unit,
    onOpenRemoteAccess: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenScriptManager: () -> Unit,
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
                    contentDescription = "Termux AI",
                    tint = theme.primaryText,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TERMUX AI",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = theme.primaryText
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Directory badge
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
                // Online/Status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isExecuting) theme.accentText else Color(0xFF00E676))
                        .alpha(if (isExecuting) pulseAlpha else 1.0f)
                )
                Spacer(modifier = Modifier.width(4.dp))

                // File Explorer button
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

                // Script Manager (.sh) button
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

                // Split Screen toggle button
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

                // Remote Access SSH/Web button
                IconButton(
                    onClick = onOpenRemoteAccess,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("remote_access_button")
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = Icons.Default.Lan,
                            contentDescription = "Acceso Remoto SSH / Web",
                            tint = if (isRemoteServerRunning || isWebRunning) Color(0xFF00E676) else theme.primaryText.copy(alpha = 0.7f),
                            modifier = Modifier.size(17.dp)
                        )
                        if (isRemoteServerRunning || isWebRunning) {
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
    currentDir: String,
    sessionType: SessionType,
    theme: com.example.model.TerminalThemePreset,
    fontSizeSp: Int,
    isExecuting: Boolean,
    onInputChange: (String) -> Unit,
    onExecute: () -> Unit
) {
    val promptPrefix = when (sessionType) {
        SessionType.SHELL -> "$"
        SessionType.CLAUDE -> "claude>"
        SessionType.OPENCLAW -> "openclaw>"
    }

    val promptColor = when (sessionType) {
        SessionType.SHELL -> theme.promptUser
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
                    autoCorrect = false
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
