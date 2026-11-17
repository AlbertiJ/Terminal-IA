package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.TerminalPreferences
import com.example.model.TerminalThemePreset
import com.example.security.SecurityManager

@Composable
fun RemoteAccessDialog(
    theme: TerminalThemePreset,
    isWebRunning: Boolean,
    webPort: Int,
    isLanAccessEnabled: Boolean,
    isHttpsEnabled: Boolean,
    isRealSshConnected: Boolean,
    deviceIps: List<String>,
    currentUsername: String,
    hasCredentials: Boolean,
    onToggleWebServer: (Boolean, Int) -> Unit,
    onToggleLanAccess: (Boolean) -> Unit,
    onToggleHttps: (Boolean) -> Unit,
    onSaveCredentials: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    var copiedWeb by remember { mutableStateOf(false) }

    var showCredentialForm by remember { mutableStateOf(!hasCredentials) }
    var usernameInput by remember(currentUsername) { mutableStateOf(currentUsername) }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val scheme = if (isHttpsEnabled) "https" else "http"
    val hostForUrl = if (isLanAccessEnabled) {
        deviceIps.firstOrNull { it != "127.0.0.1" } ?: deviceIps.firstOrNull() ?: "127.0.0.1"
    } else {
        "127.0.0.1"
    }
    val webUrl = "$scheme://$hostForUrl:$webPort/"

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
                            text = "Consola Web Segura",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = theme.primaryText
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = theme.primaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = theme.surface)
                Spacer(modifier = Modifier.height(10.dp))

                if (!isRealSshConnected) {
                    Surface(
                        color = Color(0xFFFFB300).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Modo Real (Termux SSH) desconectado. La consola web solo ejecuta comandos reales en Termux; nunca expone el modo práctica.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFFFFD54F)
                            )
                        }
                    }
                }

                // 1. WEB TERMINAL SECTION
                Text(
                    text = "1. SERVIDOR WEB TERMINAL:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.openClawColor
                )
                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = theme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isWebRunning) Color(0xFF00E676).copy(alpha = 0.5f) else theme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isWebRunning) Color(0xFF00E676) else Color(0xFFFF5252))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isWebRunning) "TERMINAL WEB ACTIVA (:$webPort)" else "TERMINAL WEB DETENIDA",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isWebRunning) Color(0xFF00E676) else Color(0xFFFF5252)
                                    )
                                }
                                Text(
                                    text = if (hasCredentials) {
                                        if (isLanAccessEnabled) "Escuchando en LAN (0.0.0.0) con sesión autenticada"
                                        else "Escuchando solo localmente (127.0.0.1)"
                                    } else {
                                        "Crea usuario y clave abajo primero (obligatorio)"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = theme.primaryText.copy(alpha = 0.75f)
                                )
                            }

                            Switch(
                                checked = isWebRunning,
                                enabled = hasCredentials || isWebRunning,
                                onCheckedChange = { checked ->
                                    onToggleWebServer(checked, webPort)
                                },
                                modifier = Modifier.testTag("web_server_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = theme.primaryText,
                                    checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.5f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = theme.background)
                        Spacer(modifier = Modifier.height(8.dp))

                        // LAN Exposure Switch (OFF by default -> 127.0.0.1 only)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Permitir acceso desde red local (LAN)",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isLanAccessEnabled) Color(0xFFFFB300) else theme.primaryText
                                )
                                Text(
                                    text = if (isLanAccessEnabled) {
                                        "Expuesto en Wi-Fi/LAN (0.0.0.0). Úsalo solo tras VPN o red confiable."
                                    } else {
                                        "Apagado (seguro): ligado únicamente a 127.0.0.1."
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = theme.primaryText.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = isLanAccessEnabled,
                                onCheckedChange = onToggleLanAccess,
                                modifier = Modifier.testTag("web_lan_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFFB300),
                                    checkedTrackColor = Color(0xFFFFB300).copy(alpha = 0.4f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // HTTPS / TLS Switch (adds Secure cookie attribute)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = if (isHttpsEnabled) Color(0xFF00E676) else theme.primaryText.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Cifrado HTTPS (TLS local + cookie Secure)",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = theme.primaryText
                                    )
                                }
                                Text(
                                    text = "Activa TLS con certificado local y atributo Secure en la cookie.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = theme.primaryText.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = isHttpsEnabled,
                                onCheckedChange = onToggleHttps,
                                modifier = Modifier.testTag("web_https_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00E676),
                                    checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.4f)
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
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (copiedWeb) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copiar URL",
                                        tint = if (copiedWeb) Color(0xFF00E676) else theme.primaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. CREDENTIALS & PBKDF2 SECURITY (APP-ONLY)
                Text(
                    text = "2. CREDENCIALES WEB (SOLO DESDE LA APP):",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.claudeColor
                )
                Spacer(modifier = Modifier.height(6.dp))

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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (hasCredentials) Icons.Default.Lock else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (hasCredentials) Color(0xFF00E676) else Color(0xFFFFB300),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = if (hasCredentials) "Usuario: $currentUsername" else "Sin credenciales configuradas",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = theme.primaryText
                                    )
                                    Text(
                                        text = if (hasCredentials) {
                                            "PBKDF2-SHA256 (${SecurityManager.CURRENT_PBKDF2_ITERATIONS} iter.)"
                                        } else {
                                            "Obligatorio: usuario propio y clave >= ${TerminalPreferences.MIN_REMOTE_PASSWORD_LENGTH} caracteres"
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = if (hasCredentials) Color(0xFF00E676) else Color(0xFFFFB300)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showCredentialForm = !showCredentialForm },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primaryText),
                                modifier = Modifier.minimumInteractiveComponentSize()
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
                            HorizontalDivider(color = theme.background)
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = { usernameInput = it },
                                label = { Text("Usuario (sin valor por defecto)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.primaryText,
                                    unfocusedBorderColor = theme.background,
                                    focusedTextColor = theme.primaryText,
                                    unfocusedTextColor = theme.primaryText
                                ),
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("remote_username_input")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Contraseña (mínimo ${TerminalPreferences.MIN_REMOTE_PASSWORD_LENGTH} caracteres)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.primaryText,
                                    unfocusedBorderColor = theme.background,
                                    focusedTextColor = theme.primaryText,
                                    unfocusedTextColor = theme.primaryText
                                ),
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("remote_password_input")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = confirmPasswordInput,
                                onValueChange = { confirmPasswordInput = it },
                                label = { Text("Confirmar contraseña") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.primaryText,
                                    unfocusedBorderColor = theme.background,
                                    focusedTextColor = theme.primaryText,
                                    unfocusedTextColor = theme.primaryText
                                ),
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("remote_confirm_password_input")
                            )

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = errorMessage!!,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFFFF5252)
                                )
                            }

                            if (successMessage != null) {
                                Spacer(modifier = Modifier.height(6.dp))
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
                                    val cleanUser = usernameInput.trim()
                                    successMessage = null
                                    if (cleanUser.isBlank()) {
                                        errorMessage = "Debes ingresar un nombre de usuario."
                                        return@Button
                                    }
                                    if (passwordInput.length < TerminalPreferences.MIN_REMOTE_PASSWORD_LENGTH) {
                                        errorMessage = "La contraseña debe tener mínimo ${TerminalPreferences.MIN_REMOTE_PASSWORD_LENGTH} caracteres."
                                        return@Button
                                    }
                                    if (passwordInput != confirmPasswordInput) {
                                        errorMessage = "Las contraseñas no coinciden."
                                        return@Button
                                    }
                                    errorMessage = null
                                    onSaveCredentials(cleanUser, passwordInput)
                                    successMessage = "¡Credenciales cifradas con ${SecurityManager.CURRENT_PBKDF2_ITERATIONS} iteraciones PBKDF2!"
                                    passwordInput = ""
                                    confirmPasswordInput = ""
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.primaryText,
                                    contentColor = theme.background
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_remote_credentials_button")
                            ) {
                                Text(
                                    "Guardar Credenciales Seguras",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Cerrar", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
