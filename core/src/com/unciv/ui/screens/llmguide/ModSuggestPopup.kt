package com.unciv.ui.screens.llmguide

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Confirmation shown before a suggested mod is downloaded.
 *
 * Nothing is downloaded until the player presses 同意; the description comes from the model (and
 * is translated to Chinese first when it arrives in English).
 */
class ModSuggestPopup(
    screen: BaseScreen,
    private val modName: String,
    private val repoUrl: String,
    description: String,
    private val onAccept: () -> Unit
) : Popup(screen, Scrollability.None) {

    init {
        add("下载模组建议".toLabel(fontSize = 24)).left().pad(6f).row()
        add(modName.toLabel(fontSize = 20)).left().pad(4f).row()
        add("来源：$repoUrl".toLabel(fontSize = 16)).left().pad(4f).row()

        val text = Label(description, BaseScreen.skin)
        text.wrap = true
        val scroll = ScrollPane(text, BaseScreen.skin)
        scroll.setFadeScrollBars(false)
        add(scroll).size(0.75f * stageToShowOn.width, 0.35f * stageToShowOn.height).row()

        val accept = "同意下载".toTextButton()
        accept.onClick {
            onAccept()
            close()
        }
        val decline = "拒绝".toTextButton()
        decline.onClick { close() }

        val buttons = Table()
        buttons.add(accept).padRight(8f)
        buttons.add(decline)
        add(buttons).row()
    }
}
