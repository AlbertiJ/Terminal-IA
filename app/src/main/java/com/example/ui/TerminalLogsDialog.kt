package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.controller.TerminalController
import com.example.controller.TerminalLogEntry
import com.example.controller.TerminalLogLevel
import com.example.model.TerminalThemePreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TerminalLogsDialog(
    controller: TerminalController,
    theme: TerminalThemePreset,
    onDismiss: () -> Unit
) {
    val logs by controller.logs.collectAsState()
    var selectedFilter by remember { mutableStateOf("TODOS") }
    var copiedNotice by remember { mutableStateOf(false) }
    val clipboard: ClipboardManager = LocalClipboardManager.current

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "TODOS" -> logs
            "#RedTeam" -> logs.filter { it.level == TerminalLogLevel.AUDIT_RED_TEAM }
            "#BlueTeam" -> logs.filter { it.level == TerminalLogLevel.AUDIT_BLUE_TEAM }
            "COMMAND" -> logs.filter { it.level == TerminalLogLevel.COMMAND }
            "ERROR" -> logs.filter { it.level == TerminalLogLevel.ERROR }
            else -> logs
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, theme.accentText.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .testTag("terminal_logs_dialog"),
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
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = theme.promptUser,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Logs y Telemetría de Seguridad",
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

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filters = listOf("TODOS", "#RedTeam", "#BlueTeam", "COMMAND", "ERROR")
                    for (f in filters) {
                        val isSelected = selectedFilter == f
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isSelected && f == "#RedTeam" -> Color(0xFFFF5252)
                                isSelected && f == "#BlueTeam" -> Color(0xFF00E5FF)
                                isSelected -> theme.primaryText
                                else -> theme.surface
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (f) {
                                    "#RedTeam" -> Color(0xFFFF5252).copy(alpha = 0.6f)
                                    "#BlueTeam" -> Color(0xFF00E5FF).copy(alpha = 0.6f)
                                    else -> theme.accentText.copy(alpha = 0.3f)
                                }
                            ),
                            modifier = Modifier
                                .clickable { selectedFilter = f }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = f,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isSelected -> Color(0xFF0A0F0D)
                                    f == "#RedTeam" -> Color(0xFFFF5252)
                                    f == "#BlueTeam" -> Color(0xFF00E5FF)
                                    else -> theme.primaryText
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = theme.surface)
                Spacer(modifier = Modifier.height(8.dp))

                // Logs list
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay registros con el filtro '$selectedFilter'.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = theme.primaryText.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredLogs.reversed()) { entry ->
                            LogItemCard(entry = entry, theme = theme)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = theme.surface)
                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (copiedNotice) "¡JSON copiado!" else "${filteredLogs.size} registros",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = if (copiedNotice) Color(0xFF00E676) else theme.primaryText.copy(alpha = 0.7f)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val json = controller.exportLogsAsJson()
                                clipboard.setText(AnnotatedString(json))
                                copiedNotice = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accentText),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Exportar JSON", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }

                        Button(
                            onClick = { controller.clearLogs() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF5252).copy(alpha = 0.2f),
                                contentColor = Color(0xFFFF5252)
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpiar", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemCard(entry: TerminalLogEntry, theme: TerminalThemePreset) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val timeFormatted = remember(entry.timestamp) { sdf.format(Date(entry.timestamp)) }

    val levelColor = when (entry.level) {
        TerminalLogLevel.AUDIT_RED_TEAM -> Color(0xFFFF5252)
        TerminalLogLevel.AUDIT_BLUE_TEAM -> Color(0xFF00E5FF)
        TerminalLogLevel.ERROR -> Color(0xFFFF1744)
        TerminalLogLevel.WARNING -> Color(0xFFFFD600)
        TerminalLogLevel.COMMAND -> Color(0xFF00E676)
        TerminalLogLevel.INFO -> theme.promptUser
        else -> theme.primaryText
    }

    Surface(
        color = theme.surface,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, levelColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.level.name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = levelColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[${entry.tag}]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = theme.accentText
                    )
                }
                Text(
                    text = timeFormatted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = theme.primaryText.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.message,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = theme.primaryText
            )
        }
    }
}
