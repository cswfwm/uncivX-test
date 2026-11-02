package com.unciv.logic.llm

import com.unciv.UncivGame

/**
 * Read access to [LlmSettings] for render-time code.
 *
 * The view layer asks this on every tile and every frame, so the hot path only reads volatile
 * booleans. The reveal switch is deliberately independent of [com.unciv.utils.DebugUtils.VISIBLE_MAP],
 * which also feeds AI decisions such as threat assessment.
 */
object LlmSettingsHolder {
    @Volatile
    private var revealMap = false

    @Volatile
    private var showEnemyRadar = false

    @Volatile
    private var showUnitNames = false

    @Volatile
    var current: LlmSettings = LlmSettings()

    fun isRevealMapEnabled(): Boolean = revealMap

    fun isEnemyRadarEnabled(): Boolean = showEnemyRadar

    fun isUnitNamesEnabled(): Boolean = showUnitNames

    fun setRevealMap(enabled: Boolean) {
        current.revealMap = enabled
        revealMap = enabled
    }

    fun setEnemyRadar(enabled: Boolean) {
        current.showEnemyRadar = enabled
        showEnemyRadar = enabled
    }

    fun setUnitNames(enabled: Boolean) {
        current.showUnitNames = enabled
        showUnitNames = enabled
    }

    /** Called once after settings are loaded so render code sees the saved value immediately. */
    fun loadFromSettings() {
        if (!UncivGame.isCurrentInitialized()) return
        current = UncivGame.Current.settings.llm
        revealMap = current.revealMap
        showEnemyRadar = current.showEnemyRadar
        showUnitNames = current.showUnitNames
    }
}
