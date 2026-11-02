package com.unciv.ui.screens.worldscreen.view

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.llm.EnemyForces
import com.unciv.logic.llm.LlmClient
import com.unciv.logic.llm.LlmSettings
import com.unciv.logic.llm.StateSerializer
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/**
 * Free-form chat about the current game, answered by the same relay the AI turn uses.
 *
 * The full game state plus a live enemy-force summary is attached to every question, so the model
 * answers about this specific turn rather than from general Civ knowledge.
 */
class LlmChatPopup(
    private val worldScreen: WorldScreen,
    private val settings: LlmSettings
) : Popup(worldScreen, Scrollability.None) {

    private val transcript = Table(BaseScreen.skin)
    private val scrollPane = ScrollPane(transcript, BaseScreen.skin)
    private val inputField = UncivTextField("输入你想问的问题…")
    private val sendButton = "发送".toTextButton()
    private var waiting = false

    init {
        transcript.defaults().growX().pad(4f).left()

        add("AI 问答".toLabel(fontSize = 26)).left().pad(6f).row()
        add(enemySummaryLabel()).colspan(2).left().pad(4f).row()

        scrollPane.setFadeScrollBars(false)
        scrollPane.setScrollingDisabled(true, false)
        add(scrollPane).colspan(2)
            .size(0.6f * stageToShowOn.width, 0.45f * stageToShowOn.height)
            .expand().fill().row()

        add(inputField).expandX().fillX()
        add(sendButton).padLeft(4f).row()

        addCloseButton("关闭")

        sendButton.onClick { ask() }
        inputField.addListener(object : InputListener() {
            override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    ask()
                    return true
                }
                return false
            }
        })

        appendLine("系统", "可以问我这一局的局势、建议、对手强弱。")
    }

    private fun enemySummaryLabel() = EnemyForces.summary(enemyStrengths()).toLabel(fontSize = 18)

    private fun enemyStrengths() = EnemyForces.of(worldScreen.selectedGameView.civView.getCiv())

    private fun appendLine(who: String, text: String) {
        val label = Label("$who：$text", BaseScreen.skin)
        label.wrap = true
        label.setAlignment(Align.topLeft)
        transcript.add(label).row()
        scrollPane.layout()
        scrollPane.scrollTo(0f, 0f, 0f, 0f)
    }

    private fun ask() {
        val question = inputField.text.trim()
        if (question.isEmpty() || waiting) return
        if (!settings.isConfigured()) {
            appendLine("系统", "还没有配置中转站地址、Key 和模型。")
            return
        }
        inputField.setText("")
        appendLine("我", question)
        waiting = true
        sendButton.disable()

        val civ = worldScreen.selectedGameView.civView.getCiv()
        val state = StateSerializer.describe(civ.gameInfo, civ)
        val strengths = EnemyForces.summary(enemyStrengths())

        Concurrency.run("LlmChat") {
            val result = LlmClient.complete(
                settings,
                "You answer questions about the player's current game of a Civilization V style 4X " +
                    "game (Unciv). Be concrete and brief. Use only the provided state.",
                "Current state:\n$state\n\n$strengths\n\nQuestion: $question"
            )
            launchOnGLThread {
                waiting = false
                sendButton.enable()
                val failure = result.exceptionOrNull()
                if (failure != null) {
                    appendLine("AI", "请求失败：${failure.message}")
                    ErrorReportPopup(worldScreen, "AI 请求失败", ErrorReportPopup.describe(failure)).open()
                } else {
                    appendLine("AI", result.getOrNull()!!)
                }
            }
        }
    }
}
