package com.unciv.logic.llm

import com.unciv.logic.github.GithubAPI
import com.unciv.logic.github.GithubAPI.downloadAndExtract
import com.unciv.models.ruleset.RulesetCache
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/**
 * Mod download queue shared by the console and its download list.
 *
 * Downloads run one at a time so progress stays readable on a phone connection, and each entry
 * keeps its own state so the list can be reopened while a download is still in flight.
 */
object ModDownloads {

    enum class State { Queued, Downloading, Extracting, Done, Failed }

    data class Item(
        val name: String,
        val repoUrl: String,
        var state: State = State.Queued,
        var percent: Int = 0,
        var message: String = ""
    )

    private val items = ArrayList<Item>()
    private var running = false

    /** Called on the GL thread whenever an item changes. */
    var onChange: (() -> Unit)? = null

    fun all(): List<Item> = items.toList()

    fun add(name: String, repoUrl: String) {
        items.add(Item(name, repoUrl))
        notifyChanged()
        startNext()
    }

    fun clearFinished() {
        items.removeAll { it.state == State.Done || it.state == State.Failed }
        notifyChanged()
    }

    private fun notifyChanged() {
        launchOnGLThread { onChange?.invoke() }
    }

    private fun startNext() {
        if (running) return
        val next = items.firstOrNull { it.state == State.Queued } ?: return
        running = true
        next.state = State.Downloading
        notifyChanged()

        Concurrency.run("ModDownload") {
            try {
                val repo = repoFromUrl(next.repoUrl)
                if (repo == null) {
                    next.state = State.Failed
                    next.message = "不是可下载的 GitHub 仓库地址"
                } else {
                    val folder = repo.downloadAndExtract { state, percent ->
                        next.percent = percent ?: 0
                        if (state == com.unciv.logic.github.DownloadAndExtractState.Finishing)
                            next.state = State.Extracting
                        notifyChanged()
                    }
                    if (folder == null) {
                        next.state = State.Failed
                        next.message = "下载失败，请检查网络或加速源"
                    } else {
                        RulesetCache.loadRulesets()
                        next.state = State.Done
                        next.percent = 100
                        next.message = "${folder.name()}，未自动启用"
                    }
                }
            } catch (ex: Exception) {
                next.state = State.Failed
                next.message = ex.message ?: "未知错误"
            } finally {
                running = false
                notifyChanged()
                startNext()
            }
        }
    }

    private fun repoFromUrl(url: String): GithubAPI.Repo? {
        val match = Regex("https?://github\\.com/([^/]+)/([^/?#]+)").find(url) ?: return null
        val owner = match.groupValues[1]
        val name = match.groupValues[2].removeSuffix(".git")
        return GithubAPI.Repo().apply {
            this.owner = GithubAPI.RepoOwner().apply { this.login = owner }
            this.name = name
            this.html_url = "https://github.com/$owner/$name"
            this.default_branch = "master"
        }
    }
}
