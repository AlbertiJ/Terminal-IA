package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agent.AgentUiSnapshot
import com.example.agent.UnifiedLlmAgentClient
import com.example.model.AgentAuditRecord
import com.example.model.AgentCommandProposal
import com.example.model.AgentSettings
import com.example.model.AiProviderType
import com.example.model.ProposalApprovalStatus
import com.example.model.ProposalExecutionStatus
import com.example.model.TerminalThemePreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentAssistantDialog(
    settings: AgentSettings,
    agentState: AgentUiSnapshot,
    isRealSshConnected: Boolean,
    theme: TerminalThemePreset,
    onSubmitPrompt: (String) -> Unit,
    onApproveProposal: (String) -> Unit,
    onRejectProposal: (String) -> Unit,
    onStopExecution: () -> Unit,
    onUpdateWorkingDir: (String) -> Unit,
    onToggleAutoApprove: (Boolean) -> Unit,
    onAddAllowlistCommand: (String) -> Result<String>,
    onRemoveAllowlistCommand: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var promptText by remember { mutableStateOf("") }
    var workingDirDraft by remember(settings.agentWorkingDir) { mutableStateOf(settings.agentWorkingDir) }
    var showAllowlistEditor by remember { mutableStateOf(false) }
    var allowlistDraft by remember { mutableStateOf("") }
    var allowlistError by remember { mutableStateOf<String?>(null) }

    val providerError = remember(settings) {
        UnifiedLlmAgentClient.validateProviderConfiguration(settings)
    }
    val pendingProposals = remember(agentState.proposals) {
        agentState.proposals.filter { it.approvalStatus == ProposalApprovalStatus.PENDING }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(0.96f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, theme.accentText.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .testTag("agent_assistant_dialog"),
            color = theme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Agente IA",
                            tint = theme.claudeColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Agente IA · Canal SSH Real",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = theme.primaryText
                            )
                            Text(
                                text = "${settings.aiProviderType.shortLabel} · ${if (isRealSshConnected) "SSH conectado" else "SSH desconectado"}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = if (isRealSshConnected) Color(0xFF00E676) else Color(0xFFFFB300)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("agent_open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configurar Proveedor IA",
                                tint = theme.accentText
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_agent_panel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar panel de Agente IA",
                                tint = theme.primaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Prominent Immediate STOP banner whenever busy
                if (agentState.isBusy) {
                    Surface(
                        color = Color(0xFFFF1744).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFFFF5252)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF5252))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (agentState.isExecutingCommand) {
                                        "Ejecutando comando aprobado por SSH..."
                                    } else {
                                        "Consultando proveedor IA..."
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFFFF8A80)
                                )
                            }
                            Button(
                                onClick = onStopExecution,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD50000),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("stop_agent_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Detener ejecución inmediatamente",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PARAR", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("agent_workspace_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Provider unconfigured / error notice (never pretends AI worked)
                    if (providerError != null || settings.aiProviderType == AiProviderType.NONE) {
                        item {
                            Surface(
                                color = Color(0xFFFFB300).copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("agent_unconfigured_banner")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Sin proveedor de IA activo",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFFFD54F)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = providerError
                                            ?: "Configura Anthropic Cloud, OpenAI Cloud o un servidor local OpenAI en Termux (127.0.0.1).",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = theme.primaryText
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = onOpenSettings,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD54F)),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text("Configurar Proveedor IA", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Execution policy card: working dir + read-only auto-approve + editable allowlist
                    item {
                        Surface(
                            color = theme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, theme.accentText.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = theme.promptUser,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Control de Aprobación y Carpeta",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = theme.promptUser
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = workingDirDraft,
                                        onValueChange = { workingDirDraft = it },
                                        label = { Text("Carpeta de trabajo permitida") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = theme.accentText,
                                            unfocusedBorderColor = theme.primaryText.copy(alpha = 0.3f),
                                            focusedTextColor = theme.primaryText,
                                            unfocusedTextColor = theme.primaryText
                                        ),
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("agent_working_dir_input")
                                    )
                                    OutlinedButton(
                                        onClick = { onUpdateWorkingDir(workingDirDraft) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text("Fijar", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    }
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
                                            text = "Aprobar automáticamente solo lectura",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = theme.primaryText
                                        )
                                        Text(
                                            text = "Nunca auto-aprueba borrar, instalar paquetes, chmod, mover, red saliente ni rutas fuera de '${settings.agentWorkingDir}'.",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = theme.primaryText.copy(alpha = 0.7f)
                                        )
                                    }
                                    Switch(
                                        checked = settings.agentAutoApproveReadOnly,
                                        onCheckedChange = onToggleAutoApprove,
                                        modifier = Modifier.testTag("auto_approve_readonly_switch"),
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = theme.promptUser,
                                            checkedTrackColor = theme.background
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedButton(
                                    onClick = { showAllowlistEditor = !showAllowlistEditor },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("toggle_allowlist_editor_button")
                                ) {
                                    Text(
                                        text = if (showAllowlistEditor) {
                                            "Ocultar lista blanca (${settings.agentReadOnlyAllowlist.size})"
                                        } else {
                                            "Editar lista blanca de lectura (${settings.agentReadOnlyAllowlist.size})"
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }

                                if (showAllowlistEditor) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = allowlistDraft,
                                            onValueChange = {
                                                allowlistDraft = it
                                                allowlistError = null
                                            },
                                            label = { Text("Comando de lectura (ej. grep)") },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = theme.accentText,
                                                unfocusedBorderColor = theme.primaryText.copy(alpha = 0.3f),
                                                focusedTextColor = theme.primaryText,
                                                unfocusedTextColor = theme.primaryText
                                            ),
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("allowlist_command_input")
                                        )
                                        Button(
                                            onClick = {
                                                val res = onAddAllowlistCommand(allowlistDraft)
                                                res.onSuccess {
                                                    allowlistDraft = ""
                                                    allowlistError = null
                                                }.onFailure { err ->
                                                    allowlistError = err.localizedMessage
                                                }
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("add_allowlist_button")
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Agregar comando", modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    if (allowlistError != null) {
                                        Text(
                                            text = allowlistError!!,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = theme.errorColor,
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .testTag("allowlist_error_text")
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (cmd in settings.agentReadOnlyAllowlist.sorted()) {
                                            AssistChip(
                                                onClick = { onRemoveAllowlistCommand(cmd) },
                                                label = {
                                                    Text(
                                                        text = "$cmd ×",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp
                                                    )
                                                },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = theme.background,
                                                    labelColor = theme.primaryText
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Provider response explanation & errors
                    if (!agentState.lastProviderError.isNullOrBlank()) {
                        item {
                            Surface(
                                color = theme.errorColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, theme.errorColor),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = agentState.lastProviderError,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = theme.errorColor,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    if (agentState.lastAgentExplanation.isNotBlank()) {
                        item {
                            Surface(
                                color = theme.surface,
                                border = BorderStroke(1.dp, theme.claudeColor.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "RESPUESTA DEL AGENTE (SOLO PROPUESTA)",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = theme.claudeColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = agentState.lastAgentExplanation,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = theme.primaryText
                                    )
                                }
                            }
                        }
                    }

                    // Pending command proposals requiring explicit confirmation
                    if (pendingProposals.isNotEmpty()) {
                        item {
                            Text(
                                text = "COMANDOS PROPUESTOS PENDIENTES (${pendingProposals.size})",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFFFFD54F),
                                modifier = Modifier.testTag("pending_proposals_header")
                            )
                        }
                        items(pendingProposals, key = { it.id }) { proposal ->
                            PendingProposalCard(
                                proposal = proposal,
                                theme = theme,
                                isBusy = agentState.isBusy,
                                onApprove = { onApproveProposal(proposal.id) },
                                onReject = { onRejectProposal(proposal.id) }
                            )
                        }
                    }

                    // Complete Audit Log
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = theme.accentText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REGISTRO DE AUDITORÍA DEL AGENTE (${agentState.auditRecords.size})",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = theme.accentText
                            )
                        }
                    }

                    if (agentState.auditRecords.isEmpty()) {
                        item {
                            Text(
                                text = "Sin eventos aún. Envía una consulta arriba para que el agente proponga comandos.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = theme.primaryText.copy(alpha = 0.55f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(agentState.auditRecords, key = { "audit_" + it.id }) { record ->
                            AgentAuditCard(record = record, theme = theme)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = theme.surface)
                Spacer(modifier = Modifier.height(8.dp))

                // Bottom prompt composer + Send + STOP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = { Text("Ej: revisa qué archivos hay en el proyecto") },
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.claudeColor,
                            unfocusedBorderColor = theme.surface,
                            focusedTextColor = theme.primaryText,
                            unfocusedTextColor = theme.primaryText
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("agent_prompt_input")
                    )

                    if (agentState.isBusy) {
                        Button(
                            onClick = onStopExecution,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD50000),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("stop_agent_composer_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Parar", modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                val submitted = promptText.trim()
                                if (submitted.isNotEmpty()) {
                                    promptText = ""
                                    onSubmitPrompt(submitted)
                                }
                            },
                            enabled = promptText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primaryText,
                                contentColor = theme.background
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("submit_agent_prompt_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Proponer comandos",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingProposalCard(
    proposal: AgentCommandProposal,
    theme: TerminalThemePreset,
    isBusy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        color = theme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFFFB300)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("proposal_card_${proposal.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "$ ${proposal.command}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFFFFD54F)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Carpeta: ${proposal.workingDir} · ${proposal.policyReason}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = theme.primaryText.copy(alpha = 0.78f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.7f)),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("reject_proposal_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rechazar", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Button(
                    onClick = onApprove,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E676),
                        contentColor = Color(0xFF0A0F0D)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("approve_proposal_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Aprobar SSH", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AgentAuditCard(
    record: AgentAuditRecord,
    theme: TerminalThemePreset
) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val timeText = remember(record.timestamp) { sdf.format(Date(record.timestamp)) }
    val statusColor = when (record.decision) {
        ProposalApprovalStatus.APPROVED_MANUAL,
        ProposalApprovalStatus.AUTO_APPROVED_READ_ONLY -> when (record.executionStatus) {
            ProposalExecutionStatus.COMPLETED -> Color(0xFF00E676)
            ProposalExecutionStatus.STOPPED_BY_USER -> Color(0xFFFF5252)
            ProposalExecutionStatus.FAILED -> Color(0xFFFF5252)
            else -> Color(0xFF00E5FF)
        }
        ProposalApprovalStatus.REJECTED,
        ProposalApprovalStatus.CANCELLED -> Color(0xFFFF5252)
        ProposalApprovalStatus.PENDING -> Color(0xFFFFD54F)
    }

    Surface(
        color = theme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${record.decision.label} · ${record.executionStatus.label}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = statusColor
                )
                Text(
                    text = timeText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = theme.primaryText.copy(alpha = 0.55f)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Propuesto: $ ${record.proposedCommand}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = theme.primaryText
            )
            Text(
                text = "Prompt: \"${record.userPrompt}\" (${record.providerLabel})",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = theme.primaryText.copy(alpha = 0.7f)
            )
            if (record.exitCode != null) {
                Text(
                    text = "Exit code: ${record.exitCode}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = statusColor
                )
            }
            if (record.output.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(theme.background)
                        .padding(6.dp)
                ) {
                    Text(
                        text = record.output,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = theme.accentText
                    )
                }
            }
        }
    }
}
