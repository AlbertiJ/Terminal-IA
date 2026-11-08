package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.TerminalThemePreset
import com.example.script.ScriptManager
import com.example.script.ScriptMetadata

@Composable
fun ScriptManagerDialog(
    scriptManager: ScriptManager,
    theme: TerminalThemePreset,
    onDismiss: () -> Unit,
    onEditScript: (String) -> Unit,
    onRunScript: (String) -> Unit
) {
    var scripts by remember { mutableStateOf(scriptManager.listScripts()) }
    var showNewModal by remember { mutableStateOf(false) }
    var newScriptName by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val templates = listOf(
        "Diagnóstico de Sistema" to "#!/bin/sh\necho 'Ejecutando diagnóstico seguro...'\ndate\nwhoami\nuname -a\necho 'Comprobando scripts en VFS...'\nls -la /home/user/scripts\necho 'Diagnóstico completado.'\n",
        "Pipeline de Agente Claude" to "#!/bin/sh\necho 'Iniciando pipeline de agente...'\nneofetch | claude 'resume las especificaciones del sistema'\necho 'Pipeline completado.'\n",
        "Backup de Proyectos" to "#!/bin/sh\necho 'Creando respaldo local...'\nmkdir /home/user/backup\ncp /home/user/welcome.txt /home/user/backup/\necho 'Respaldo generado con éxito en ~/backup'\n"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, theme.accentText.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .testTag("script_manager_dialog"),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = theme.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gestor de Scripts (.sh)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
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

                Spacer(modifier = Modifier.height(6.dp))

                // Security Policy Banner
                Surface(
                    color = theme.surface,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.promptPath.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ENTORNO DE EJECUCIÓN RESTRINGIDO (EACCES/EPERM)",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color(0xFF00E676)
                            )
                            Text(
                                text = "Usuario no privilegiado (UID: 10234). Sin acceso a root (su/sudo). Aislamiento total en /home/user.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = theme.primaryText.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Top Toolbar: Create New Script button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scripts en /home/user/scripts (${scripts.size}):",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.accentText
                    )

                    Button(
                        onClick = { showNewModal = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primaryText,
                            contentColor = theme.background
                        ),
                        modifier = Modifier.testTag("new_script_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nuevo Script .sh", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = theme.surface)
                Spacer(modifier = Modifier.height(8.dp))

                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = theme.promptUser,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Scripts List
                if (scripts.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay scripts .sh guardados.\nToca 'Nuevo Script .sh' para crear uno.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = theme.primaryText.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scripts) { script ->
                            ScriptCardItem(
                                script = script,
                                theme = theme,
                                onToggleExecutable = { newState ->
                                    scriptManager.setExecutable(script.virtualPath, newState)
                                    scripts = scriptManager.listScripts()
                                    statusMessage = "Permiso de ejecución actualizado para ${script.name}: ${if (newState) "+x" else "-x"}"
                                },
                                onRun = {
                                    onDismiss()
                                    onRunScript(script.virtualPath)
                                },
                                onEdit = {
                                    onDismiss()
                                    onEditScript(script.virtualPath)
                                },
                                onDelete = {
                                    scriptManager.deleteScript(script.virtualPath)
                                    scripts = scriptManager.listScripts()
                                    statusMessage = "Script eliminado: ${script.name}"
                                }
                            )
                        }
                    }
                }

                // New Script Creation Modal
                if (showNewModal) {
                    Dialog(onDismissRequest = { showNewModal = false }) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = theme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.accentText),
                            modifier = Modifier.fillMaxWidth(0.92f).padding(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Crear Script Personalizado .sh",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = theme.primaryText
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = newScriptName,
                                    onValueChange = { newScriptName = it },
                                    label = { Text("Nombre (ej: mi_tarea.sh)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = theme.primaryText,
                                        unfocusedBorderColor = theme.background,
                                        focusedTextColor = theme.primaryText,
                                        unfocusedTextColor = theme.primaryText
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Seleccionar Plantilla:",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = theme.primaryText
                                )

                                templates.forEachIndexed { idx, (title, _) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.RadioButton(
                                            selected = selectedTemplate == idx,
                                            onClick = { selectedTemplate = idx }
                                        )
                                        Text(
                                            text = title,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = theme.primaryText
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(onClick = { showNewModal = false }) {
                                        Text("Cancelar", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (newScriptName.isNotBlank()) {
                                                val clean = if (newScriptName.endsWith(".sh")) newScriptName else "$newScriptName.sh"
                                                val templateContent = templates[selectedTemplate].second
                                                scriptManager.saveScript(clean, templateContent, makeExecutable = true)
                                                scripts = scriptManager.listScripts()
                                                showNewModal = false
                                                newScriptName = ""
                                                onDismiss()
                                                onEditScript("/home/user/scripts/$clean")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryText, contentColor = theme.background)
                                    ) {
                                        Text("Guardar y Editar", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScriptCardItem(
    script: ScriptMetadata,
    theme: TerminalThemePreset,
    onToggleExecutable: (Boolean) -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = theme.surface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (script.isExecutable) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFFFFB300).copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Title and Permission Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = if (script.isExecutable) Color(0xFF00E676) else Color(0xFFFFB300),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = script.name,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = theme.primaryText
                    )
                }

                // Permission chip
                Surface(
                    color = if (script.isExecutable) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFFB300).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (script.isExecutable) Color(0xFF00E676) else Color(0xFFFFB300)
                    )
                ) {
                    Text(
                        text = script.permissionString,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = if (script.isExecutable) Color(0xFF00E676) else Color(0xFFFFB300),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Metadata: Path, Size, User
            Text(
                text = "${script.virtualPath} • ${script.sizeBytes} B • ${script.lineCount} líneas",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = theme.primaryText.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = theme.background)
            Spacer(modifier = Modifier.height(6.dp))

            // Controls: Toggle Execute Permission & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = script.isExecutable,
                        onCheckedChange = { onToggleExecutable(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E676),
                            checkedTrackColor = theme.background
                        ),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (script.isExecutable) "Permiso +x" else "Solo lectura (-x)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = if (script.isExecutable) Color(0xFF00E676) else Color(0xFFFFB300)
                    )
                }

                Row {
                    OutlinedButton(
                        onClick = onEdit,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accentText),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editar", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = onRun,
                        enabled = script.isExecutable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color(0xFF0A0F0D)
                        ),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ejecutar", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
