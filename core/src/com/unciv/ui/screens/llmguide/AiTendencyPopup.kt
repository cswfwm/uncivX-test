package com.unciv.ui.screens.llmguide

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.llm.CoteRulesetLoader
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Library of AI tendency profiles (`.cote` files).
 *
 * Lists every rule set in the ruleset folder, shows what it does, and applies one on request.
 * Applying is always explicit - nothing is activated just by opening this screen.
 */
class AiTendencyPopup(
    stage: com.badlogic.gdx.scenes.scene2d.Stage,
    private val onApplied: (String) -> Unit = {}
) : Popup(stage, Scrollability.None) {

    private val listTable = Table(BaseScreen.skin)
    private val status = "".toLabel(fontSize = 15)

    init {
        add("AI 趋势".toLabel(fontSize = 24)).left().pad(6f).row()
        add("点开一份查看介绍，确认后才生效".toLabel(fontSize = 14)).left().row()

        listTable.defaults().growX().pad(3f).left()
        val scroll = ScrollPane(listTable, BaseScreen.skin)
        scroll.setFadeScrollBars(false)
        add(scroll).size(0.78f * stageToShowOn.width, 0.5f * stageOnHeight()).expand().fill().row()

        val activeName = CoteRulesetLoader.active?.name ?: "未启用规则集（使用内置规则）"
        add("当前生效：$activeName".toLabel(fontSize = 14)).left().row()
        add(status).left().row()
        addCloseButton("关闭")

        refresh()
    }

    private fun stageOnHeight(): Float = stageToShowOn.height * 0.9f

    private fun refresh() {
        listTable.clear()
        val files = CoteRulesetLoader.available()
        if (files.isEmpty()) {
            listTable.add("还没有规则集，可在设置里生成示例，或让 AI 指导生成一份".toLabel(fontSize = 16)).row()
            return
        }
        for (file in files) {
            val parsed = CoteRulesetLoader.parse(file.readString("UTF-8"))
            val ruleset = parsed.getOrNull()
            val row = Table()
            if (ruleset == null) {
                row.add("${file.name()}（格式有误）".toLabel(fontSize = 16)).expandX().left()
                listTable.add(row).growX().row()
                continue
            }
            row.add(ruleset.name.toLabel(fontSize = 17)).expandX().left()
            "查看".toTextButton().apply {
                onClick { showDetails(ruleset, file.name()) }
            }.also { row.add(it).padRight(6f) }
            "导入".toTextButton().apply {
                onClick { apply(ruleset) }
            }.also { row.add(it) }
            listTable.add(row).growX().row()

            val summary = buildString {
                append(ruleset.tendencies.let {
                    "进攻 ${it.aggression} · 扩张 ${it.expansion} · 外交 ${it.diplomacy} · " +
                        "抬价 ${it.tradeProfit} · 毁约 ${it.betrayal} · 军事 ${it.militaryFocus} · 科研 ${it.scienceFocus}"
                })
            }
            listTable.add(summary.toLabel(fontSize = 12)).padLeft(8f).row()
        }
    }

    private fun showDetails(ruleset: com.unciv.logic.llm.CoteRuleset, fileName: String) {
        val details = Table(BaseScreen.skin)
        details.pad(10f)
        details.add(ruleset.name.toLabel(fontSize = 20)).left().row()
        if (ruleset.description.isNotBlank())
            details.add(ruleset.description.toLabel(fontSize = 14)).left().row()

        details.add("倾向".toLabel(fontSize = 15)).padTop(8f).left().row()
        val t = ruleset.tendencies
        details.add(("进攻性 ${t.aggression}　扩张 ${t.expansion}　外交 ${t.diplomacy}\n" +
            "交易抬价 ${t.tradeProfit}　毁约 ${t.betrayal}\n" +
            "军事优先 ${t.militaryFocus}　科研优先 ${t.scienceFocus}").toLabel(fontSize = 14))
            .left().row()

        if (ruleset.rules.isNotEmpty()) {
            details.add("规则（${ruleset.rules.size} 条）".toLabel(fontSize = 15)).padTop(8f).left().row()
            for (rule in ruleset.rules) {
                val cond = rule.condition?.let { "（$it）" } ?: ""
                val label = Label("· ${rule.text}$cond", BaseScreen.skin)
                label.wrap = true
                label.setAlignment(Align.topLeft)
                details.add(label).width(0.7f * stageToShowOn.width).left().row()
            }
        }

        details.add("文件：$fileName".toLabel(fontSize = 12)).padTop(8f).left().row()

        val popup = Popup(stageToShowOn, Scrollability.None)
        val scroll = ScrollPane(details, BaseScreen.skin)
        scroll.setFadeScrollBars(false)
        popup.add(scroll).size(0.78f * stageToShowOn.width, 0.5f * stageToShowOn.height).row()
        "导入这份".toTextButton().apply {
            onClick {
                apply(ruleset)
                popup.close()
            }
        }.also { popup.add(it).left().row() }
        popup.addCloseButton("关闭")
        popup.open()
    }

    private fun apply(ruleset: com.unciv.logic.llm.CoteRuleset) {
        val saved = CoteRulesetLoader.save(ruleset)
        val file = saved.getOrNull()
        if (file == null) {
            status.setText("导入失败：${saved.exceptionOrNull()?.message}")
            return
        }
        CoteRulesetLoader.load(file)
            .onSuccess {
                status.setText("已导入：${it.name}，下一次 AI 操作起生效")
                onApplied(it.name)
            }
            .onFailure { status.setText("导入失败：${it.message}") }
    }
}
