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

        addHeader("AI player")

        addCheckbox("Enable AI panel", settings.llm::enabled, updateWorld = true) {
            LlmSettingsHolder.loadFromSettings()
        }

        addTextSetting("Relay endpoint", settings.llm.baseUrl) { settings.llm.baseUrl = it }
        addTextSetting("API key", settings.llm.apiKey) { settings.llm.apiKey = it }
        addTextSetting("Model", settings.llm.model) { settings.llm.model = it }

        addCheckbox("AI operates every turn", settings.llm::autoPlayEachTurn)

        addCheckbox("Reveal the whole map", settings.llm::revealMap, updateWorld = true) {
            LlmSettingsHolder.loadFromSettings()
        }

        addCheckbox("Show enemy radar", settings.llm::showEnemyRadar, updateWorld = true) {
            LlmSettingsHolder.loadFromSettings()
        }

        addCheckbox("Show unit names", settings.llm::showUnitNames, updateWorld = true) {
            LlmSettingsHolder.loadFromSettings()
        }

        addCheckbox("Ask before declaring war", settings.llm::confirmWarDeclarations)

        addPlayerIdImport()

        addWrapped {
            add("Test connection".toTextButton().apply {
                onClick { testConnection() }
            })
        }

        add(resultLabel).colspan(2).left().row()

        super.lateInitialize()
    }

    /** Label plus text field, a clipboard paste button and an explicit save button. */
    private fun addTextSetting(label: String, initial: String, save: (String) -> Unit) {
        val field = UncivTextField(label, initial)
        field.maxLength = 500
        val pasteButton = "Paste".toTextButton()
        val saveButton = "Enter".toTextButton()
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
    private fun addPlayerIdImport() {
        addHeader("Player ID")

        val idLabel = "".toLabel()
        addWrapped {
            add("Take user ID from clipboard".toTextButton().apply {
                onClick {
                    val clipboard = Gdx.app.clipboard.contents?.trim()
                    if (clipboard == null || !clipboard.isUUID()) {
                        idLabel.color = Color.RED
                        idLabel.setText("Invalid ID!")
                        return@onClick
                    }
                    ConfirmPopup(
                        stage,
                        "Doing this will reset your current user ID to the clipboard contents - are you sure?",
                        "Take user ID from clipboard"
                    ) {
                        settings.multiplayer.setUserId(clipboard)
                        idLabel.color = Color.WHITE
                        idLabel.setText("ID successfully set!")
                    }.open(true)
                }
            })
        }
        add(idLabel).colspan(2).left().row()
    }

    private fun testConnection() {
        resultLabel.setText("Testing...")
        val snapshot = settings.llm
        Concurrency.run("LlmTest") {
            val result = LlmClient.complete(snapshot, "Reply with the single word OK.", "ping")
            launchOnGLThread {
                resultLabel.setText(if (result.isSuccess) "OK" else "Failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
