package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.TerminalThemePreset

@Composable
fun RemoteAccessDialog(
    theme: TerminalThemePreset,
    isServerRunning: Boolean,
    serverPort: Int,
    isWebRunning: Boolean,
    webPort: Int,
    deviceIps: List<String>,
    currentUsername: String,
    hasCredentials: Boolean,
    onToggleServer: (Boolean, Int) -> Unit,
    onToggleWebServer: (Boolean, Int) -> Unit,
    onSaveCredentials: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    var copiedIp by remember { mutableStateOf(false) }
    var copiedWeb by remember { mutableStateOf(false) }

    var showCredentialForm by remember { mutableStateOf(!hasCredentials) }
    var usernameInput by remember { mutableStateOf(currentUsername) }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val primaryIp = deviceIps.firstOrNull() ?: "192.168.1.X"
    val sshCommand = "ssh $currentUsername@$primaryIp -p $serverPort"
    val webUrl = "http://$primaryIp:$webPort/"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, theme.accentText.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .testTag("remote_access_dialog"),
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
                            imageVector = Icons.Default.Lan,
                            contentDescription = null,
                            tint = theme.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Acceso Remoto: Web & SSH",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = theme.primaryText
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = theme.primaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = theme.surface)
                Spacer(modifier = Modifier.height(10.dp))

                // 1. WEB TERMINAL SECTION
                Text(
                    text = "1. CONSOLA TERMINAL WEB (NAVEGADOR):",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.openClawColor
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = theme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isWebRunning) Color(0xFF00E676).copy(alpha = 0.5f) else theme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isWebRunning) Color(0xFF00E676) else Color(0xFFFF5252))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isWebRunning) "TERMINAL WEB ACTIVA (:8080)" else "TERMINAL WEB DETENIDA",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isWebRunning) Color(0xFF00E676) else Color(0xFFFF5252)
                                    )
                                }
                                Text(
                                    text = "Conéctate desde Chrome, Safari o Firefox con login seguro",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = theme.primaryText.copy(alpha = 0.7f)
                                )
                            }

                            Switch(
                                checked = isWebRunning,
                                onCheckedChange = { checked ->
                                    onToggleWebServer(checked, 8080)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = theme.primaryText,
                                    checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.5f)
                                )
                            )
                        }

                        if (isWebRunning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(theme.background)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = webUrl,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = theme.openClawColor
                                )
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(webUrl))
                                        copiedWeb = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (copiedWeb) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copiar URL",
                                        tint = if (copiedWeb) Color(0xFF00E676) else theme.primaryText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. SSH / TELNET SERVER SECTION
                Text(
                    text = "2. SERVIDOR SSH / TELNET (CLI):",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.promptPath
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = theme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isServerRunning) Color(0xFF00E676) else Color(0xFFFF5252))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isServerRunning) "SSH ACTIVO (:$serverPort)" else "SSH DETENIDO",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isServerRunning) Color(0xFF00E676) else Color(0xFFFF5252)
                                    )
                                }
                                Text(
                                    text = "Conéctate por terminal desde PC, Mac o Linux",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = theme.primaryText.copy(alpha = 0.7f)
                                )
                            }

                            Switch(
                                checked = isServerRunning,
                                onCheckedChange = { checked ->
                                    onToggleServer(checked, serverPort)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = theme.primaryText,
                                    checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.5f)
                                )
                            )
                        }

                        if (isServerRunning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(theme.background)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sshCommand,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = theme.accentText
                                )
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(sshCommand))
                                        copiedIp = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (copiedIp) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copiar SSH",
                                        tint = if (copiedIp) Color(0xFF00E676) else theme.primaryText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. CREDENTIALS & PBKDF2 SECURITY
                Text(
                    text = "3. CREDENCIALES DE ACCESO REMOTO:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.claudeColor
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = theme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasCredentials) Icons.Default.Lock else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (hasCredentials) Color(0xFF00E676) else Color(0xFFFFB300),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Usuario: $currentUsername",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = theme.primaryText
                                    )
                                    Text(
                                        text = if (hasCredentials) "Contraseña cifrada con Salt PBKDF2" else "¡Sin clave configurada!",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = if (hasCredentials) Color(0xFF00E676) else Color(0xFFFFB300)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showCredentialForm = !showCredentialForm },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primaryText)
                            ) {
                                Text(
                                    text = if (showCredentialForm) "Ocultar" else if (hasCredentials) "Cambiar" else "Crear Clave",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (showCredentialForm) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = theme.background)
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = { usernameInput = it },
                                label = { Text("Usuario") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.primaryText,
                                    unfocusedBorderColor = theme.background,
                                    focusedTextColor = theme.primaryText,
                                    unfocusedTextColor = theme.primaryText
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Nueva contraseña") },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.primaryText,
                                    unfocusedBorderColor = theme.background,
                                    focusedTextColor = theme.primaryText,
                                    unfocusedTextColor = theme.primaryText
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = confirmPasswordInput,
                                onValueChange = { confirmPasswordInput = it },
                                label = { Text("Confirmar contraseña") },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.primaryText,
                                    unfocusedBorderColor = theme.background,
                                    focusedTextColor = theme.primaryText,
                                    unfocusedTextColor = theme.primaryText
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errorMessage!!,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFFFF5252)
                                )
                            }

                            if (successMessage != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = successMessage!!,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFF00E676)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    if (passwordInput.length < 4) {
                                        errorMessage = "La contraseña debe tener mínimo 4 caracteres."
                                        return@Button
                                    }
                                    if (passwordInput != confirmPasswordInput) {
                                        errorMessage = "Las contraseñas no coinciden."
                                        return@Button
                                    }
                                    errorMessage = null
                                    onSaveCredentials(usernameInput.trim(), passwordInput)
                                    successMessage = "¡Credenciales guardadas y cifradas exitosamente!"
                                    passwordInput = ""
                                    confirmPasswordInput = ""
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.primaryText,
                                    contentColor = theme.background
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Guardar Credenciales Seguras", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primaryText,
                            contentColor = theme.background
                        )
                    ) {
                        Text("Cerrar", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
