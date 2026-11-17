package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.agent.AgentCommandPolicy
import java.util.UUID

enum class LineType {
    COMMAND,       // e.g. user@termux:~$ ls -la
    OUTPUT,        // Normal command stdout
    ERROR,         // stderr or failed command
    SYSTEM,        // System notifications or boot message
    HEADER,        // Welcome / Neofetch ASCII art
    AGENT_CLAUDE,  // Output from AI agent
    AGENT_OPENCLAW,// Legacy/agent step badge
    AGENT_STEP     // Step in agent workflow
}

enum class SessionType {
    SHELL,
    CLAUDE,
    OPENCLAW
}

enum class AiProviderType(val displayName: String, val shortLabel: String) {
    NONE("Sin proveedor (Desactivado)", "Desactivado"),
    ANTHROPIC_CLOUD("Anthropic Cloud API (Claude)", "Anthropic Cloud"),
    OPENAI_CLOUD("OpenAI / Compatible Cloud (HTTPS)", "OpenAI Cloud"),
    TERMUX_LOCAL_OPENAI("Servidor Local OpenAI en Termux (127.0.0.1)", "Local 127.0.0.1")
}

enum class ProposalApprovalStatus(val label: String) {
    PENDING("Pendiente de aprobación"),
    APPROVED_MANUAL("Aprobado manualmente"),
    AUTO_APPROVED_READ_ONLY("Auto-aprobado (solo lectura)"),
    REJECTED("Rechazado"),
    CANCELLED("Cancelado")
}

enum class ProposalExecutionStatus(val label: String) {
    NOT_EXECUTED("Sin ejecutar"),
    RUNNING("Ejecutando en SSH..."),
    COMPLETED("Ejecutado"),
    FAILED("Error al ejecutar"),
    STOPPED_BY_USER("Detenido por el usuario")
}

data class AgentCommandProposal(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val providerLabel: String,
    val userPrompt: String,
    val command: String,
    val workingDir: String,
    val canAutoApprove: Boolean,
    val policyReason: String,
    val isReadOnlySafeInWorkspace: Boolean,
    val approvalStatus: ProposalApprovalStatus = ProposalApprovalStatus.PENDING,
    val executionStatus: ProposalExecutionStatus = ProposalExecutionStatus.NOT_EXECUTED,
    val exitCode: Int? = null,
    val output: String = ""
)

data class AgentAuditRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val providerLabel: String,
    val userPrompt: String,
    val proposedCommand: String,
    val workingDir: String,
    val decision: ProposalApprovalStatus,
    val executionStatus: ProposalExecutionStatus,
    val exitCode: Int? = null,
    val output: String = "",
    val policyReason: String = ""
)

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
    val aiProviderType: AiProviderType = AiProviderType.NONE,
    val claudeApiKey: String = "",
    val claudeModel: String = "claude-sonnet-4-20250514",
    val claudeSystemPrompt: String = "Eres un asistente técnico de Linux y Termux. Solo propones comandos en bloques ```sh``` para que el usuario los apruebe; nunca afirmes haber ejecutado comandos.",
    val openAiBaseUrl: String = "https://api.openai.com/v1",
    val openAiApiKey: String = "",
    val openAiModel: String = "gpt-4o-mini",
    val localOpenAiBaseUrl: String = "http://127.0.0.1:11434/v1",
    val localOpenAiToken: String = "",
    val localOpenAiModel: String = "qwen2.5-coder:7b",
    val agentWorkingDir: String = "~",
    val agentAutoApproveReadOnly: Boolean = false,
    val agentReadOnlyAllowlist: Set<String> = AgentCommandPolicy.DEFAULT_READ_ONLY_ALLOWLIST,
    val terminalTheme: TerminalThemePreset = TerminalThemePreset.HACKER_GREEN,
    val fontSizeSp: Int = 13,
    val enableVibration: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    val autoStartRemoteServersOnBoot: Boolean = false,
    val runBootScriptsOnBoot: Boolean = true
)
