package com.unciv.ui.screens.llmguide

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.github.GithubAPI
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen

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
        acceptButton.isDisabled = true
        val repo = Regex("https?://github\\.com/[^/]+/[^/?#]+").containsMatchIn(repoUrl)
        if (!repo) {
            statusLabel.setText("这个地址不是可以下载的 GitHub 仓库")
            acceptButton.isDisabled = false
            return
        }
        // Queue it: the download list shows progress and keeps working after this dialog closes.
        com.unciv.logic.llm.ModDownloads.add(modName, repoUrl)
        statusLabel.setText("已加入下载列表，可在「下载列表」查看进度")
        onAccepted(modName)
    }
}
