package com.unciv.ui.screens.llmguide

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.llm.LlmClient
import com.unciv.logic.llm.LlmUsage
import com.unciv.logic.llm.WebSearch
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/**
 * Terminal-style AI console, reachable from the main menu.
 *
 * Commands go to the configured relay; the model may propose settings changes and mod downloads,
 * but every such action is surfaced as its own confirmation dialog and never applied silently.
 */
class AiGuideScreen : BaseScreen() {

    private val transcript = Table(BaseScreen.skin)
    private val scrollPane: ScrollPane
    private val inputField = UncivTextField("输入指令，例如：推荐一个适合新手的模组")
    private val sendButton = "发送".toTextButton()
    private val usageLabel = "".toLabel(fontSize = 16)
    private var busy = false

    init {
        val root = Table()
        root.setFillParent(true)
        root.background = ImageGetter.getWhiteDotDrawable().tint(com.badlogic.gdx.graphics.Color(0.1f, 0.1f, 0.1f, 0.97f))
        root.pad(10f)

        val title = Table()
        title.add("AI 指导".toLabel(fontSize = 28)).expandX().left()
        val close = "返回".toTextButton()
        close.onClick { game.popScreen() }
        title.add(close).right()
        root.add(title).growX().row()

        transcript.defaults().growX().pad(3f).left()
        scrollPane = ScrollPane(transcript, BaseScreen.skin)
        scrollPane.setFadeScrollBars(false)
        scrollPane.setScrollingDisabled(true, false)
        root.add(scrollPane).grow().row()

        usageLabel.setText(LlmUsage.summary())
        root.add(usageLabel).left().row()

        val inputRow = Table()
        inputRow.add(inputField).growX().padRight(6f)
        sendButton.onClick { send() }
        inputRow.add(sendButton)
        root.add(inputRow).growX().row()

        stage.addActor(root)

        inputField.addListener(object : InputListener() {
            override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    send()
                    return true
                }
                return false
            }
        })

        append("系统", "可以让我做这些事：查询或翻译游戏机制、推荐并下载模组、查看或修改游戏设置。")
    }

    private fun append(who: String, text: String) {
        val label = Label("$who：$text", BaseScreen.skin)
        label.wrap = true
        label.setAlignment(Align.topLeft)
        transcript.add(label).row()
        scrollPane.layout()
        scrollPane.scrollTo(0f, 0f, 0f, 0f)
    }

    private fun send() {
        val command = inputField.text.trim()
        if (command.isEmpty() || busy) return
        val settings = game.settings.llm
        if (!settings.isConfigured()) {
            append("系统", "还没有配置中转站地址、Key 和模型，请先到设置里的 AI 页填写。")
            return
        }
        inputField.setText("")
        append("我", command)
        busy = true
        sendButton.isDisabled = true

        Concurrency.run("AiGuide") {
            val reference = if (settings.allowWebSearch && WebSearch.shouldLookUp(command))
                WebSearch.lookup(command) else null
            val prompt = buildString {
                appendLine("玩家指令：$command")
                if (reference != null) appendLine("联网参考资料：$reference")
                appendLine()
                appendLine(MOD_AND_SETTINGS_INSTRUCTIONS)
            }
            val result = LlmClient.complete(
                settings,
                "你是 Unciv 的游戏顾问，用简体中文回答，回答要具体、简短。" +
                    "你可以建议下载模组和修改设置，但都必须由玩家确认。",
                prompt
            )
            launchOnGLThread {
                busy = false
                sendButton.isDisabled = false
                usageLabel.setText(LlmUsage.summary())
                val failure = result.exceptionOrNull()
                if (failure != null) {
                    append("AI", "请求失败：${failure.message}")
                    showError(failure)
                } else {
                    val answer = result.getOrNull()!!
                    append("AI", stripDirectives(answer))
                    handleDirectives(answer)
                }
            }
        }
    }

    /** Removes the machine-readable directive lines from what the player sees. */
    private fun stripDirectives(answer: String): String =
        answer.lines().filterNot { it.trimStart().startsWith("MOD:") || it.trimStart().startsWith("SET:") }
            .joinToString("\n").trim()

    /** Surfaces each proposed mod or settings change as its own confirmation. */
    private fun handleDirectives(answer: String) {
        for (line in answer.lines()) {
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("MOD:") -> {
                    val parts = trimmed.removePrefix("MOD:").split("|").map { it.trim() }
                    if (parts.size >= 2) {
                        val description = parts.getOrElse(2) { "模型未提供说明" }
                        ModSuggestPopup(
                            this, parts[0], parts[1], description
                        ) {
                            append("系统", "已记录要下载「${parts[0]}」，请在模组界面确认后下载。")
                        }.open()
                    }
                }
                trimmed.startsWith("SET:") -> {
                    val body = trimmed.removePrefix("SET:").trim()
                    val key = body.substringBefore("=").trim()
                    val value = body.substringAfter("=", "").trim().toBooleanStrictOrNull() ?: continue
                    val entry = com.unciv.logic.llm.SettingsChanges.supported()[key] ?: continue
                    val stateText = if (value) "开启" else "关闭"
                    confirm(
                        "AI 建议把「${entry.first}」改为$stateText" +
                            "（当前为${if (entry.second()) "开启" else "关闭"}），是否同意？"
                    ) {
                        com.unciv.logic.llm.SettingsChanges.request(key, value)
                        val change = com.unciv.logic.llm.SettingsChanges.poll()
                        if (change != null && com.unciv.logic.llm.SettingsChanges.apply(change))
                            append("系统", "已修改设置：${entry.first} → $stateText")
                    }
                }
            }
        }
    }

    private fun confirm(question: String, onAccept: () -> Unit) {
        val popup = Popup(this, Scrollability.None)
        val label = Label(question, BaseScreen.skin)
        label.wrap = true
        popup.add(label).width(0.7f * stage.width).row()
        val accept = "同意".toTextButton()
        accept.onClick { onAccept(); popup.close() }
        val decline = "拒绝".toTextButton()
        decline.onClick { popup.close() }
        val row = Table()
        row.add(accept).padRight(8f)
        row.add(decline)
        popup.add(row).row()
        popup.open()
    }

    private fun showError(error: Throwable) {
        Popup(this, Scrollability.None).apply {
            add("请求失败".toLabel(fontSize = 22)).left().pad(6f).row()
            val text = Label(describe(error), BaseScreen.skin)
            text.wrap = true
            val scroll = ScrollPane(text, BaseScreen.skin)
            scroll.setFadeScrollBars(false)
            add(scroll).size(0.8f * stage.width, 0.4f * stage.height).row()
            val copy = "复制日志".toTextButton()
            copy.onClick { com.badlogic.gdx.Gdx.app.clipboard.contents = describe(error) }
            add(copy).left().row()
            addCloseButton("关闭")
        }.open()
    }

    private fun describe(error: Throwable): String {
        val out = StringBuilder()
        var current: Throwable? = error
        while (current != null) {
            out.append(current.toString()).append("\n")
            current.stackTrace.take(40).forEach { out.append("    at ").append(it).append("\n") }
            current = current.cause
        }
        return out.toString()
    }

    private companion object {
        val MOD_AND_SETTINGS_INSTRUCTIONS = """
            如果你认为需要下载某个模组，请在回答中用独立一行写出：
            MOD: 模组名 | 仓库地址 | 一句话说明这个模组做什么
            如果建议修改设置，用独立一行写出：
            SET: 设置键名 = true/false
            可用的设置键：autoAssignCityProduction, autoBuildingRoads,
            automatedUnitsCanUpgrade, automatedUnitsChoosePromotions,
            citiesAutoBombardAtEndOfTurn, automatedUnitsMoveOnTurnStart
            不要自行假设这些操作已被执行。
        """.trimIndent()
    }
}
