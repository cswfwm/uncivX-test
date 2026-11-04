package com.unciv.ui.screens.llmguide

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.UpdateCheckResult
import com.unciv.logic.UpdateChecker
import com.unciv.logic.llm.ReleaseNotes
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/**
 * Update dialog reached by tapping the version number.
 *
 * Shows the release notes of the newer build, installation advice, and a warning that updating
 * replaces the app - existing saves live in the app folder and are kept on reinstall, but a clean
 * install would remove them, so that risk is spelled out.
 */
class UpdatePopup(stage: Stage) : Popup(stage, Scrollability.None) {

    private val content = Table(BaseScreen.skin)
    private val status = "".toLabel(fontSize = 16)

    init {
        add("检查更新".toLabel(fontSize = 24)).left().pad(6f).row()
        add("当前版本 ${ReleaseNotes.currentVersion()}（构建 ${ReleaseNotes.currentBuild()}）".toLabel(fontSize = 15))
            .left().row()

        val scroll = ScrollPane(content, BaseScreen.skin)
        scroll.setFadeScrollBars(false)
        add(scroll).size(0.78f * stage.width, 0.45f * stage.height).row()
        add(status).colspan(2).left().row()
        addCloseButton("关闭")

        content.add("正在检查…".toLabel(fontSize = 16)).row()
        check()
    }

    private fun check() {
        Concurrency.run("UpdateCheck") {
            val result = UpdateChecker.checkForUpdates()
            launchOnGLThread {
                content.clear()
                when (result) {
                    is UpdateCheckResult.UpToDate -> {
                        content.add("已是最新版本".toLabel(fontSize = 18)).row()
                        status.setText("无需更新")
                    }
                    is UpdateCheckResult.CheckFailed -> {
                        content.add("检查失败，请检查网络后重试".toLabel(fontSize = 18)).row()
                        status.setText("")
                    }
                    is UpdateCheckResult.UpdateAvailable -> showAvailable(result.latestRelease.tag_name,
                        result.latestRelease.body, result.latestRelease.html_url)
                }
            }
        }
    }

    private fun showAvailable(tag: String, body: String, htmlUrl: String) {
        content.add("发现新版本 $tag".toLabel(fontSize = 20)).left().row()

        content.add("本次更新内容".toLabel(fontSize = 16)).padTop(8f).left().row()
        val notes = body.ifBlank { "（该版本未填写更新说明）" }
        val notesLabel = Label(notes, BaseScreen.skin)
        notesLabel.wrap = true
        notesLabel.setAlignment(Align.topLeft)
        content.add(notesLabel).width(0.72f * stage.width).left().row()

        content.add("更新建议".toLabel(fontSize = 16)).padTop(8f).left().row()
        val advice = Label(
            "1. 直接覆盖安装即可，不需要卸载，设置与存档都会保留。\n" +
                "2. 如果你是手动卸载后再装，应用内的存档会一并删除，请先在游戏内备份需要的存档。\n" +
                "3. 更新后建议重启一次应用，确保新版本生效。",
            BaseScreen.skin)
        advice.wrap = true
        advice.setAlignment(Align.topLeft)
        content.add(advice).width(0.72f * stage.width).left().row()

        val open = "打开发布页下载".toTextButton()
        open.onClick {
            // The platform opens links for us; keep the URL visible when it cannot.
            status.setText("下载地址：$htmlUrl")
            com.badlogic.gdx.Gdx.net.openURI(htmlUrl)
        }
        content.add(open).padTop(10f).left().row()

        content.add("下载地址：$htmlUrl".toLabel(fontSize = 12)).left().row()
    }
}
