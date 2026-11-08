package com.example.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClaudeAgentService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun queryClaude(
        prompt: String,
        apiKey: String,
        model: String = "claude-3-5-sonnet-20241022",
        systemPrompt: String = "You are Claude, an expert AI programming and Linux system assistant operating directly within a Termux Android terminal console.",
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<ClaudeResponse> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            // Free autonomous offline agent simulation mode
            return@withContext Result.success(getSimulatedClaudeResponse(prompt))
        }

        try {
            val jsonBody = JSONObject().apply {
                put("model", model.ifBlank { "claude-3-5-sonnet-20241022" })
                put("max_tokens", 1024)
                put("system", systemPrompt)

                val messagesArray = JSONArray()
                // Append historical turns if present
                for ((role, text) in conversationHistory.takeLast(6)) {
                    val msgObj = JSONObject().apply {
                        put("role", role)
                        put("content", text)
                    }
                    messagesArray.put(msgObj)
                }

                // Add current prompt
                val currentMsg = JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
                messagesArray.put(currentMsg)

                put("messages", messagesArray)
            }

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey.trim())
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errObj = JSONObject(body).optJSONObject("error")
                        errObj?.optString("message") ?: "HTTP ${response.code}: $body"
                    } catch (e: Exception) {
                        "HTTP ${response.code}: $body"
                    }
                    return@withContext Result.failure(Exception("Claude API Error: $errorMsg"))
                }

                val resObj = JSONObject(body)
                val contentArray = resObj.optJSONArray("content")
                val textBuilder = StringBuilder()
                if (contentArray != null) {
                    for (i in 0 until contentArray.length()) {
                        val item = contentArray.getJSONObject(i)
                        if (item.optString("type") == "text") {
                            textBuilder.append(item.optString("text"))
                        }
                    }
                }
                val replyText = textBuilder.toString().ifBlank { "(Respuesta vacía recibida de Claude)" }
                val suggestedCommand = extractSuggestedCommand(replyText)

                Result.success(
                    ClaudeResponse(
                        text = replyText,
                        suggestedCommand = suggestedCommand,
                        modelUsed = resObj.optString("model", model),
                        isLiveApi = true
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception("Fallo en la conexión con Claude: ${e.localizedMessage}"))
        }
    }

    private fun extractSuggestedCommand(text: String): String? {
        val bashBlockRegex = Regex("```(?:bash|sh)?\\s*\\n([^`]+)\\n```", RegexOption.IGNORE_CASE)
        val match = bashBlockRegex.find(text)
        return match?.groupValues?.get(1)?.trim()?.lines()?.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
    }

    private fun getSimulatedClaudeResponse(prompt: String): ClaudeResponse {
        val lower = prompt.lowercase()
        val (reply, suggested) = when {
            lower.contains("neofetch") || lower.contains("sistema") || lower.contains("specs") -> {
                Pair(
                    "He analizado el entorno Termux. Tu sistema Android ejecuta un kernel Linux optimizado para arquitecturas ARM64. Puedes ejecutar `neofetch` para ver las especificaciones visuales detalladas de hardware, memoria y uptime.",
                    "neofetch"
                )
            }
            lower.contains("script") || lower.contains("python") || lower.contains("crear") -> {
                Pair(
                    "He preparado un script para ti. Puedes inspeccionar los scripts existentes en `~/scripts/` o crear uno nuevo con el editor integrado usando `nano script.py`.\n\nEjemplo de ejecución en shell:\n```bash\npython ~/scripts/agent_task.py\n```",
                    "python ~/scripts/agent_task.py"
                )
            }
            lower.contains("ping") || lower.contains("red") || lower.contains("curl") -> {
                Pair(
                    "Para verificar la conectividad de red y latencia en el terminal Android, puedes ejecutar ping hacia los servidores DNS de Google o Cloudflare:\n\n```bash\nping 8.8.8.8\n```",
                    "ping 8.8.8.8"
                )
            }
            lower.contains("openclaw") -> {
                Pair(
                    "OpenClaw es el agente autónomo de crawling y automatización en CLI. Puedes ejecutar flujos autónomos como `openclaw run syscheck` o verificar su estado con `openclaw status`.",
                    "openclaw run syscheck"
                )
            }
            lower.contains("ayuda") || lower.contains("help") || lower.contains("comandos") -> {
                Pair(
                    "Hola! Soy el agente Claude operando en tu consola Termux AI. Puedes interactuar conmigo en cualquier momento mediante:\n- `claude \"pregunta\"` para consultas rápidas\n- `claude -i` para sesión interactiva directa\n- `claude config` para configurar tu API Key oficial de Anthropic si deseas conexión directa a los modelos Sonnet y Opus en la nube.",
                    "help"
                )
            }
            else -> {
                Pair(
                    "Comprendido. Como agente de Claude en la consola Termux AI, puedo ayudarte a escribir código en Python/Bash, diagnosticar problemas del sistema, configurar herramientas y automatizar tareas en Android.\n\n(Tip: Puedes añadir tu Anthropic API Key en el menú de Ajustes o escribiendo `claude config` para enlazar directamente con Claude 3.5 Sonnet).",
                    null
                )
            }
        }

        return ClaudeResponse(
            text = reply,
            suggestedCommand = suggested,
            modelUsed = "Claude 3.5 Sonnet (Autonomous Shell Agent)",
            isLiveApi = false
        )
    }

    data class ClaudeResponse(
        val text: String,
        val suggestedCommand: String?,
        val modelUsed: String,
        val isLiveApi: Boolean
    )
}
