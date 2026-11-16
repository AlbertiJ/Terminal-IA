package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.TerminalThemePreset
import com.example.ssh.SshConnectionError
import com.example.ssh.SshDiagnosticResult

@Composable
fun TermuxSetupWizardDialog(
    theme: TerminalThemePreset,
    isTermuxInstalled: Boolean,
    setupCommandCopied: Boolean,
    sshPort: Int,
    sshUsername: String,
    publicKeyFingerprint: String,
    isTestingConnection: Boolean,
    diagnosticResult: SshDiagnosticResult?,
    pendingHostKeyMismatch: SshConnectionError.HostKeyChanged?,
    onRefreshTermuxStatus: () -> Unit,
    onOpenTermuxDownload: () -> Unit,
    onLaunchTermux: () -> Unit,
    onUpdatePortAndUser: (Int, String) -> Unit,
    onCopySetupCommand: (port: Int) -> String,
    onTestConnection: (port: Int, username: String) -> Unit,
    onAcceptChangedHostKey: (SshConnectionError.HostKeyChanged) -> Unit,
    onDismissHostKeyMismatch: () -> Unit,
    onConnectShellAndClose: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var portText by remember(sshPort) { mutableStateOf(sshPort.toString()) }
    var userText by remember(sshUsername) { mutableStateOf(sshUsername) }
    var portError by remember { mutableStateOf<String?>(null) }
    var copiedBanner by remember { mutableStateOf(false) }

    val parsedPort = portText.trim().toIntOrNull()
    val validPort = parsedPort != null && parsedPort in 1024..65535
    val isConnectionOk = diagnosticResult is SshDiagnosticResult.Success

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, theme.accentText.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .testTag("termux_setup_wizard_dialog"),
            color = theme.background
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Asistente de Conexión Termux",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = theme.primaryText
                            )
                            Text(
                                text = "Shell Linux real vía SSH local (127.0.0.1)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = theme.primaryText.copy(alpha = 0.7f)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("wizard_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar asistente",
                            tint = theme.primaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = theme.surface)
                Spacer(modifier = Modifier.height(12.dp))

                // PASO 1: Detectar e instalar Termux
                WizardStepCard(
                    stepNumber = 1,
                    title = "Instalar Termux (F-Droid / GitHub)",
                    isDone = isTermuxInstalled,
                    theme = theme
                ) {
                    Text(
                        text = if (isTermuxInstalled) {
                            "Paquete com.termux detectado en el dispositivo."
                        } else {
                            "Terminal-IA no incluye Termux dentro del APK: se conecta al Termux real instalado en tu equipo. Descárgalo desde F-Droid (no Google Play)."
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = theme.primaryText.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isTermuxInstalled) {
                            Button(
                                onClick = onOpenTermuxDownload,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E676),
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("wizard_download_termux_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Descargar Termux",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = onLaunchTermux,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primaryText),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("wizard_open_termux_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Abrir Termux",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onRefreshTermuxStatus,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accentText),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("wizard_refresh_termux_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Verificar Termux nuevamente",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // PASO 2: Copiar comando único idempotente y configurar puerto
                val step2Done = setupCommandCopied || isConnectionOk
                WizardStepCard(
                    stepNumber = 2,
                    title = "Autorizar clave Ed25519 y activar sshd",
                    isDone = step2Done,
                    theme = theme
                ) {
                    Text(
                        text = "La clave privada nunca sale del Keystore de esta app. Copia el comando, pégalo en Termux y presiona Enter:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = theme.primaryText.copy(alpha = 0.85f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.background)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = theme.accentText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Huella Ed25519: $publicKeyFingerprint",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = theme.accentText
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { raw ->
                                portText = raw.filter { it.isDigit() }.take(5)
                                val p = portText.toIntOrNull()
                                portError = if (p == null || p !in 1024..65535) {
                                    "Puerto 1024..65535"
                                } else {
                                    onUpdatePortAndUser(p, userText)
                                    null
                                }
                            },
                            label = { Text("Puerto SSH", fontSize = 10.sp) },
                            isError = portError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.primaryText,
                                unfocusedBorderColor = theme.background,
                                focusedTextColor = theme.primaryText,
                                unfocusedTextColor = theme.primaryText
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wizard_ssh_port_input")
                        )

                        OutlinedTextField(
                            value = userText,
                            onValueChange = { raw ->
                                userText = raw.replace(" ", "")
                                val p = parsedPort ?: sshPort
                                if (p in 1024..65535) {
                                    onUpdatePortAndUser(p, userText)
                                }
                            },
                            label = { Text("Usuario (opcional)", fontSize = 10.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.primaryText,
                                unfocusedBorderColor = theme.background,
                                focusedTextColor = theme.primaryText,
                                unfocusedTextColor = theme.primaryText
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wizard_ssh_user_input")
                        )
                    }

                    if (portError != null) {
                        Text(
                            text = portError!!,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFFFF5252),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val targetPort = parsedPort ?: return@Button
                            val cmd = onCopySetupCommand(targetPort)
                            clipboard.setText(AnnotatedString(cmd))
                            copiedBanner = true
                        },
                        enabled = validPort,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primaryText,
                            contentColor = theme.background
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("wizard_copy_setup_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (copiedBanner || setupCommandCopied) {
                                "Comando copiado (volver a copiar)"
                            } else {
                                "Copiar comando de configuración"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "• Instala openssh, fija ListenAddress 127.0.0.1, desactiva contraseñas y habilita PubkeyAuthentication sin duplicar líneas.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = theme.primaryText.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // PASO 3: Probar conexión y diagnóstico exacto
                WizardStepCard(
                    stepNumber = 3,
                    title = "Probar conexión SSH local",
                    isDone = isConnectionOk,
                    theme = theme
                ) {
                    Text(
                        text = "Verifica el handshake SSH real en 127.0.0.1:${parsedPort ?: sshPort} y comprueba la huella del servidor (TOFU).",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = theme.primaryText.copy(alpha = 0.85f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val targetPort = parsedPort ?: sshPort
                            onTestConnection(targetPort, userText.ifBlank { "termux" })
                        },
                        enabled = validPort && !isTestingConnection,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.accentText,
                            contentColor = theme.background
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("wizard_test_connection_button")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = theme.background
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Probando SSH en 127.0.0.1...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Probar conexión",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (diagnosticResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        when (diagnosticResult) {
                            is SshDiagnosticResult.Success -> {
                                Surface(
                                    color = Color(0xFF00E676).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E676)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("wizard_diagnostic_success")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "CONEXIÓN VERIFICADA (${diagnosticResult.host}:${diagnosticResult.port})",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color(0xFF00E676)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• Host key (${diagnosticResult.hostAlgorithm}): ${diagnosticResult.hostFingerprintSha256}\n" +
                                                if (diagnosticResult.firstTimeTrusted) {
                                                    "• Guardada por primera vez (TOFU)."
                                                } else {
                                                    "• Coincide con la clave confiable guardada."
                                                },
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = theme.primaryText
                                        )
                                    }
                                }
                            }
                            is SshDiagnosticResult.Failure -> {
                                Surface(
                                    color = Color(0xFFFF5252).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("wizard_diagnostic_error")
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
                                            text = diagnosticResult.error.toUserMessage(),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = Color(0xFFFF8A80)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (pendingHostKeyMismatch != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HostKeyMismatchWarningCard(
                            mismatch = pendingHostKeyMismatch,
                            theme = theme,
                            onAcceptNewKey = { onAcceptChangedHostKey(pendingHostKeyMismatch) },
                            onCancel = onDismissHostKeyMismatch
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primaryText),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Text("Cerrar", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }

                    Button(
                        onClick = onConnectShellAndClose,
                        enabled = isTermuxInstalled && validPort,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("wizard_connect_shell_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Abrir Shell Real",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardStepCard(
    stepNumber: Int,
    title: String,
    isDone: Boolean,
    theme: TerminalThemePreset,
    content: @Composable () -> Unit
) {
    val badgeColor = if (isDone) Color(0xFF00E676) else Color(0xFFFFB300)
    Surface(
        color = theme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PASO $stepNumber: $title",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = theme.primaryText
                    )
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, badgeColor)
                ) {
                    Text(
                        text = if (isDone) "HECHO" else "FALTA",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun HostKeyMismatchWarningCard(
    mismatch: SshConnectionError.HostKeyChanged,
    theme: TerminalThemePreset,
    onAcceptNewKey: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = Color(0xFFFF5252).copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFFF5252)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("host_key_mismatch_card")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "CAMBIO DE CLAVE DE SERVIDOR (${mismatch.host}:${mismatch.port})",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color(0xFFFF5252)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Esperada: ${mismatch.expectedFingerprint}\nActual:   ${mismatch.actualFingerprint}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = theme.primaryText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primaryText),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                ) {
                    Text("Cancelar", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
                Button(
                    onClick = onAcceptNewKey,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("accept_changed_host_key_button")
                ) {
                    Text("Confiar en nueva clave", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
