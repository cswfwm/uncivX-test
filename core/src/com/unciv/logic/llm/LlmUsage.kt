package com.unciv.logic.llm

/**
 * Running token accounting for this session.
 *
 * Relays report `usage` per completion; it is summed here so the settings page and the panel can
 * show what the AI has actually cost. Counts are session-only and are not written into saves.
 */
object LlmUsage {
    @Volatile
    var promptTokens: Long = 0
        private set

    @Volatile
    var completionTokens: Long = 0
        private set

    @Volatile
    var requestCount: Int = 0
        private set

    val totalTokens: Long get() = promptTokens + completionTokens

    fun record(prompt: Long, completion: Long) {
        promptTokens += prompt
        completionTokens += completion
        requestCount += 1
    }

    fun reset() {
        promptTokens = 0
        completionTokens = 0
        requestCount = 0
    }

    /** One-line summary for the settings page and the floating panel. */
    fun summary(): String =
        if (requestCount == 0) "本次会话尚未消耗 token"
        else "本次会话：$requestCount 次请求，输入 $promptTokens，输出 $completionTokens，共 $totalTokens token"
}
