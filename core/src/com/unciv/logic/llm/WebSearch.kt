package com.unciv.logic.llm

import com.unciv.logic.UncivKtor
import com.unciv.utils.Log
import io.ktor.client.statement.bodyAsText

/**
 * Optional web lookup for the AI.
 *
 * Only consulted when the player enables it and only for explicit "what is X" style questions:
 * the game state already answers everything about the current board. Wikipedia's REST API is used
 * because it needs no key and returns stable plain text.
 */
object WebSearch {

    private const val ENDPOINT = "https://zh.wikipedia.org/api/rest_v1/page/summary/"

    /**
     * @return a short extract, or null when unavailable. Never throws.
     */
    suspend fun lookup(query: String): String? {
        val title = query.trim().replace(' ', '_')
        if (title.isEmpty()) return null
        return try {
            val response = UncivKtor.getOrNull(ENDPOINT + java.net.URLEncoder.encode(title, "UTF-8")) ?: return null
            val body = response.bodyAsText()
            val extract = Regex("\"extract\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .find(body)?.groupValues?.get(1)
                ?: return null
            extract.replace("\\n", " ").replace("\\\"", "\"").take(600).ifBlank { null }
        } catch (ex: Exception) {
            Log.debug("Web lookup failed for $query: ${ex.message}")
            null
        }
    }

    /** Heuristic: only questions about concepts, not about the current board. */
    fun shouldLookUp(question: String): Boolean {
        val trimmed = question.trim()
        if (trimmed.length < 4) return false
        val asksWhat = listOf("是什么", "什么是", "介绍", "如何", "怎么", "为什么", "攻略", "技巧")
        return asksWhat.any { trimmed.contains(it) }
    }
}
