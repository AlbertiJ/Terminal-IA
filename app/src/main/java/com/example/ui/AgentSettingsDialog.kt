package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.agent.AgentCommandPolicy
import com.example.model.AgentSettings
import com.example.model.AiProviderType
import com.example.model.TerminalThemePreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentSettingsDialog(
    currentSettings: AgentSettings,
    onDismiss: () -> Unit,
    onSave: (AgentSettings) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    var aiProviderType by remember { mutableStateOf(currentSettings.aiProviderType) }
    var claudeApiKey by remember { mutableStateOf(currentSettings.claudeApiKey) }
    var claudeModel by remember { mutableStateOf(currentSettings.claudeModel) }
    var claudeSystemPrompt by remember { mutableStateOf(currentSettings.claudeSystemPrompt) }
    var showClaudeKey by remember { mutableStateOf(false) }

    var openAiBaseUrl by remember { mutableStateOf(currentSettings.openAiBaseUrl) }
    var openAiApiKey by remember { mutableStateOf(currentSettings.openAiApiKey) }
    var openAiModel by remember { mutableStateOf(currentSettings.openAiModel) }
    var showOpenAiKey by remember { mutableStateOf(false) }

    var localOpenAiBaseUrl by remember { mutableStateOf(currentSettings.localOpenAiBaseUrl) }
    var localOpenAiToken by remember { mutableStateOf(currentSettings.localOpenAiToken) }
    var localOpenAiModel by remember { mutableStateOf(currentSettings.localOpenAiModel) }

    var agentWorkingDir by remember { mutableStateOf(currentSettings.agentWorkingDir) }
    var agentAutoApproveReadOnly by remember { mutableStateOf(currentSettings.agentAutoApproveReadOnly) }
    var allowlistSet by remember { mutableStateOf(currentSettings.agentReadOnlyAllowlist) }
    var allowlistDraft by remember { mutableStateOf("") }
    var allowlistError by remember { mutableStateOf<String?>(null) }

    var selectedTheme by remember { mutableStateOf(currentSettings.terminalTheme) }
    var fontSizeSp by remember { mutableIntStateOf(currentSettings.fontSizeSp) }
    var enableVibration by remember { mutableStateOf(currentSettings.enableVibration) }

    var autoStartOnBoot by remember { mutableStateOf(currentSettings.autoStartOnBoot) }
    var autoStartRemoteServersOnBoot by remember { mutableStateOf(currentSettings.autoStartRemoteServersOnBoot) }
    var runBootScriptsOnBoot by remember { mutableStateOf(currentSettings.runBootScriptsOnBoot) }

    val context = LocalContext.current
    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
    }

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
                            text = "Ajustes de Terminal-IA",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = selectedTheme.primaryText
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = selectedTheme.primaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs: Proveedor IA, Apariencia, Arranque
                val tabTitles = listOf("Proveedor IA", "Apariencia", "Arranque")
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
                        Text(
                            text = "PROVEEDOR DE AGENTE IA",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = selectedTheme.claudeColor
                        )
                        Text(
                            text = "Las claves de API se cifran con Android Keystore (AES-256-GCM) y nunca aparecen en logs ni exportaciones. Si no configuras un proveedor, el agente no simula respuestas.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = selectedTheme.primaryText.copy(alpha = 0.72f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        for (type in AiProviderType.entries) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clickable { aiProviderType = type }
                                    .padding(vertical = 4.dp)
                                    .testTag("provider_option_${type.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .border(
                                            2.dp,
                                            if (aiProviderType == type) selectedTheme.claudeColor else selectedTheme.primaryText.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .background(if (aiProviderType == type) selectedTheme.claudeColor else Color.Transparent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = type.displayName,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (aiProviderType == type) selectedTheme.primaryText else selectedTheme.primaryText.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when (aiProviderType) {
                            AiProviderType.NONE -> {
                                Surface(
                                    color = selectedTheme.surface,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Modo sin proveedor activo: las consultas al agente indicarán que falta configurar un proveedor en nube o local en 127.0.0.1.",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = selectedTheme.primaryText.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            AiProviderType.ANTHROPIC_CLOUD -> {
                                OutlinedTextField(
                                    value = claudeApiKey,
                                    onValueChange = { claudeApiKey = it },
                                    label = { Text("Anthropic API Key (sk-ant-...)") },
                                    visualTransformation = if (showClaudeKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showClaudeKey = !showClaudeKey }) {
                                            Icon(
                                                imageVector = if (showClaudeKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Mostrar/Ocultar clave",
                                                tint = selectedTheme.primaryText
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = selectedTheme.claudeColor,
                                        unfocusedBorderColor = selectedTheme.surface,
                                        focusedTextColor = selectedTheme.primaryText,
                                        unfocusedTextColor = selectedTheme.primaryText
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("claude_api_key_input")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = claudeModel,
                                    onValueChange = { claudeModel = it },
                                    label = { Text("Modelo Anthropic") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = selectedTheme.claudeColor,
                                        unfocusedBorderColor = selectedTheme.surface,
                                        focusedTextColor = selectedTheme.primaryText,
                                        unfocusedTextColor = selectedTheme.primaryText
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            AiProviderType.OPENAI_CLOUD -> {
                                OutlinedTextField(
                                    value = openAiBaseUrl,
                                    onValueChange = { openAiBaseUrl = it },
                                    label = { Text("Endpoint OpenAI Compatible (HTTPS)") },
                                    placeholder = { Text("https://api.openai.com/v1") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = selectedTheme.claudeColor,
                                        unfocusedBorderColor = selectedTheme.surface,
                                        focusedTextColor = selectedTheme.primaryText,
                                        unfocusedTextColor = selectedTheme.primaryText
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = openAiApiKey,
                                    onValueChange = { openAiApiKey = it },
                                    label = { Text("API Key (sk-...)") },
                                    visualTransformation = if (showOpenAiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showOpenAiKey = !showOpenAiKey }) {
                                            Icon(
                                                imageVector = if (showOpenAiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Mostrar/Ocultar clave",
                                                tint = selectedTheme.primaryText
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = selectedTheme.claudeColor,
                                        unfocusedBorderColor = selectedTheme.surface,
                                        focusedTextColor = selectedTheme.primaryText,
                                        unfocusedTextColor = selectedTheme.primaryText
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("openai_api_key_input")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = openAiModel,
                                    onValueChange = { openAiModel = it },
                                    label = { Text("Modelo (ej. gpt-4o-mini)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = selectedTheme.claudeColor,
                                        unfocusedBorderColor = selectedTheme.surface,
                                        focusedTextColor = selectedTheme.primaryText,
                                        unfocusedTextColor = selectedTheme.primaryText
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            AiProviderType.TERMUX_LOCAL_OPENAI -> {
                                OutlinedTextField(
                                    value = localOpenAiBaseUrl,
                                    onValueChange = { localOpenAiBaseUrl = it },
                                    label = { Text("URL servidor local en Termux (solo 127.0.0.1)") },
                                    placeholder = { Text("http://127.0.0.1:11434/v1") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = selectedTheme.promptUser,
                                        unfocusedBorderColor = selectedTheme.surface,
                                        focusedTextColor = selectedTheme.primaryText,
                                        unfocusedTextColor = selectedTheme.primaryText
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("local_openai_url_input")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = localOpenAiModel,
                                    onValueChange = { localOpenAiModel = it },
                                    label = { Text("Modelo local (Ollama / llama.cpp)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = selectedTheme.promptUser,
                                        unfocusedBorderColor = selectedTheme.surface,
                                        focusedTextColor = selectedTheme.primaryText,
                                        unfocusedTextColor = selectedTheme.primaryText
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = localOpenAiToken,
                                    onValueChange = { localOpenAiToken = it },
                                    label = { Text("Token Bearer opcional (si el servidor local lo exige)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = selectedTheme.promptUser,
                                        unfocusedBorderColor = selectedTheme.surface,
                                        focusedTextColor = selectedTheme.primaryText,
                                        unfocusedTextColor = selectedTheme.primaryText
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = claudeSystemPrompt,
                            onValueChange = { claudeSystemPrompt = it },
                            label = { Text("Instrucción base del sistema") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = selectedTheme.accentText,
                                unfocusedBorderColor = selectedTheme.surface,
                                focusedTextColor = selectedTheme.primaryText,
                                unfocusedTextColor = selectedTheme.primaryText
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = selectedTheme.surface)
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "POLÍTICA DE APROBACIÓN EN SSH REAL",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = selectedTheme.promptUser
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = agentWorkingDir,
                            onValueChange = { agentWorkingDir = it },
                            label = { Text("Carpeta de trabajo permitida para el agente") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = selectedTheme.promptUser,
                                unfocusedBorderColor = selectedTheme.surface,
                                focusedTextColor = selectedTheme.primaryText,
                                unfocusedTextColor = selectedTheme.primaryText
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Aprobar automáticamente solo lectura",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = selectedTheme.primaryText
                                )
                                Text(
                                    text = "Apagado por defecto. Jamás auto-aprueba borrar, instalar paquetes, chmod, mover, red saliente ni rutas externas.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = selectedTheme.primaryText.copy(alpha = 0.65f)
                                )
                            }
                            Switch(
                                checked = agentAutoApproveReadOnly,
                                onCheckedChange = { agentAutoApproveReadOnly = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = selectedTheme.promptUser,
                                    checkedTrackColor = selectedTheme.surface
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lista blanca de comandos de lectura (${allowlistSet.size}):",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = selectedTheme.primaryText
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = allowlistDraft,
                                onValueChange = {
                                    allowlistDraft = it
                                    allowlistError = null
                                },
                                label = { Text("Agregar comando (ej. grep)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = selectedTheme.accentText,
                                    unfocusedBorderColor = selectedTheme.surface,
                                    focusedTextColor = selectedTheme.primaryText,
                                    unfocusedTextColor = selectedTheme.primaryText
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    AgentCommandPolicy.validateAllowlistEntry(allowlistDraft)
                                        .onSuccess { cmd ->
                                            allowlistSet = allowlistSet + cmd
                                            allowlistDraft = ""
                                            allowlistError = null
                                        }
                                        .onFailure { err ->
                                            allowlistError = err.localizedMessage
                                        }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Agregar", modifier = Modifier.size(16.dp))
                            }
                        }

                        if (allowlistError != null) {
                            Text(
                                text = allowlistError!!,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = selectedTheme.errorColor,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (cmd in allowlistSet.sorted()) {
                                AssistChip(
                                    onClick = { allowlistSet = allowlistSet - cmd },
                                    label = {
                                        Text("$cmd ×", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = selectedTheme.surface,
                                        labelColor = selectedTheme.primaryText
                                    )
                                )
                            }
                        }
                    }

                    1 -> {
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
                                        contentDescription = "Seleccionado",
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

                    2 -> {
                        // Auto-boot and universal installation options
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = selectedTheme.accentText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AUTO-ARRANQUE Y ALMACENAMIENTO",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = selectedTheme.accentText
                            )
                        }
                        Text(
                            text = "Configura el inicio automático compatible con Android 15/16 (sin servicios bloqueados) y scripts en ~/.termux/boot/.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = selectedTheme.primaryText.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Auto-arranque al encender",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = selectedTheme.primaryText
                                )
                                Text(
                                    text = "Escucha BOOT_COMPLETED y muestra notificación rápida tras reiniciar.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = selectedTheme.primaryText.copy(alpha = 0.65f)
                                )
                            }
                            Switch(
                                checked = autoStartOnBoot,
                                onCheckedChange = { enabled ->
                                    autoStartOnBoot = enabled
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                modifier = Modifier.testTag("auto_start_boot_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = selectedTheme.accentText,
                                    checkedTrackColor = selectedTheme.surface
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Ejecutar scripts ~/.termux/boot/*.sh",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = if (autoStartOnBoot) selectedTheme.primaryText else selectedTheme.primaryText.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "Valida sandbox (bloquea su/sudo) y guarda log en last_boot.log.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = selectedTheme.primaryText.copy(alpha = 0.65f)
                                )
                            }
                            Switch(
                                checked = runBootScriptsOnBoot,
                                enabled = autoStartOnBoot,
                                onCheckedChange = { runBootScriptsOnBoot = it },
                                modifier = Modifier.testTag("run_boot_scripts_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = selectedTheme.accentText,
                                    checkedTrackColor = selectedTheme.surface
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Auto-iniciar Terminal Web (:8080)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = selectedTheme.primaryText
                                )
                                Text(
                                    text = "Respeta 127.0.0.1 por defecto salvo que actives acceso LAN explícitamente.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = selectedTheme.primaryText.copy(alpha = 0.65f)
                                )
                            }
                            Switch(
                                checked = autoStartRemoteServersOnBoot,
                                onCheckedChange = { autoStartRemoteServersOnBoot = it },
                                modifier = Modifier.testTag("auto_start_remote_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = selectedTheme.accentText,
                                    checkedTrackColor = selectedTheme.surface
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(selectedTheme.surface)
                                .border(1.dp, selectedTheme.accentText.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SdStorage,
                                    contentDescription = null,
                                    tint = selectedTheme.primaryText,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Instalación Universal (installLocation=\"auto\")",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = selectedTheme.primaryText
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• La APK puede instalarse o moverse entre memoria interna y tarjeta SD externa.\n• Si mueves la app a SD externa y la unidad no está montada al encender, Android omitirá BOOT_COMPLETED hasta abrirla.\n• Para estabilidad en segundo plano, configura Batería en 'Sin restricciones'.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = selectedTheme.primaryText.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = selectedTheme.surface)
                Spacer(modifier = Modifier.height(12.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = selectedTheme.primaryText),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Cancelar", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = currentSettings.copy(
                                aiProviderType = aiProviderType,
                                claudeApiKey = claudeApiKey.trim(),
                                claudeModel = claudeModel.trim().ifBlank { "claude-sonnet-4-20250514" },
                                claudeSystemPrompt = claudeSystemPrompt.trim(),
                                openAiBaseUrl = openAiBaseUrl.trim().ifBlank { "https://api.openai.com/v1" },
                                openAiApiKey = openAiApiKey.trim(),
                                openAiModel = openAiModel.trim().ifBlank { "gpt-4o-mini" },
                                localOpenAiBaseUrl = localOpenAiBaseUrl.trim().ifBlank { "http://127.0.0.1:11434/v1" },
                                localOpenAiToken = localOpenAiToken.trim(),
                                localOpenAiModel = localOpenAiModel.trim().ifBlank { "qwen2.5-coder:7b" },
                                agentWorkingDir = agentWorkingDir.trim().ifBlank { "~" },
                                agentAutoApproveReadOnly = agentAutoApproveReadOnly,
                                agentReadOnlyAllowlist = AgentCommandPolicy.sanitizeAllowlist(allowlistSet),
                                terminalTheme = selectedTheme,
                                fontSizeSp = fontSizeSp,
                                enableVibration = enableVibration,
                                autoStartOnBoot = autoStartOnBoot,
                                autoStartRemoteServersOnBoot = autoStartRemoteServersOnBoot,
                                runBootScriptsOnBoot = runBootScriptsOnBoot
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = selectedTheme.primaryText,
                            contentColor = selectedTheme.background
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_settings_button")
                    ) {
                        Text("Guardar Cambios", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
