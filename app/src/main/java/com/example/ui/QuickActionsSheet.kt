package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.TerminalThemePreset

data class QuickActionItem(
    val title: String,
    val description: String,
    val command: String,
    val icon: ImageVector,
    val isAgent: Boolean = false
)

@Composable
fun QuickActionsDialog(
    theme: TerminalThemePreset,
    onDismiss: () -> Unit,
    onSelectAction: (String) -> Unit
) {
    val actions = listOf(
        QuickActionItem(
            title = "Ver IP y Conexión Remota (SSH)",
            description = "Muestra la IP local de tu celular y guía de conexión",
            command = "ip",
            icon = Icons.Default.Lan
        ),
        QuickActionItem(
            title = "Iniciar Servidor SSH / Telnet",
            description = "Arranca el socket de escucha en el puerto 8022",
            command = "sshd start",
            icon = Icons.Default.VpnKey
        ),
        QuickActionItem(
            title = "Neofetch Ficha de Sistema",
            description = "Muestra especificaciones, hardware, memoria y uptime",
            command = "neofetch",
            icon = Icons.Default.Terminal
        ),
        QuickActionItem(
            title = "Piping: Analizar Script con Claude",
            description = "Canaliza código syscheck.sh directamente a Claude vía pipe (|)",
            command = "cat ~/scripts/syscheck.sh | claude \"analiza este script de diagnóstico\"",
            icon = Icons.Default.SmartToy,
            isAgent = true
        ),
        QuickActionItem(
            title = "Agent API: Claude en JSON",
            description = "Ejecuta llamada de API programática de Claude para scripts",
            command = "agent api claude \"explica la estructura del sistema Android\"",
            icon = Icons.Default.Code,
            isAgent = true
        ),
        QuickActionItem(
            title = "OpenClaw: Syscheck Autónomo",
            description = "Ejecuta flujo autónomo con herramientas de kernel y red",
            command = "openclaw run syscheck",
            icon = Icons.Default.AutoAwesome,
            isAgent = true
        ),
        QuickActionItem(
            title = "OpenClaw: Auditoría de Scripts",
            description = "Escanea la carpeta de scripts en VFS y valida permisos",
            command = "openclaw run audit",
            icon = Icons.Default.Security,
            isAgent = true
        ),
        QuickActionItem(
            title = "Python: Ejecutar Demo Agent",
            description = "Corre el script python de tareas agent_task.py",
            command = "python ~/scripts/agent_task.py",
            icon = Icons.Default.Code
        ),
        QuickActionItem(
            title = "Test de Conectividad (Ping)",
            description = "Verifica latencia y respuesta DNS hacia 8.8.8.8",
            command = "ping 8.8.8.8",
            icon = Icons.Default.Wifi
        ),
        QuickActionItem(
            title = "Manual de Comandos (Help)",
            description = "Guía completa de órdenes y sintaxis de agentes",
            command = "help",
            icon = Icons.Default.Help
        )
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, theme.accentText.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .testTag("quick_actions_dialog"),
            color = theme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = theme.primaryText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Acciones Rápidas & Agentes",
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

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(actions) { item ->
                        Surface(
                            color = theme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (item.isAgent) theme.claudeColor.copy(alpha = 0.4f) else theme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectAction(item.command)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (item.isAgent) theme.claudeColor else theme.primaryText,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = theme.primaryText
                                    )
                                    Text(
                                        text = item.description,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = theme.primaryText.copy(alpha = 0.65f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$ ${item.command}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = theme.promptPath
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
