package com.unciv.logic.llm

import com.unciv.logic.github.Github
import com.unciv.utils.Log

/**
 * Searches the Unciv mod ecosystem for the AI console.
 *
 * Mods are GitHub repositories tagged `unciv-mod`; the game already ships the search call, so the
 * console reuses it instead of guessing repository names from the model's memory.
 */
object ModSearch {

    data class Hit(val name: String, val url: String, val description: String, val stars: Int)

    /**
     * Strips the filler words players use ("帮我找一个娘化模组") so the remaining keywords can match
     * repository names and descriptions.
     */
    fun keywords(query: String): String =
        query.replace(Regex("(帮我|请|找|一个|一下|搜索|搜|有没有|推荐|模组|插件|mod|Mod|MOD|的|吗|？|\?)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** @return matching mods, most-starred first, or an empty list when the query finds nothing. */
    suspend fun search(query: String, limit: Int = 8): List<Hit> {
        val terms = keywords(query)
        if (terms.isBlank()) return emptyList()
        return try {
            val result = Github.tryGetGithubReposWithTopic(1, 30, terms) ?: return emptyList()
            result.items
                .asSequence()
                .filter { it.name.isNotBlank() && it.html_url.isNotBlank() }
                .sortedByDescending { it.stargazers_count }
                .take(limit)
                .map {
                    Hit(it.name, it.html_url, it.description?.take(200) ?: "（无描述）", it.stargazers_count)
                }
                .toList()
        } catch (ex: Exception) {
            Log.debug("Mod search failed for $query: ${ex.message}")
            emptyList()
        }
    }

    /** Renders results for the prompt so the model can describe and recommend them. */
    fun asPromptText(hits: List<Hit>): String {
        if (hits.isEmpty()) return "（没有搜到匹配的模组）"
        return hits.joinToString("\n") {
            "- ${it.name} | ${it.url} | 星标 ${it.stars} | ${it.description}"
        }
    }
}
