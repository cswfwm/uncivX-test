package com.unciv.logic.llm

import kotlinx.serialization.Serializable

/**
 * Configuration for the LLM player proxy.
 *
 * [baseUrl] is a full OpenAI-compatible endpoint, e.g. `https://api.example.com/v1/chat/completions`.
 * The key is stored in the local settings file only - it is never written into saves or uploaded
 * to a multiplayer server.
 */
class LlmSettings {
    var baseUrl: String = ""
    var apiKey: String = ""
    var model: String = "gpt-4o-mini"

    /** Master switch for the in-game floating panel; on by default so the orb is always reachable. */
    var enabled: Boolean = true

    /** Whether the AI plans each turn automatically, or only when the panel button is pressed. */
    var autoPlayEachTurn: Boolean = false

    /**
     * In multiplayer, press "I'm done" automatically once the AI finished its turn.
     *
     * Off by default: submitting ends the player's window, so this should be a deliberate choice.
     */
    var autoFinishTurn: Boolean = false

    /** [LlmDecisionGranularity.name] */
    var granularity: String = LlmDecisionGranularity.Direction.name

    /** Reveal the whole map to the viewer and to the LLM input. Does not touch DebugUtils. */
    var revealMap: Boolean = false

    /** Request timeout in seconds. Reasoning stations can be slow on large prompts. */
    var timeoutSeconds: Int = 120

    /** Radar: mark enemy units on the map regardless of exploration. */
    var showEnemyRadar: Boolean = false

    /** Show a name label above each unit on the map. */
    var showUnitNames: Boolean = false

    /** Ask the player before the AI starts a war instead of declaring it directly. */
    var confirmWarDeclarations: Boolean = true

    /** Let the AI look up game mechanics online when a question is about concepts, not the board. */
    var allowWebSearch: Boolean = false

    fun granularityOrDefault(): LlmDecisionGranularity =
        LlmDecisionGranularity.entries.firstOrNull { it.name == granularity }
            ?: LlmDecisionGranularity.Direction

    fun isConfigured(): Boolean = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/** How much of the turn the LLM owns. */
enum class LlmDecisionGranularity {
    /** The LLM picks strategy (tech, social policy stance, build focus, diplomacy stance); the
     *  stock automation carries the decisions out. Cheapest and most robust. */
    Direction,

    /** The LLM names each city's construction and each unit's action. Most human-like, but large
     *  prompts and more chances of an invalid action, which falls back to stock automation. */
    PerItem
}

@Serializable
data class LlmChatMessage(
    val role: String,
    val content: String
)
