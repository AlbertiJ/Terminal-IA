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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AgentSettings
import com.example.model.TerminalThemePreset

@Composable
fun AgentSettingsDialog(
    currentSettings: AgentSettings,
    onDismiss: () -> Unit,
    onSave: (AgentSettings) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    var claudeApiKey by remember { mutableStateOf(currentSettings.claudeApiKey) }
    var claudeModel by remember { mutableStateOf(currentSettings.claudeModel) }
    var claudeSystemPrompt by remember { mutableStateOf(currentSettings.claudeSystemPrompt) }
    var showClaudeKey by remember { mutableStateOf(false) }

    var openClawEndpoint by remember { mutableStateOf(currentSettings.openClawEndpoint) }
    var openClawToken by remember { mutableStateOf(currentSettings.openClawToken) }
    var openClawAutonomous by remember { mutableStateOf(currentSettings.openClawAutonomous) }

    var selectedTheme by remember { mutableStateOf(currentSettings.terminalTheme) }
    var fontSizeSp by remember { mutableIntStateOf(currentSettings.fontSizeSp) }
    var enableVibration by remember { mutableStateOf(currentSettings.enableVibration) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, selectedTheme.accentText.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .testTag("agent_settings_dialog"),
            color = selectedTheme.background
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
                            tint = selectedTheme.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ajustes de Termux AI",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = selectedTheme.primaryText
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = selectedTheme.primaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs: Claude, OpenClaw, Terminal
                val tabTitles = listOf("Claude Agent", "OpenClaw", "Apariencia")
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = selectedTheme.surface,
                    contentColor = selectedTheme.primaryText,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = selectedTheme.primaryText
                        )
                    }
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        // Claude Agent Config
                        Text(
                            text = "INTEGRACIÓN CON AGENTE CLAUDE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = selectedTheme.claudeColor
                        )
                        Text(
                            text = "Conexión directa con la API oficial de Anthropic. Si no tienes API Key, la app opera en Modo Libre Sandbox sin ningún costo.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = selectedTheme.primaryText.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = claudeApiKey,
                            onValueChange = { claudeApiKey = it },
                            label = { Text("Anthropic API Key (sk-ant-...)") },
                            placeholder = { Text("Opcional: Dejar vacío para modo offline gratuito") },
                            visualTransformation = if (showClaudeKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showClaudeKey = !showClaudeKey }) {
                                    Icon(
                                        imageVector = if (showClaudeKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Key",
                                        tint = selectedTheme.primaryText
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = selectedTheme.claudeColor,
                                unfocusedBorderColor = selectedTheme.surface,
                                focusedTextColor = selectedTheme.primaryText,
                                unfocusedTextColor = selectedTheme.primaryText,
                                focusedLabelColor = selectedTheme.claudeColor,
                                unfocusedLabelColor = selectedTheme.primaryText.copy(alpha = 0.6f)
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("claude_api_key_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Modelo de Claude:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = selectedTheme.primaryText
                        )
                        val models = listOf(
                            "claude-3-5-sonnet-20241022" to "Claude 3.5 Sonnet (Recomendado)",
                            "claude-3-5-haiku-20241022" to "Claude 3.5 Haiku (Ultra rápido)",
                            "claude-3-opus-20240229" to "Claude 3 Opus (Razonamiento profundo)"
                        )
                        for ((mId, mName) in models) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { claudeModel = mId }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .border(
                                            2.dp,
                                            if (claudeModel == mId) selectedTheme.claudeColor else selectedTheme.primaryText.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .background(if (claudeModel == mId) selectedTheme.claudeColor else Color.Transparent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = mName,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (claudeModel == mId) selectedTheme.primaryText else selectedTheme.primaryText.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = claudeSystemPrompt,
                            onValueChange = { claudeSystemPrompt = it },
                            label = { Text("Instrucción del Sistema (Prompt)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = selectedTheme.claudeColor,
                                unfocusedBorderColor = selectedTheme.surface,
                                focusedTextColor = selectedTheme.primaryText,
                                unfocusedTextColor = selectedTheme.primaryText
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    1 -> {
                        // OpenClaw Agent Config
                        Text(
                            text = "AGENTE AUTÓNOMO OPENCLAW",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = selectedTheme.openClawColor
                        )
                        Text(
                            text = "OpenClaw ejecuta flujos multi-paso autónomos en el shell (auditoría, crawling y ejecución de herramientas). Puede conectarse a un gateway remoto o usar el motor local.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = selectedTheme.primaryText.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = openClawEndpoint,
                            onValueChange = { openClawEndpoint = it },
                            label = { Text("Endpoint de OpenClaw Gateway / Servidor") },
                            placeholder = { Text("https://api.openclaw.io/v1") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = selectedTheme.openClawColor,
                                unfocusedBorderColor = selectedTheme.surface,
                                focusedTextColor = selectedTheme.primaryText,
                                unfocusedTextColor = selectedTheme.primaryText
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = openClawToken,
                            onValueChange = { openClawToken = it },
                            label = { Text("Bearer Token de OpenClaw (Opcional)") },
                            placeholder = { Text("Dejar vacío para motor local") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = selectedTheme.openClawColor,
                                unfocusedBorderColor = selectedTheme.surface,
                                focusedTextColor = selectedTheme.primaryText,
                                unfocusedTextColor = selectedTheme.primaryText
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ejecución autónoma de herramientas",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = selectedTheme.primaryText
                                )
                                Text(
                                    text = "Permitir a OpenClaw planificar y ejecutar pasos de diagnóstico automáticamente",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = selectedTheme.primaryText.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = openClawAutonomous,
                                onCheckedChange = { openClawAutonomous = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = selectedTheme.openClawColor,
                                    checkedTrackColor = selectedTheme.surface
                                )
                            )
                        }
                    }

                    2 -> {
                        // Appearance and themes
                        Text(
                            text = "TEMAS DE COLOR DEL TERMINAL",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = selectedTheme.primaryText
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        for (preset in TerminalThemePreset.values()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedTheme == preset) preset.surface else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (selectedTheme == preset) preset.accentText else preset.surface,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedTheme = preset }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(preset.primaryText)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = preset.displayName,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = preset.primaryText
                                    )
                                }
                                if (selectedTheme == preset) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = preset.primaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Tamaño de fuente: ${fontSizeSp}sp",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = selectedTheme.primaryText
                        )
                        Slider(
                            value = fontSizeSp.toFloat(),
                            onValueChange = { fontSizeSp = it.toInt() },
                            valueRange = 10f..20f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = selectedTheme.primaryText,
                                activeTrackColor = selectedTheme.accentText
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Vibración háptica en teclas",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = selectedTheme.primaryText
                            )
                            Switch(
                                checked = enableVibration,
                                onCheckedChange = { enableVibration = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = selectedTheme.primaryText,
                                    checkedTrackColor = selectedTheme.surface
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = selectedTheme.surface)
                Spacer(modifier = Modifier.height(12.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = selectedTheme.primaryText)
                    ) {
                        Text("Cancelar", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = currentSettings.copy(
                                claudeApiKey = claudeApiKey.trim(),
                                claudeModel = claudeModel,
                                claudeSystemPrompt = claudeSystemPrompt.trim(),
                                openClawEndpoint = openClawEndpoint.trim(),
                                openClawToken = openClawToken.trim(),
                                openClawAutonomous = openClawAutonomous,
                                terminalTheme = selectedTheme,
                                fontSizeSp = fontSizeSp,
                                enableVibration = enableVibration
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = selectedTheme.primaryText,
                            contentColor = selectedTheme.background
                        ),
                        modifier = Modifier.testTag("save_settings_button")
                    ) {
                        Text("Guardar Cambios", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
