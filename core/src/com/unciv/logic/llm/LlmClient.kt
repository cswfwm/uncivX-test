package com.unciv.logic.llm

import com.unciv.logic.UncivKtor
import com.unciv.utils.Log
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Minimal client for OpenAI-compatible `/chat/completions` endpoints (works with relay stations).
 *
 * Every failure path returns a [Result.failure] instead of throwing, so a bad key or a flaky relay
 * degrades the AI turn into stock automation rather than breaking the game.
 */
object LlmClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun complete(
        settings: LlmSettings,
        systemPrompt: String,
        userPrompt: String
    ): Result<String> {
        if (!settings.isConfigured())
            return Result.failure(IllegalStateException("LLM endpoint not configured"))

        val payload = buildJsonObject {
            put("model", settings.model)
            put("temperature", 0.3)
            put("max_tokens", 2048)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }

        return try {
            val response = UncivKtor.client.post(normalizeEndpoint(settings.baseUrl)) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${settings.apiKey}")
                setBody(payload.toString())
            }
            val text = response.bodyAsText()
            if (response.status.value !in 200..299)
                return Result.failure(IllegalStateException("HTTP ${response.status.value}: ${text.take(300)}"))
            parseContent(text)
        } catch (ex: Exception) {
            Log.error("LLM request failed: ${ex.message}")
            Result.failure(ex)
        }
    }

    /**
     * Relays are usually documented as a base URL (`https://host/v1`) or a bare host. Posting to
     * those yields a 301 from the relay, and a redirected POST turns into a GET, so the API never
     * answers. Complete the path here instead of trusting what the user typed.
     */
    fun normalizeEndpoint(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val withoutTrailingSlash = trimmed.trimEnd('/')
        if (withoutTrailingSlash.endsWith("/chat/completions")) return withoutTrailingSlash
        if (withoutTrailingSlash.endsWith("/v1")) return "$withoutTrailingSlash/chat/completions"
        return "$withoutTrailingSlash/v1/chat/completions"
    }

    fun parseContent(rawBody: String): Result<String> = try {
        val root = json.parseToJsonElement(rawBody).jsonObject
        val choices = root["choices"] as? JsonArray ?: return Result.failure(
            IllegalStateException("No choices in response"))
        val content = (choices.firstOrNull() as? JsonObject)?.get("message")
            ?.jsonObject?.get("content")?.jsonPrimitive?.content
        if (content.isNullOrBlank())
            Result.failure(IllegalStateException("Empty completion content"))
        else
            Result.success(content)
    } catch (ex: Exception) {
        Result.failure(ex)
    }
}
