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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.fs.VirtualFileSystem
import com.example.model.TerminalThemePreset

@Composable
fun FileManagerDialog(
    vfs: VirtualFileSystem,
    initialPath: String = "/home/user",
    theme: TerminalThemePreset,
    onDismiss: () -> Unit,
    onEditFile: (String) -> Unit,
    onRunFile: (String) -> Unit
) {
    var currentPath by remember { mutableStateOf(initialPath) }
    var fileEntries by remember(currentPath) { mutableStateOf(vfs.listDirectory(currentPath).getOrDefault(emptyList())) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, theme.accentText.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .testTag("file_manager_dialog"),
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
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = theme.promptPath,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Explorador VFS: $currentPath",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
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

                // Navigation Toolbar: Back / Up / New File
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        if (currentPath != "/" && currentPath != "/home/user") {
                            OutlinedButton(
                                onClick = {
                                    val parent = vfs.normalizePath(currentPath, "..")
                                    currentPath = parent
                                    fileEntries = vfs.listDirectory(parent).getOrDefault(emptyList())
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primaryText),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Subir", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Subir ..", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                currentPath = "/home/user"
                                fileEntries = vfs.listDirectory("/home/user").getOrDefault(emptyList())
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accentText)
                        ) {
                            Text("~ (Home)", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = { showNewFileDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryText, contentColor = theme.background)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nuevo Script", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = theme.surface)
                Spacer(modifier = Modifier.height(6.dp))

                // Files List
                if (fileEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "(Carpeta vacía)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
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
                        items(fileEntries) { entry ->
                            val fullPath = if (currentPath.endsWith("/")) "$currentPath${entry.name}" else "$currentPath/${entry.name}"

                            Surface(
                                color = theme.surface,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (entry.isDirectory) theme.promptPath.copy(alpha = 0.3f) else theme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (entry.isDirectory) {
                                            currentPath = fullPath
                                            fileEntries = vfs.listDirectory(fullPath).getOrDefault(emptyList())
                                        } else {
                                            onDismiss()
                                            onEditFile(fullPath)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                entry.isDirectory -> Icons.Default.Folder
                                                entry.name.endsWith(".py") || entry.name.endsWith(".sh") -> Icons.Default.Code
                                                else -> Icons.Default.Description
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                entry.isDirectory -> theme.promptPath
                                                entry.name.endsWith(".sh") -> Color(0xFF00FF66)
                                                entry.name.endsWith(".py") -> theme.claudeColor
                                                else -> theme.primaryText.copy(alpha = 0.8f)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = entry.name,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = if (entry.isDirectory) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.sp,
                                                color = theme.primaryText
                                            )
                                            if (!entry.isDirectory) {
                                                Text(
                                                    text = "${entry.size} bytes",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    color = theme.primaryText.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    }

                                    // Action buttons for files
                                    if (!entry.isDirectory) {
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    onDismiss()
                                                    onRunFile(fullPath)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Ejecutar",
                                                    tint = Color(0xFF00E676),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    onDismiss()
                                                    onEditFile(fullPath)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Editar",
                                                    tint = theme.accentText,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    vfs.deleteFile(fullPath)
                                                    fileEntries = vfs.listDirectory(currentPath).getOrDefault(emptyList())
                                                },
                                                modifier = Modifier.size(28.dp)
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
                    }
                }

                // New file dialog
                if (showNewFileDialog) {
                    Dialog(onDismissRequest = { showNewFileDialog = false }) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = theme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.accentText),
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Crear Nuevo Archivo / Script",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = theme.primaryText
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = newFileName,
                                    onValueChange = { newFileName = it },
                                    label = { Text("Nombre (ej: script.sh o tarea.py)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = theme.primaryText,
                                        unfocusedBorderColor = theme.background,
                                        focusedTextColor = theme.primaryText,
                                        unfocusedTextColor = theme.primaryText
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(onClick = { showNewFileDialog = false }) {
                                        Text("Cancelar", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (newFileName.isNotBlank()) {
                                                val cleanName = newFileName.trim()
                                                val target = "$currentPath/$cleanName"
                                                val template = if (cleanName.endsWith(".py")) {
                                                    "#!/usr/bin/env python3\nprint('Script ejecutado con éxito')\n"
                                                } else {
                                                    "#!/bin/sh\necho 'Script bash ejecutado con éxito'\n"
                                                }
                                                vfs.writeFile(target, template)
                                                showNewFileDialog = false
                                                newFileName = ""
                                                onDismiss()
                                                onEditFile(target)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryText, contentColor = theme.background)
                                    ) {
                                        Text("Crear y Editar", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
