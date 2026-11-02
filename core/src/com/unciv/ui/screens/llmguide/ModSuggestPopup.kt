package com.unciv.ui.screens.llmguide

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.github.GithubAPI
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/**
 * Confirmation before a suggested mod is downloaded.
 *
 * Nothing is fetched until the player presses 同意; once accepted the file is downloaded and
 * extracted through the game's own GitHub code, then loaded into the ruleset cache.
 */
class ModSuggestPopup(
    screen: BaseScreen,
    private val modName: String,
    private val repoUrl: String,
    description: String,
    private val onAccepted: (String) -> Unit
) : Popup(screen, Scrollability.None) {

    private val statusLabel = "".toLabel(fontSize = 16)
    private var downloading = false

    init {
        add("下载模组建议".toLabel(fontSize = 24)).left().pad(6f).row()
        add(modName.toLabel(fontSize = 20)).left().pad(4f).row()
        add("来源：$repoUrl".toLabel(fontSize = 16)).left().pad(4f).row()

        val text = Label(description, BaseScreen.skin)
        text.wrap = true
        val scroll = ScrollPane(text, BaseScreen.skin)
        scroll.setFadeScrollBars(false)
        add(scroll).size(0.75f * stageToShowOn.width, 0.3f * stageToShowOn.height).row()

        val accept = "同意下载".toTextButton()
        val decline = "拒绝".toTextButton()
        accept.onClick {
            if (downloading) return@onClick
            startDownload(accept)
        }
        decline.onClick { close() }

        val buttons = Table()
        buttons.add(accept).padRight(8f)
        buttons.add(decline)
        add(buttons).row()
        add(statusLabel).colspan(2).left().row()
    }

    private fun startDownload(acceptButton: com.badlogic.gdx.scenes.scene2d.ui.TextButton) {
        downloading = true
        acceptButton.isDisabled = true
        statusLabel.setText("准备下载…")

        val repo = repoFromUrl(repoUrl)
        if (repo == null) {
            statusLabel.setText("这个地址不是可以下载的 GitHub 仓库")
            downloading = false
            acceptButton.isDisabled = false
            return
        }

        Concurrency.run("ModDownload") {
            try {
                val folder = repo.downloadAndExtract { state, percent ->
                    launchOnGLThread {
                        statusLabel.setText(
                            when (state.name) {
                                "Downloading" -> "下载中 ${percent ?: 0}%"
                                else -> "解压中…"
                            })
                    }
                }
                launchOnGLThread {
                    if (folder == null) {
                        statusLabel.setText("下载失败，请检查网络或下载源")
                        downloading = false
                        acceptButton.isDisabled = false
                        return@launchOnGLThread
                    }
                    // Load it into the cache so it appears in the mod list; never auto-enable.
                    RulesetCache.loadRulesets()
                    statusLabel.setText("已下载：${folder.name()}（未自动启用）")
                    onAccepted(folder.name())
                }
            } catch (ex: Exception) {
                launchOnGLThread {
                    statusLabel.setText("下载出错：${ex.message}")
                    downloading = false
                    acceptButton.isDisabled = false
                }
            }
        }
    }

    /** Builds a Repo from `https://github.com/owner/name`, which is what the model emits. */
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
