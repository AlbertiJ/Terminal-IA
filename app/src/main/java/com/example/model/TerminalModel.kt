package com.example.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class LineType {
    COMMAND,       // e.g. user@termux:~$ ls -la
    OUTPUT,        // Normal command stdout
    ERROR,         // stderr or failed command
    SYSTEM,        // System notifications or boot message
    HEADER,        // Welcome / Neofetch ASCII art
    AGENT_CLAUDE,  // Output from Claude agent
    AGENT_OPENCLAW,// Output from OpenClaw autonomous agent
    AGENT_STEP     // Step in agent workflow (Plan, Tool execution, Result)
}

enum class SessionType {
    SHELL,
    CLAUDE,
    OPENCLAW
}

data class TerminalLine(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val type: LineType = LineType.OUTPUT,
    val timestamp: Long = System.currentTimeMillis(),
    val agentName: String? = null,
    val commandText: String? = null,
    val toolCallName: String? = null,
    val isStreaming: Boolean = false
)

data class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: SessionType = SessionType.SHELL,
    val currentDirectory: String = "/home/user",
    val history: List<String> = emptyList()
)

enum class TerminalThemePreset(
    val displayName: String,
    val background: Color,
    val surface: Color,
    val primaryText: Color,
    val accentText: Color,
    val promptUser: Color,
    val promptPath: Color,
    val errorColor: Color,
    val claudeColor: Color,
    val openClawColor: Color
) {
    HACKER_GREEN(
        displayName = "Hacker Green (Termux Classic)",
        background = Color(0xFF0A0F0D),
        surface = Color(0xFF111A16),
        primaryText = Color(0xFF00FF66),
        accentText = Color(0xFF33FFAA),
        promptUser = Color(0xFF00E676),
        promptPath = Color(0xFF80FFD4),
        errorColor = Color(0xFFFF4444),
        claudeColor = Color(0xFFD4A373),
        openClawColor = Color(0xFF00E5FF)
    ),
    CYBER_CYAN(
        displayName = "Cyber Cyan (Matrix / AI)",
        background = Color(0xFF070B12),
        surface = Color(0xFF0E1624),
        primaryText = Color(0xFF00F5FF),
        accentText = Color(0xFF7000FF),
        promptUser = Color(0xFF00E5FF),
        promptPath = Color(0xFF38BDF8),
        errorColor = Color(0xFFFF3366),
        claudeColor = Color(0xFFF59E0B),
        openClawColor = Color(0xFF06B6D4)
    ),
    DRACULA(
        displayName = "Dracula Night",
        background = Color(0xFF1E1F29),
        surface = Color(0xFF282A36),
        primaryText = Color(0xFFF8F8F2),
        accentText = Color(0xFFBD93F9),
        promptUser = Color(0xFF50FA7B),
        promptPath = Color(0xFF8BE9FD),
        errorColor = Color(0xFFFF5555),
        claudeColor = Color(0xFFFFB86C),
        openClawColor = Color(0xFFFF79C6)
    ),
    AMBER_CRT(
        displayName = "Amber Phosphor CRT",
        background = Color(0xFF100B04),
        surface = Color(0xFF1C1307),
        primaryText = Color(0xFFFFB000),
        accentText = Color(0xFFFFD000),
        promptUser = Color(0xFFFF9100),
        promptPath = Color(0xFFFFC107),
        errorColor = Color(0xFFFF3D00),
        claudeColor = Color(0xFFFFAB40),
        openClawColor = Color(0xFFFFD740)
    ),
    MONOKAI(
        displayName = "Monokai Pro",
        background = Color(0xFF19181A),
        surface = Color(0xFF221F22),
        primaryText = Color(0xFFFCFCFA),
        accentText = Color(0xFFFFD866),
        promptUser = Color(0xFFA9DC76),
        promptPath = Color(0xFF78DCE8),
        errorColor = Color(0xFFFF6188),
        claudeColor = Color(0xFFFFD866),
        openClawColor = Color(0xFFAB9DF2)
    )
}

data class AgentSettings(
    val claudeApiKey: String = "",
    val claudeModel: String = "claude-3-5-sonnet-20241022",
    val claudeSystemPrompt: String = "You are Claude, an expert AI programming and Linux system assistant operating directly within a Termux Android terminal console. Provide direct, concise, and executable technical solutions.",
    val openClawEndpoint: String = "https://api.openclaw.io/v1",
    val openClawToken: String = "",
    val openClawAutonomous: Boolean = true,
    val terminalTheme: TerminalThemePreset = TerminalThemePreset.HACKER_GREEN,
    val fontSizeSp: Int = 13,
    val enableVibration: Boolean = true
)
