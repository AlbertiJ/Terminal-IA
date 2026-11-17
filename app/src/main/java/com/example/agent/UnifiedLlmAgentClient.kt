package com.example.agent

import com.example.model.AgentSettings
import com.example.model.AiProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class LlmAgentResponse(
    val explanationText: String,
    val proposedCommands: List<String>,
    val modelUsed: String,
    val providerLabel: String
)

/**
 * Real LLM client supporting Anthropic Cloud, OpenAI-compatible Cloud (HTTPS),
 * and local OpenAI-compatible servers running inside Termux strictly on 127.0.0.1/localhost.
 * Never returns fabricated/simulated AI responses when unconfigured.
 */
class UnifiedLlmAgentClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val activeCall = AtomicReference<Call?>(null)

    fun cancelActiveCall() {
        activeCall.getAndSet(null)?.cancel()
    }

    suspend fun queryProvider(
        prompt: String,
        settings: AgentSettings,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        anthropicUrlOverride: String? = null
    ): Result<LlmAgentResponse> = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Escribe una instrucción o consulta para el agente."))
        }

        val validationError = validateProviderConfiguration(settings)
        if (validationError != null) {
            return@withContext Result.failure(IllegalStateException(validationError))
        }

        val systemInstruction = buildSystemPrompt(settings)

        return@withContext when (settings.aiProviderType) {
            AiProviderType.NONE -> {
                Result.failure(
                    IllegalStateException(
                        "No hay ningún proveedor de IA configurado. Configura una API Key en la nube o un servidor local OpenAI en 127.0.0.1 desde Ajustes."
                    )
                )
            }
            AiProviderType.ANTHROPIC_CLOUD -> {
                callAnthropicMessagesApi(
                    endpoint = anthropicUrlOverride ?: "https://api.anthropic.com/v1/messages",
                    apiKey = settings.claudeApiKey.trim(),
                    model = settings.claudeModel.trim().ifBlank { "claude-sonnet-4-20250514" },
                    systemPrompt = systemInstruction,
                    userPrompt = cleanPrompt,
                    history = conversationHistory
                )
            }
            AiProviderType.OPENAI_CLOUD -> {
                callOpenAiChatCompletions(
                    baseUrl = settings.openAiBaseUrl.trim(),
                    apiKey = settings.openAiApiKey.trim(),
                    model = settings.openAiModel.trim().ifBlank { "gpt-4o-mini" },
                    systemPrompt = systemInstruction,
                    userPrompt = cleanPrompt,
                    history = conversationHistory,
                    providerLabel = "OpenAI Cloud"
                )
            }
            AiProviderType.TERMUX_LOCAL_OPENAI -> {
                callOpenAiChatCompletions(
                    baseUrl = settings.localOpenAiBaseUrl.trim(),
                    apiKey = settings.localOpenAiToken.trim(),
                    model = settings.localOpenAiModel.trim().ifBlank { "qwen2.5-coder:7b" },
                    systemPrompt = systemInstruction,
                    userPrompt = cleanPrompt,
                    history = conversationHistory,
                    providerLabel = "Local OpenAI (Termux 127.0.0.1)"
                )
            }
        }
    }

    private fun buildSystemPrompt(settings: AgentSettings): String {
        val workDir = settings.agentWorkingDir.trim().ifBlank { "~" }
        val base = settings.claudeSystemPrompt.trim().ifBlank {
            "Eres un asistente técnico de Linux y Termux."
        }
        return "$base\n" +
            "Reglas estrictas:\n" +
            "1. Solo PROPONES comandos para que el usuario los apruebe antes de ejecutarse por SSH en Termux.\n" +
            "2. Nunca afirmes haber ejecutado un comando ni inventes salidas del sistema.\n" +
            "3. Carpeta de trabajo objetivo: $workDir\n" +
            "4. Coloca cada comando propuesto dentro de un bloque ```sh ... ``` (un comando por línea)."
    }

    private fun callAnthropicMessagesApi(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        history: List<Pair<String, String>>
    ): Result<LlmAgentResponse> {
        var call: Call? = null
        return try {
            val messagesArray = JSONArray()
            for ((role, text) in history.takeLast(6)) {
                messagesArray.put(JSONObject().put("role", role).put("content", text))
            }
            messagesArray.put(JSONObject().put("role", "user").put("content", userPrompt))

            val bodyJson = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1024)
                put("system", systemPrompt)
                put("messages", messagesArray)
            }

            val req = Request.Builder()
                .url(endpoint)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            call = client.newCall(req)
            activeCall.set(call)
            call.execute().use { response ->
                val rawBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}"
                    return Result.failure(IllegalStateException("Error Anthropic API ($errMsg)"))
                }

                val resObj = JSONObject(rawBody)
                val contentArray = resObj.optJSONArray("content")
                val textBuilder = StringBuilder()
                if (contentArray != null) {
                    for (i in 0 until contentArray.length()) {
                        val item = contentArray.optJSONObject(i) ?: continue
                        if (item.optString("type") == "text") {
                            textBuilder.append(item.optString("text"))
                        }
                    }
                }
                val replyText = textBuilder.toString().trim()
                if (replyText.isEmpty()) {
                    return Result.failure(IllegalStateException("El proveedor Anthropic devolvió una respuesta vacía."))
                }

                Result.success(
                    LlmAgentResponse(
                        explanationText = replyText,
                        proposedCommands = extractProposedCommands(replyText),
                        modelUsed = resObj.optString("model", model),
                        providerLabel = "Anthropic Cloud"
                    )
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            if (call?.isCanceled() == true) {
                Result.failure(CancellationException("Consulta cancelada por el usuario."))
            } else {
                Result.failure(IllegalStateException("Error al consultar Anthropic Cloud: ${e.localizedMessage ?: "fallo de red"}"))
            }
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    private fun callOpenAiChatCompletions(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        history: List<Pair<String, String>>,
        providerLabel: String
    ): Result<LlmAgentResponse> {
        var call: Call? = null
        return try {
            val completionsUrl = buildChatCompletionsUrl(baseUrl)
            val messagesArray = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                for ((role, text) in history.takeLast(6)) {
                    put(JSONObject().put("role", role).put("content", text))
                }
                put(JSONObject().put("role", "user").put("content", userPrompt))
            }

            val bodyJson = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("temperature", 0.2)
            }

            val reqBuilder = Request.Builder()
                .url(completionsUrl)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))

            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            call = client.newCall(reqBuilder.build())
            activeCall.set(call)
            call.execute().use { response ->
                val rawBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}"
                    return Result.failure(IllegalStateException("Error $providerLabel ($errMsg)"))
                }

                val resObj = JSONObject(rawBody)
                val firstChoice = resObj.optJSONArray("choices")?.optJSONObject(0)
                val replyText = firstChoice?.optJSONObject("message")?.optString("content")?.trim().orEmpty()
                if (replyText.isEmpty()) {
                    return Result.failure(IllegalStateException("$providerLabel devolvió una respuesta vacía."))
                }

                Result.success(
                    LlmAgentResponse(
                        explanationText = replyText,
                        proposedCommands = extractProposedCommands(replyText),
                        modelUsed = resObj.optString("model", model),
                        providerLabel = providerLabel
                    )
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            if (call?.isCanceled() == true) {
                Result.failure(CancellationException("Consulta cancelada por el usuario."))
            } else {
                Result.failure(
                    IllegalStateException(
                        "Error al conectar con $providerLabel (${baseUrl.trim()}): ${e.localizedMessage ?: "sin respuesta"}"
                    )
                )
            }
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    companion object {
        fun validateProviderConfiguration(settings: AgentSettings): String? {
            return when (settings.aiProviderType) {
                AiProviderType.NONE ->
                    "No hay ningún proveedor de IA configurado. Configura Anthropic/OpenAI Cloud o un servidor local OpenAI en Termux (127.0.0.1) desde Ajustes."
                AiProviderType.ANTHROPIC_CLOUD -> {
                    if (settings.claudeApiKey.isBlank()) {
                        "Falta tu API Key de Anthropic en Ajustes > Proveedor IA."
                    } else null
                }
                AiProviderType.OPENAI_CLOUD -> {
                    val parsed = settings.openAiBaseUrl.trim().toHttpUrlOrNull()
                    when {
                        parsed == null -> "La URL de OpenAI Cloud no es válida."
                        parsed.scheme != "https" -> "OpenAI Cloud requiere HTTPS cifrado (usa 'Local OpenAI en Termux' para 127.0.0.1)."
                        settings.openAiApiKey.isBlank() -> "Falta tu API Key para OpenAI Cloud en Ajustes > Proveedor IA."
                        else -> null
                    }
                }
                AiProviderType.TERMUX_LOCAL_OPENAI -> {
                    val parsed = settings.localOpenAiBaseUrl.trim().toHttpUrlOrNull()
                    when {
                        parsed == null -> "La URL del servidor local en Termux no es válida (ejemplo: http://127.0.0.1:11434/v1)."
                        parsed.host != "127.0.0.1" && parsed.host != "localhost" ->
                            "El servidor local en Termux debe apuntar estrictamente a 127.0.0.1 o localhost (actual: ${parsed.host})."
                        else -> null
                    }
                }
            }
        }

        fun buildChatCompletionsUrl(baseUrl: String): String {
            val clean = baseUrl.trim().trimEnd('/')
            return if (clean.endsWith("/chat/completions")) {
                clean
            } else {
                "$clean/chat/completions"
            }
        }

        fun extractProposedCommands(text: String): List<String> {
            val results = linkedSetOf<String>()
            val codeBlockRegex = Regex("```(?:bash|sh|zsh|shell)?\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            for (match in codeBlockRegex.findAll(text)) {
                val block = match.groupValues[1]
                for (rawLine in block.lines()) {
                    val cleaned = rawLine.trim()
                        .removePrefix("$ ")
                        .trim()
                    if (cleaned.isNotEmpty() && !cleaned.startsWith("#")) {
                        results.add(cleaned)
                    }
                }
            }
            if (results.isEmpty()) {
                val cmdLineRegex = Regex("""(?im)^(?:COMMAND|COMANDO):\s*(.+)$""")
                for (match in cmdLineRegex.findAll(text)) {
                    val cmd = match.groupValues[1].trim()
                    if (cmd.isNotEmpty()) results.add(cmd)
                }
            }
            return results.toList()
        }
    }
}
