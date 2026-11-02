package com.unciv.ui.screens.worldscreen.view

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.utils.Align
import com.unciv.logic.llm.EnemyForces
import com.unciv.logic.llm.LlmDecisionGranularity
import com.unciv.logic.llm.LlmSettingsHolder
import com.unciv.logic.llm.LlmTurnPlanner
import com.unciv.logic.llm.WarDeclarations
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/**
 * Floating AI control: a small draggable orb when collapsed, a narrow panel when expanded.
 *
 * The panel never ends the turn nor uploads to a multiplayer server: it only runs the AI for the
 * turn currently held locally, so the player keeps control of passing the turn.
 */
class LlmPanel(private val worldScreen: WorldScreen) : Table() {

    private companion object {
        const val PANEL_WIDTH = 190f
        const val ORB_SIZE = 52f
        const val MARGIN = 10f
        const val LOG_HEIGHT = 70f
    }

    private val settings = worldScreen.game.settings.llm

    private val statusLabel = Label("", BaseScreen.skin)
    private val logLabel = Label("", BaseScreen.skin)
    private val enemyLabel = Label("", BaseScreen.skin)
    private var expanded = false
    private var busy = false

    /** Last failure detail, shown in [ErrorReportPopup] when the player asks for the log. */
    private var lastError: String? = null

    /** Position of the collapsed orb; kept across rebuilds so dragging sticks. */
    private val orbPosition = Vector2(Float.NaN, Float.NaN)

    init {
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/LlmPanel", tintColor = ImageGetter.CHARCOAL)
        pad(8f)
        defaults().left().padBottom(4f)
        build()
        refreshPosition()
    }

    fun refreshPosition() {
        val stage = worldScreen.stage
        if (expanded) {
            setPosition(stage.width - width - MARGIN, MARGIN)
            return
        }
        if (orbPosition.x.isNaN()) {
            orbPosition.set(stage.width - ORB_SIZE - MARGIN, stage.height - ORB_SIZE - MARGIN * 8)
        }
        orbPosition.x = orbPosition.x.coerceIn(0f, stage.width - width)
        orbPosition.y = orbPosition.y.coerceIn(0f, stage.height - height)
        setPosition(orbPosition.x, orbPosition.y)
    }

    private fun build() {
        clear()

        if (!expanded) {
            buildOrb()
            return
        }

        buildHeader()
        statusLabel.setText(statusText())
        add(statusLabel).growX().row()

        val enemyStrengths = currentCivStrengths()
        enemyLabel.setText(
            if (enemyStrengths.isEmpty()) "敌方兵力：无交战"
            else "敌方兵力：军事 ${enemyStrengths.sumOf { it.military }}" +
                "，平民 ${enemyStrengths.sumOf { it.civilian }}")
        add(enemyLabel).growX().row()

        "AI 问答".toTextButton().apply {
            onClick { LlmChatPopup(worldScreen, settings).open() }
        }.also { add(it).growX().row() }

        "音乐播放器".toTextButton().apply {
            onClick { MusicPlayerPopup(worldScreen).open() }
        }.also { add(it).growX().row() }

        "AI 操作本回合".toTextButton().apply {
            if (busy) disable()
            onClick { runAiTurn() }
        }.also { add(it).growX().row() }

        if (lastError != null) {
            "查看日志".toTextButton().apply {
                onClick { ErrorReportPopup(worldScreen, "AI 操作失败", lastError!!).open() }
            }.also { add(it).growX().row() }
        }

        (if (settings.granularityOrDefault() == LlmDecisionGranularity.Direction)
            "粒度：定方向" else "粒度：逐项").toTextButton().apply {
            onClick {
                settings.granularity =
                    if (settings.granularityOrDefault() == LlmDecisionGranularity.Direction)
                        LlmDecisionGranularity.PerItem.name
                    else
                        LlmDecisionGranularity.Direction.name
                rebuild()
            }
        }.also { add(it).growX().row() }

        toggleRow("全图视野", settings.revealMap) {
            LlmSettingsHolder.setRevealMap(it)
        }
        toggleRow("敌方雷达", settings.showEnemyRadar) {
            LlmSettingsHolder.setEnemyRadar(it)
        }
        toggleRow("单位名称", settings.showUnitNames) {
            LlmSettingsHolder.setUnitNames(it)
        }

        logLabel.setAlignment(Align.topLeft)
        logLabel.wrap = true
        val logScroll = ScrollPane(logLabel, BaseScreen.skin)
        logScroll.setFadeScrollBars(false)
        add(logScroll).growX().height(LOG_HEIGHT).row()

        pack()
        width = PANEL_WIDTH
    }

    /** Diplomacy is not always ready while the world screen is still being assembled. */
    private fun currentCivStrengths(): List<EnemyForces.Strength> = try {
        EnemyForces.of(worldScreen.selectedGameView.civView.getCiv())
    } catch (ex: Exception) {
        emptyList()
    }

    private fun buildOrb() {
        val orbGroup = Group()
        orbGroup.setSize(ORB_SIZE, ORB_SIZE)

        val circle = ImageGetter.getCircle(Color(0.12f, 0.12f, 0.12f, 0.92f), ORB_SIZE)
        circle.setPosition(0f, 0f)
        orbGroup.addActor(circle)

        val label = Label("AI", BaseScreen.skin).apply {
            color = Color.GOLD
            setAlignment(Align.center)
            setSize(ORB_SIZE, ORB_SIZE)
        }
        orbGroup.addActor(label)

        // Swallow touches so a tap near the orb cannot reach the map underneath.
        orbGroup.touchable = Touchable.enabled
        orbGroup.onClick {
            expanded = true
            rebuild()
        }

        add(orbGroup).size(ORB_SIZE).row()
        pack()
        width = ORB_SIZE
        height = ORB_SIZE
        installDrag(orbGroup)
    }

    private fun buildHeader() {
        val header = Table()
        header.add(Label("AI", BaseScreen.skin).apply { color = Color.GOLD }).padRight(6f)
        "\u25B6".toTextButton().apply {
            onClick {
                expanded = false
                rebuild()
            }
        }.also { header.add(it).right().expandX() }
        add(header).growX().row()
    }

    private fun toggleRow(label: String, current: Boolean, apply: (Boolean) -> Unit) {
        (if (current) "$label：开" else "$label：关").toTextButton().apply {
            onClick {
                apply(!current)
                worldScreen.shouldUpdate = true
                rebuild()
            }
        }.also { add(it).growX().row() }
    }

    private fun rebuild() {
        build()
        refreshPosition()
    }

    /** Lets the collapsed orb be dragged anywhere on screen; a plain tap still expands it. */
    private fun installDrag(actor: com.badlogic.gdx.scenes.scene2d.Actor) {
        actor.addListener(object : DragListener() {
            private var startX = 0f
            private var startY = 0f

            init {
                // A short motion should still count as a tap, not a drag.
                tapSquareSize = 40f
            }
            override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                startX = orbPosition.x
                startY = orbPosition.y
            }

            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                val dx = x - getTouchDownX()
                val dy = y - getTouchDownY()
                val stage = worldScreen.stage
                orbPosition.x = (startX + dx).coerceIn(0f, stage.width - width)
                orbPosition.y = (startY + dy).coerceIn(0f, stage.height - height)
                setPosition(orbPosition.x, orbPosition.y)
            }
        })
    }

    /** Runs the AI for the current turn if the player enabled automatic operation. Called from
     *  WorldScreen.update, which is always on the GL thread. */
    fun maybeRunAutomaticTurn() {
        if (!settings.autoPlayEachTurn || busy || !settings.isConfigured()) return
        if (!worldScreen.isPlayersTurn || worldScreen.hasOpenPopups()) return
        runAiTurn()
    }

    /** Asks the player about wars the AI wanted to start. Called from WorldScreen.update. */
    fun askPendingWarDeclarations() {
        if (worldScreen.hasOpenPopups()) return
        if (!worldScreen.isPlayersTurn) return
        val target = WarDeclarations.poll() ?: return
        val civ = worldScreen.selectedGameView.civView.getCiv()
        ConfirmPopup(
            worldScreen,
            "AI 建议对 [${target.civName}] 宣战，是否同意？",
            "宣战",
            isConfirmPositive = false
        ) {
            civ.getDiplomacyManager(target)?.declareWar()
            worldScreen.shouldUpdate = true
        }.open(true)
    }

    private fun statusText(): String = when {
        !settings.isConfigured() -> "未配置接口"
        busy -> "运行中…"
        else -> "已连接 ${settings.model}"
    }

    private fun runAiTurn() {
        if (busy || !settings.isConfigured()) return
        busy = true
        rebuild()
        val civ = worldScreen.selectedGameView.civView.getCiv()
        Concurrency.run("LlmTurn") {
            val outcome = LlmTurnPlanner.planTurn(civ, settings)
            launchOnGLThread {
                busy = false
                if (outcome.fallbackUsed) {
                    lastError = outcome.error ?: "未知错误"
                    logLabel.setText("回退内置 AI，点“查看日志”看原因")
                    ErrorReportPopup(worldScreen, "AI 操作失败", lastError!!).open()
                } else {
                    lastError = null
                    val reasoning = outcome.reasoning
                    logLabel.setText(
                        if (reasoning != null) "思路：$reasoning\n" + outcome.applied.take(4).joinToString("\n")
                        else outcome.applied.take(6).joinToString("\n"))
                }
                rebuild()
                worldScreen.shouldUpdate = true
            }
        }
    }
}
