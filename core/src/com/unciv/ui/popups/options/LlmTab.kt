package com.unciv.ui.popups.options

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.unciv.logic.llm.LlmClient
import com.unciv.logic.llm.LlmSettingsHolder
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.utils.isUUID
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/**
 * Options page for the LLM player proxy: relay endpoint, key, model, and the behaviour switches
 * that are also mirrored on the in-game panel.
 */
internal class LlmTab(
    optionsPopup: OptionsPopup
) : OptionsPopupTab(optionsPopup) {

    private val resultLabel = "".toLabel()

    override fun lateInitialize() {
        top()

        addHeader("AI 托管")

        addCheckbox("启用 AI 悬浮球", settings.llm::enabled, updateWorld = true) {
            LlmSettingsHolder.loadFromSettings()
        }

        addTextSetting("中转站地址", settings.llm.baseUrl) { settings.llm.baseUrl = it }
        addTextSetting("API 密钥", settings.llm.apiKey) { settings.llm.apiKey = it }
        addTextSetting("模型名称", settings.llm.model) { settings.llm.model = it }

        addCheckbox("每回合自动操作", settings.llm::autoPlayEachTurn)

        addCheckbox("联机时自动点「我完成了」", settings.llm::autoFinishTurn)

        addCheckbox("显示全图", settings.llm::revealMap, updateWorld = true) {
            LlmSettingsHolder.loadFromSettings()
        }

        addCheckbox("显示敌方雷达", settings.llm::showEnemyRadar, updateWorld = true) {
            LlmSettingsHolder.loadFromSettings()
        }

        addCheckbox("显示单位名称", settings.llm::showUnitNames, updateWorld = true) {
            LlmSettingsHolder.loadFromSettings()
        }

        addCheckbox("允许联网搜索", settings.llm::allowWebSearch)

        addCheckbox("宣战前询问", settings.llm::confirmWarDeclarations)

        addRulesetSection()

        addPlayerIdImport()

        addWrapped {
            add("测试连接".toTextButton().apply {
                onClick { testConnection() }
            })
        }

        add(resultLabel).colspan(2).left().row()
        add(com.unciv.logic.llm.LlmUsage.summary().toLabel(fontSize = 16)).colspan(2).left().row()

        super.lateInitialize()
    }

    /** Label plus text field, a clipboard paste button and an explicit save button. */
    private fun addTextSetting(label: String, initial: String, save: (String) -> Unit) {
        val field = UncivTextField(label, initial)
        field.maxLength = 500
        val pasteButton = "粘贴".toTextButton()
        val saveButton = "保存".toTextButton()
        saveButton.disable()
        field.onChange { if (field.text != initial) saveButton.enable() else saveButton.disable() }
        pasteButton.onClick {
            val clipboard = Gdx.app.clipboard.contents?.trim()
            if (clipboard.isNullOrEmpty()) return@onClick
            field.setText(clipboard)
            saveButton.enable()
        }
        saveButton.onClick {
            save(field.text.trim())
            saveButton.disable()
        }
        addWrapped {
            add(label.toLabel())
            add(field).growX().padRight(5f)
            add(pasteButton).padRight(5f)
            add(saveButton)
        }
    }

    /** Import an existing multiplayer player ID from the clipboard, reusing the game's own flow. */
    /** Load a player-written `.cote` rule set that steers the AI's tendencies. */
    private fun addRulesetSection() {
        addHeader("AI 规则集")

        val label = "".toLabel(fontSize = 15)
        val activeName = com.unciv.logic.llm.CoteRulesetLoader.active?.name ?: "未加载（使用内置规则）"
        label.setText("当前：$activeName")

        addWrapped {
            add("重新读取规则集".toTextButton().apply {
                onClick {
                    val files = com.unciv.logic.llm.CoteRulesetLoader.available()
                    if (files.isEmpty()) {
                        label.setText("规则集目录为空，可先点“生成示例文件”")
                        return@onClick
                    }
                    val latest = files.maxByOrNull { it.lastModified() }!!
                    com.unciv.logic.llm.CoteRulesetLoader.load(latest)
                        .onSuccess { label.setText("已加载：${it.name}") }
                        .onFailure { label.setText("加载失败：${it.message}") }
                }
            }).padRight(6f)

            add("生成示例文件".toTextButton().apply {
                onClick {
                    val file = com.unciv.logic.llm.CoteRulesetLoader.writeExample()
                    label.setText("已生成：${file.name()}，可编辑后重新读取")
                }
            }).padRight(6f)

            add("停用规则集".toTextButton().apply {
                onClick {
                    com.unciv.logic.llm.CoteRulesetLoader.clear()
                    label.setText("已停用，使用内置规则")
                }
            })
        }

        add(label).colspan(2).left().row()
        add("规则集放在游戏目录的 rulesets 文件夹，后缀 .cote，内容为 JSON".toLabel(fontSize = 13))
            .colspan(2).left().row()
    }

    private fun addPlayerIdImport() {
        addHeader("玩家 ID")

        val idLabel = "".toLabel()
        addWrapped {
            add("从剪贴板导入玩家 ID".toTextButton().apply {
                onClick {
                    val clipboard = Gdx.app.clipboard.contents?.trim()
                    if (clipboard == null || !clipboard.isUUID()) {
                        idLabel.color = Color.RED
                        idLabel.setText("ID 格式无效！")
                        return@onClick
                    }
                    ConfirmPopup(
                        stage,
                        "这会把你的玩家 ID 重置为剪贴板中的内容，确定继续吗？",
                        "确认导入"
                    ) {
                        settings.multiplayer.setUserId(clipboard)
                        idLabel.color = Color.WHITE
                        idLabel.setText("玩家 ID 已设置！")
                    }.open(true)
                }
            })
        }
        add(idLabel).colspan(2).left().row()
    }

    private fun testConnection() {
        resultLabel.setText("正在测试…")
        val snapshot = settings.llm
        Concurrency.run("LlmTest") {
            val result = LlmClient.complete(snapshot, "Reply with the single word OK.", "ping")
            launchOnGLThread {
                resultLabel.setText(if (result.isSuccess) "连接成功" else "失败：${result.exceptionOrNull()?.message}")
            }
        }
    }
}
