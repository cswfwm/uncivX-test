package com.unciv.ui.screens.llmguide

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.llm.ModDownloads
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Live view of the mod download queue.
 *
 * Stays open while downloads run; the parent passes its refresh callback so the console can show
 * the same state.
 */
class ModDownloadListPopup(
    screen: BaseScreen,
    private val onClosed: () -> Unit = {}
) : Popup(screen, Scrollability.None) {

    private val listTable = Table(BaseScreen.skin)

    init {
        add("模组下载列表".toLabel(fontSize = 24)).left().pad(6f).row()

        listTable.defaults().growX().pad(2f).left()
        val scroll = ScrollPane(listTable, BaseScreen.skin)
        scroll.setFadeScrollBars(false)
        add(scroll).size(0.75f * stageToShowOn.width, 0.45f * stageToShowOn.height)
            .expand().fill().row()

        val clear = "清除已完成".toTextButton()
        clear.onClick {
            ModDownloads.clearFinished()
            refresh()
        }
        add(clear).left().row()
        addCloseButton("关闭")

        ModDownloads.onChange = { refresh() }
        refresh()
    }

    private fun refresh() {
        listTable.clear()
        val items = ModDownloads.all()
        if (items.isEmpty()) {
            listTable.add("还没有下载任务".toLabel(fontSize = 18)).row()
            return
        }
        for (item in items) {
            val row = Table()
            val stateText = when (item.state) {
                ModDownloads.State.Queued -> "排队中"
                ModDownloads.State.Downloading -> "下载中 ${item.percent}%"
                ModDownloads.State.Extracting -> "解压中…"
                ModDownloads.State.Done -> "已完成"
                ModDownloads.State.Failed -> "失败"
            }
            row.add("${item.name}　$stateText".toLabel(fontSize = 18)).expandX().left()
            if (item.message.isNotEmpty())
                row.add(item.message.toLabel(fontSize = 14)).left()
            listTable.add(row).growX().row()
        }
    }

    override fun close() {
        ModDownloads.onChange = null
        onClosed()
        super.close()
    }
}
