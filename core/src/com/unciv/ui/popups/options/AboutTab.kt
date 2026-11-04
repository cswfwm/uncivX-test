package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.llm.ReleaseNotes
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.llmguide.UpdatePopup

/**
 * About page for this build: version, bundled changelog, and a version number that checks for a
 * newer release when tapped.
 */
internal class AboutTab(
    optionsPopup: OptionsPopup
) : OptionsPopupTab(optionsPopup) {

    override fun lateInitialize() {
        top()

        addHeader("关于 uncivCN-X")
        add(ReleaseNotes.headline().toLabel(fontSize = 18)).colspan(2).left().row()
        add("基于 UncivCN 4.22.4.4（Unciv 分支），遵循 MPL-2.0 许可证".toLabel(fontSize = 14))
            .colspan(2).left().row()

        val versionRow = Table()
        versionRow.add("当前版本".toLabel(fontSize = 16)).padRight(8f)
        val versionButton = "点击检查更新（${ReleaseNotes.currentVersion()}）".toTextButton()
        versionButton.onClick { checkForUpdate() }
        versionRow.add(versionButton)
        add(versionRow).colspan(2).left().row()

        addHeader("更新日志")

        val logTable = Table(BaseScreen.skin)
        logTable.defaults().left().growX().pad(2f)
        for (entry in ReleaseNotes.changelog) {
            logTable.add("${entry.version}　${entry.date}".toLabel(fontSize = 17)).row()
            for (item in entry.items)
                logTable.add("· $item".toLabel(fontSize = 14)).padLeft(12f).row()
            logTable.add(Label("", BaseScreen.skin)).row()
        }
        val scroll = ScrollPane(logTable, BaseScreen.skin)
        scroll.setFadeScrollBars(false)
        add(scroll).colspan(2).size(0.8f * optionsPopup.stage.width, 0.45f * optionsPopup.stage.height).row()

        super.lateInitialize()
    }

    companion object {
        /** Reused by the main menu's About popup, which is not an [OptionsPopupTab]. */
        fun asTable(): Table {
            val table = Table(BaseScreen.skin)
            table.pad(16f)
            table.add(ReleaseNotes.headline().toLabel(fontSize = 18)).left().row()
            table.add("基于 UncivCN 4.22.4.4，遵循 MPL-2.0 许可证".toLabel(fontSize = 14)).left().row()
            table.add("更新日志".toLabel(fontSize = 17)).padTop(10f).left().row()
            for (entry in ReleaseNotes.changelog.take(1)) {
                table.add("${entry.version}　${entry.date}".toLabel(fontSize = 16)).left().row()
                for (item in entry.items.take(8))
                    table.add("· $item".toLabel(fontSize = 13)).padLeft(10f).left().row()
            }
            return table
        }
    }

    private fun checkForUpdate() {
        UpdatePopup(optionsPopup.stage).open()
    }
}
