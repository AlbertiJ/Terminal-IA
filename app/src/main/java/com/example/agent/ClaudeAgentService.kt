package com.example.agent

import com.example.model.AgentSettings
import com.example.model.AiProviderType

/**
 * Legacy wrapper preserved for compatibility with TerminalEngine commands.
 * Strictly requires a configured provider and NEVER returns fabricated/static AI responses.
 */
class ClaudeAgentService(
    private val unifiedClient: UnifiedLlmAgentClient = UnifiedLlmAgentClient()
) {
    suspend fun queryClaude(
        prompt: String,
        apiKey: String,
        model: String = "claude-sonnet-4-20250514",
        systemPrompt: String = "",
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<ClaudeResponse> {
        if (apiKey.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "No hay ningún proveedor/API Key configurado. Configura tu proveedor IA en Ajustes (Anthropic, OpenAI o servidor local en 127.0.0.1)."
                )
            )
        }
        val settings = AgentSettings(
            aiProviderType = AiProviderType.ANTHROPIC_CLOUD,
            claudeApiKey = apiKey,
            claudeModel = model,
            claudeSystemPrompt = systemPrompt
        )
        return unifiedClient.queryProvider(prompt, settings, conversationHistory).map { res ->
            ClaudeResponse(
                text = res.explanationText,
                suggestedCommand = res.proposedCommands.firstOrNull(),
                modelUsed = res.modelUsed,
                isLiveApi = true
            )
        }
    }

    data class ClaudeResponse(
        val text: String,
        val suggestedCommand: String?,
        val modelUsed: String,
        val isLiveApi: Boolean
    )
}
