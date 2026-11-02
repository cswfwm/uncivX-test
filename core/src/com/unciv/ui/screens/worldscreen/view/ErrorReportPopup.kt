package com.unciv.ui.screens.worldscreen.view

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Readable, copyable error report window.
 *
 * The game's own crash screen replaces the whole screen and cannot be reached from inside a
 * running game, and the short toast popups give no way to extract the trace. This popup shows the
 * full trace in a scrollable area and copies it in one tap.
 */
class ErrorReportPopup(
    screen: BaseScreen,
    title: String,
    private val detail: String
) : Popup(screen, Scrollability.None) {

    private val statusLabel = "".toLabel(fontSize = 18)

    init {
        add(title.toLabel(fontSize = 24, fontColor = Color.SALMON)).left().pad(6f).row()

        val text = Label(detail, BaseScreen.skin)
        text.wrap = true
        text.setAlignment(Align.topLeft)

        val scroll = ScrollPane(text, BaseScreen.skin)
        scroll.setFadeScrollBars(false)
        scroll.setScrollingDisabled(true, false)
        add(scroll).colspan(2)
            .size(0.75f * stageToShowOn.width, 0.5f * stageToShowOn.height)
            .expand().fill().row()

        val copyButton = "复制日志".toTextButton()
        copyButton.onClick {
            Gdx.app.clipboard.contents = detail
            statusLabel.color = Color.GREEN
            statusLabel.setText("已复制到剪贴板")
        }

        val closeButton = "关闭".toTextButton()
        closeButton.onClick { close() }

        val buttons = Table()
        buttons.add(copyButton).padRight(8f)
        buttons.add(closeButton)
        add(buttons).colspan(2).row()
        add(statusLabel).colspan(2).left().row()
    }

    companion object {
        /** Formats a throwable with its full cause chain, including stack traces. */
        fun describe(error: Throwable): String {
            val out = StringBuilder()
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < 8) {
                out.append(if (depth == 0) "" else "\nCaused by: ")
                out.append(current.toString()).append("\n\n")
                current.stackTrace.forEach { out.append("    at ").append(it).append("\n") }
                current = current.cause
                depth++
            }
            return out.toString()
        }
    }
}
