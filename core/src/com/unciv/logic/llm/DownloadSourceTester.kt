package com.unciv.logic.llm

import com.unciv.logic.UncivKtor
import com.unciv.logic.github.GithubAPI
import com.unciv.utils.Log
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Measures which mod download mirrors actually work from the player's own network.
 *
 * The result depends entirely on the device's connectivity, so this only runs when the player asks
 * for it, never during a build or at startup.
 */
object DownloadSourceTester {

    /** Small file used as the probe: present in every Unciv checkout, a few KiB at most. */
    private const val PROBE =
        "https://raw.githubusercontent.com/yairm210/Unciv/master/README.md"

    data class Result(
        val source: GithubAPI.ModDownloadSource,
        val ok: Boolean,
        val millis: Long,
        val speedKbPerSecond: Double,
        val detail: String
    )

    private fun prefixOf(source: GithubAPI.ModDownloadSource): String = when (source) {
        GithubAPI.ModDownloadSource.Official -> ""
        GithubAPI.ModDownloadSource.Custom -> {
            val custom = com.unciv.UncivGame.Current.settings.customModDownloadPrefix.trim().trimEnd('/')
            if (custom.isEmpty()) "" else "$custom/"
        }
        else -> source.urlPrefix
    }

    private suspend fun probe(source: GithubAPI.ModDownloadSource): Result {
        val url = prefixOf(source) + PROBE
        val start = System.currentTimeMillis()
        return try {
            // A range-limited GET only fetches the first bytes, so a probe costs a few hundred
            // milliseconds instead of a full file download.
            val response = UncivKtor.getOrNull(url) {
                header(HttpHeaders.Range, "bytes=0-1023")
            }
            val millis = System.currentTimeMillis() - start
            if (response == null) {
                Result(source, false, millis, 0.0, "无法连接")
            } else {
                val bytes = response.bodyAsText().length
                val seconds = (millis / 1000.0).coerceAtLeast(0.001)
                Result(source, true, millis, bytes / 1024.0 / seconds, "可用，${millis}ms")
            }
        } catch (ex: Exception) {
            Log.debug("Probe failed for ${source.name}: ${ex.message}")
            Result(source, false, System.currentTimeMillis() - start, 0.0, ex.message ?: "失败")
        }
    }

    /** Tests every built-in mirror at once and returns them fastest-first. */
    suspend fun testAll(): List<Result> = coroutineScope {
        val sources = listOf(
            GithubAPI.ModDownloadSource.Official,
            GithubAPI.ModDownloadSource.GhProxyCom,
            GithubAPI.ModDownloadSource.GhFastTop,
            GithubAPI.ModDownloadSource.GhProxyNet
        )
        val results = sources.map { async { probe(it) } }.map { it.await() }
        results.sortedWith(compareByDescending<Result> { it.ok }.thenBy { it.millis })
    }

    /** Applies the fastest working mirror, or returns null when none responded. */
    fun applyFastest(results: List<Result>): GithubAPI.ModDownloadSource? {
        val best = results.firstOrNull { it.ok } ?: return null
        com.unciv.UncivGame.Current.settings.modDownloadSource = best.source.name
        com.unciv.UncivGame.Current.settings.save()
        return best.source
    }
}
