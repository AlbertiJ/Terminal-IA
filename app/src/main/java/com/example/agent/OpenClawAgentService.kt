package com.example.agent

/**
 * Legacy OpenClaw entrypoint cleaned of fabricated local execution telemetry.
 * All AI interactions now go exclusively through UnifiedLlmAgentClient + explicit SSH command approval.
 */
class OpenClawAgentService {

    sealed class AgentStep {
        data class Plan(val objective: String, val steps: List<String>) : AgentStep()
        data class ToolCall(val tool: String, val command: String, val stepIndex: Int) : AgentStep()
        data class ToolResult(val tool: String, val output: String, val isSuccess: Boolean) : AgentStep()
        data class Summary(val text: String, val artifactsCreated: List<String> = emptyList()) : AgentStep()
    }

    suspend fun executeTask(
        goal: String,
        endpoint: String = "",
        token: String = "",
        onStep: (AgentStep) -> Unit
    ): Result<String> {
        val msg = "OpenClaw simulado fue eliminado. Configura un proveedor IA real (Nube con API Key o Local OpenAI en 127.0.0.1) y usa el panel 'Agente IA' sobre SSH real con aprobación manual."
        onStep(AgentStep.ToolResult("Policy", msg, false))
        return Result.failure(IllegalStateException(msg))
    }
}
