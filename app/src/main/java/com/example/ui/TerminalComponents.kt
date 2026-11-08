package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LineType
import com.example.model.SessionType
import com.example.model.TerminalLine
import com.example.model.TerminalSession
import com.example.model.TerminalThemePreset

@Composable
fun TerminalLineView(
    line: TerminalLine,
    theme: TerminalThemePreset,
    fontSizeSp: Int,
    onRunSuggestedCommand: (String) -> Unit
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    when (line.type) {
        LineType.COMMAND -> {
            Text(
                text = line.text,
                color = theme.promptUser,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        }
        LineType.ERROR -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = theme.errorColor,
                    modifier = Modifier.size((fontSizeSp + 2).dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = line.text,
                    color = theme.errorColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        LineType.SYSTEM -> {
            Text(
                text = line.text,
                color = theme.accentText.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = fontSizeSp.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }
        LineType.HEADER -> {
            val annotated = parseAnsiColors(line.text, theme.primaryText)
            Text(
                text = annotated,
                fontFamily = FontFamily.Monospace,
                fontSize = (fontSizeSp - 1).sp,
                lineHeight = (fontSizeSp + 3).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
        LineType.AGENT_CLAUDE -> {
            ClaudeResponseCard(
                line = line,
                theme = theme,
                fontSizeSp = fontSizeSp,
                onRunSuggested = onRunSuggestedCommand
            )
        }
        LineType.AGENT_OPENCLAW -> {
            OpenClawResponseCard(
                line = line,
                theme = theme,
                fontSizeSp = fontSizeSp,
                onRunSuggested = onRunSuggestedCommand
            )
        }
        LineType.AGENT_STEP -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(theme.openClawColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = line.text,
                    color = theme.openClawColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (fontSizeSp - 0.5f).sp
                )
            }
        }
        LineType.OUTPUT -> {
            val annotated = parseAnsiColors(line.text, theme.primaryText)
            Text(
                text = annotated,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp + 4).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }
    }
}

@Composable
fun ClaudeResponseCard(
    line: TerminalLine,
    theme: TerminalThemePreset,
    fontSizeSp: Int,
    onRunSuggested: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Surface(
        color = theme.surface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.claudeColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("claude_response_card")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Claude Agent",
                        tint = theme.claudeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CLAUDE AGENT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.claudeColor
                    )
                }

                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(line.text))
                        copied = true
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy text",
                        tint = if (copied) Color(0xFF00E676) else theme.primaryText.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = line.text,
                color = theme.primaryText,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp + 4).sp
            )

            if (line.commandText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onRunSuggested(line.commandText) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = theme.claudeColor
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_claude_suggestion_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ejecutar en Shell: ${line.commandText}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OpenClawResponseCard(
    line: TerminalLine,
    theme: TerminalThemePreset,
    fontSizeSp: Int,
    onRunSuggested: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Surface(
        color = theme.surface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.openClawColor.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("openclaw_response_card")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "OpenClaw Agent",
                        tint = theme.openClawColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "OPENCLAW AUTONOMOUS AGENT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.openClawColor
                    )
                }

                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(line.text))
                        copied = true
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy text",
                        tint = if (copied) Color(0xFF00E676) else theme.primaryText.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = line.text,
                color = theme.primaryText,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp + 4).sp
            )
        }
    }
}

@Composable
fun SessionTabsBar(
    sessions: List<TerminalSession>,
    activeSessionId: String,
    theme: TerminalThemePreset,
    onSelectSession: (String) -> Unit,
    onCreateSession: (SessionType) -> Unit,
    onCloseSession: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (session in sessions) {
            val isActive = session.id == activeSessionId
            val chipBg = if (isActive) theme.background else theme.surface
            val chipBorder = if (isActive) theme.accentText else Color.Transparent

            Row(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(chipBg)
                    .border(1.dp, chipBorder, RoundedCornerShape(6.dp))
                    .clickable { onSelectSession(session.id) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (session.type) {
                        SessionType.SHELL -> Icons.Default.Terminal
                        SessionType.CLAUDE -> Icons.Default.SmartToy
                        SessionType.OPENCLAW -> Icons.Default.AutoAwesome
                    },
                    contentDescription = null,
                    tint = if (isActive) theme.primaryText else theme.primaryText.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = session.title,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) theme.primaryText else theme.primaryText.copy(alpha = 0.6f)
                )
                if (sessions.size > 1 && isActive) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close tab",
                        tint = theme.primaryText.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(12.dp)
                            .clickable { onCloseSession(session.id) }
                    )
                }
            }
        }

        // Add session button
        IconButton(
            onClick = { onCreateSession(SessionType.SHELL) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Session",
                tint = theme.accentText,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ExtraKeysBar(
    theme: TerminalThemePreset,
    onKeyPressed: (String) -> Unit
) {
    val scrollState1 = rememberScrollState()

    val primaryKeys = listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "|", "/", "-", "~", "\"", "$")
    val shortcutKeys = listOf("scripts", "history", "ssh", "ip", "clear", "neofetch", "claude", "openclaw")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface)
            .padding(vertical = 2.dp)
    ) {
        // Main extra keys row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState1)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (key in primaryKeys) {
                Surface(
                    color = theme.background,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.accentText.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .clickable { onKeyPressed(key) }
                        .testTag("key_$key")
                ) {
                    Text(
                        text = key,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.primaryText,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
            }

            // Shortcuts inside same bar for easy access
            for (s in shortcutKeys) {
                Surface(
                    color = when (s) {
                        "scripts" -> Color(0xFF00E676).copy(alpha = 0.15f)
                        "claude" -> theme.claudeColor.copy(alpha = 0.2f)
                        "openclaw" -> theme.openClawColor.copy(alpha = 0.2f)
                        else -> theme.background
                    },
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (s) {
                            "scripts" -> Color(0xFF00E676).copy(alpha = 0.5f)
                            "claude" -> theme.claudeColor.copy(alpha = 0.6f)
                            "openclaw" -> theme.openClawColor.copy(alpha = 0.6f)
                            else -> theme.accentText.copy(alpha = 0.3f)
                        }
                    ),
                    modifier = Modifier
                        .clickable { onKeyPressed(s) }
                        .testTag("shortcut_$s")
                ) {
                    Text(
                        text = s,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (s) {
                            "scripts" -> Color(0xFF00E676)
                            "history" -> theme.promptUser
                            "claude" -> theme.claudeColor
                            "openclaw" -> theme.openClawColor
                            else -> theme.accentText
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Parses simple ANSI escape color sequences into Jetpack Compose AnnotatedString
 */
fun parseAnsiColors(rawText: String, defaultColor: Color): AnnotatedString {
    val clean = rawText.replace("\r", "")
    val builder = AnnotatedString.Builder()
    val pattern = Regex("(?:\u001B|\\[)?\\[([0-9;]+)m")

    var lastIndex = 0
    var currentColor = defaultColor
    var isBold = false

    val matches = pattern.findAll(clean)
    for (m in matches) {
        val matchStart = m.range.first
        if (matchStart > lastIndex) {
            val segment = clean.substring(lastIndex, matchStart)
            builder.pushStyle(
                SpanStyle(
                    color = currentColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                )
            )
            builder.append(segment)
            builder.pop()
        }

        val code = m.groupValues[1]
        val parts = code.split(";")
        for (p in parts) {
            when (p) {
                "0" -> {
                    currentColor = defaultColor
                    isBold = false
                }
                "1" -> isBold = true
                "30" -> currentColor = Color(0xFF4A5568)
                "31" -> currentColor = Color(0xFFFF4444)
                "32" -> currentColor = Color(0xFF00FF66)
                "33" -> currentColor = Color(0xFFFFD700)
                "34" -> currentColor = Color(0xFF38BDF8)
                "35" -> currentColor = Color(0xFFFF79C6)
                "36" -> currentColor = Color(0xFF00F5FF)
                "37" -> currentColor = Color(0xFFF8F8F2)
            }
        }
        lastIndex = m.range.last + 1
    }

    if (lastIndex < clean.length) {
        builder.pushStyle(
            SpanStyle(
                color = currentColor,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
            )
        )
        builder.append(clean.substring(lastIndex))
        builder.pop()
    }

    return builder.toAnnotatedString()
}
