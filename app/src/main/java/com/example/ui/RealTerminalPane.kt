package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TerminalThemePreset
import com.example.ssh.SshConnectionError
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulatorFactory

sealed class RealSshConnectionState {
    data object Disconnected : RealSshConnectionState()
    data object Connecting : RealSshConnectionState()
    data class Connected(
        val host: String,
        val port: Int,
        val hostFingerprint: String
    ) : RealSshConnectionState()
    data class Error(val error: SshConnectionError) : RealSshConnectionState()
}

interface RealTerminalByteSink {
    fun onBytesReceived(bytes: ByteArray, length: Int)
}

@Composable
fun RealTerminalPane(
    theme: TerminalThemePreset,
    fontSizeSp: Int,
    sshPort: Int,
    isTermuxInstalled: Boolean,
    connectionState: RealSshConnectionState,
    pendingHostKeyMismatch: SshConnectionError.HostKeyChanged?,
    onRegisterByteSink: (RealTerminalByteSink?) -> Unit,
    onKeyboardInputBytes: (ByteArray) -> Unit,
    onViewportResize: (cols: Int, rows: Int) -> Unit,
    onConnect: (cols: Int, rows: Int) -> Unit,
    onDisconnect: () -> Unit,
    onOpenWizard: () -> Unit,
    onSwitchToPracticeMode: () -> Unit,
    onAcceptChangedHostKey: (SshConnectionError.HostKeyChanged) -> Unit,
    onDismissHostKeyMismatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emulator = remember(theme) {
        TerminalEmulatorFactory.create(
            defaultForeground = theme.primaryText,
            defaultBackground = theme.background,
            onKeyboardInput = { data ->
                onKeyboardInputBytes(data)
            },
            onResize = { dims ->
                onViewportResize(dims.columns, dims.rows)
            }
        )
    }

    DisposableEffect(emulator) {
        val sink = object : RealTerminalByteSink {
            override fun onBytesReceived(bytes: ByteArray, length: Int) {
                emulator.writeInput(bytes, 0, length)
            }
        }
        onRegisterByteSink(sink)
        onDispose {
            onRegisterByteSink(null)
            (emulator as? AutoCloseable)?.close()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .testTag("real_terminal_pane")
    ) {
        // Real mode status strip
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val dotColor = when (connectionState) {
                        is RealSshConnectionState.Connected -> Color(0xFF00E676)
                        is RealSshConnectionState.Connecting -> Color(0xFFFFB300)
                        is RealSshConnectionState.Error -> Color(0xFFFF5252)
                        is RealSshConnectionState.Disconnected -> theme.primaryText.copy(alpha = 0.45f)
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (connectionState) {
                            is RealSshConnectionState.Connected ->
                                "SSH REAL · 127.0.0.1:${connectionState.port}"
                            is RealSshConnectionState.Connecting ->
                                "Conectando SSH a Termux (:$sshPort)..."
                            is RealSshConnectionState.Error ->
                                "SSH desconectado (revisa diagnóstico)"
                            is RealSshConnectionState.Disconnected ->
                                if (isTermuxInstalled) "Termux listo · Desconectado (:$sshPort)" else "Falta instalar Termux"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.primaryText
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onOpenWizard,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accentText),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("open_wizard_inline_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Asistente", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }

                    if (connectionState is RealSshConnectionState.Connected) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("disconnect_ssh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = "Desconectar SSH",
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // Host key mismatch explicit alert if triggered during reconnect
        if (pendingHostKeyMismatch != null) {
            Box(modifier = Modifier.padding(8.dp)) {
                HostKeyMismatchWarningCard(
                    mismatch = pendingHostKeyMismatch,
                    theme = theme,
                    onAcceptNewKey = { onAcceptChangedHostKey(pendingHostKeyMismatch) },
                    onCancel = onDismissHostKeyMismatch
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val density = LocalDensity.current
            val estimatedCharWidthPx = with(density) { (fontSizeSp * 0.62f).sp.toPx().coerceAtLeast(6f) }
            val estimatedLineHeightPx = with(density) { (fontSizeSp * 1.35f).sp.toPx().coerceAtLeast(12f) }
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val estimatedCols = (widthPx / estimatedCharWidthPx).toInt().coerceIn(30, 240)
            val estimatedRows = (heightPx / estimatedLineHeightPx).toInt().coerceIn(8, 120)

            LaunchedEffect(estimatedCols, estimatedRows, connectionState is RealSshConnectionState.Connected) {
                if (connectionState is RealSshConnectionState.Connected) {
                    onViewportResize(estimatedCols, estimatedRows)
                }
            }

            // Always mount the real libvterm Terminal surface when connected (or once output exists)
            if (connectionState is RealSshConnectionState.Connected) {
                Terminal(
                    terminalEmulator = emulator,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("termlib_terminal_view")
                )
            } else {
                // Disconnected / Error / Connecting onboarding card inside the pane
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = theme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, theme.accentText.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Terminal Linux Real (vía Termux SSH)",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = theme.primaryText
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ejecuta bash, pkg, python, git, vim, htop y tmux reales conectados al sshd de Termux en 127.0.0.1:$sshPort con PTY VT100/xterm-256color.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = theme.primaryText.copy(alpha = 0.8f)
                            )

                            if (connectionState is RealSshConnectionState.Error) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = Color(0xFFFF5252).copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = connectionState.error.toUserMessage(),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = Color(0xFFFF8A80)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onConnect(estimatedCols, estimatedRows) },
                                    enabled = connectionState !is RealSshConnectionState.Connecting,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00E676),
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("connect_real_terminal_button")
                                ) {
                                    if (connectionState is RealSshConnectionState.Connecting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.Black
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Conectando...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Conectar SSH", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = onOpenWizard,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accentText),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("open_setup_wizard_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Asistente (3 pasos)", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onSwitchToPracticeMode,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFFFB300)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("switch_to_practice_mode_button")
                            ) {
                                Text(
                                    text = "Cambiar a Modo Práctica (Simulado)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
