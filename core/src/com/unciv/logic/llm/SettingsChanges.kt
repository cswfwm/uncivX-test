package com.unciv.logic.llm

import com.unciv.UncivGame

/**
 * Settings the AI may propose changing, and only after the player approves.
 *
 * The AI never writes settings directly: it posts a [PendingChange], the world screen shows a
 * confirmation with old and new values, and only an accepted answer is applied. The whitelist
 * exists so the model cannot flip unrelated switches (graphics, audio, multiplayer identity).
 */
object SettingsChanges {

    data class ChangeRequest(
        val key: String,
        val displayName: String,
        val currentValue: Boolean,
        val requestedValue: Boolean
    )

    data class PendingChange(val request: ChangeRequest)

    private val pending = ArrayList<ChangeRequest>()

    /** Whitelisted automations, keyed by the name the model uses. */
    fun supported(): Map<String, Pair<String, () -> Boolean>> {
        val settings = UncivGame.Current.settings
        return linkedMapOf(
            "autoAssignCityProduction" to ("自动分配城市生产" to { settings.autoAssignCityProduction }),
            "autoBuildingRoads" to ("自动修建道路" to { settings.autoBuildingRoads }),
            "automatedUnitsCanUpgrade" to ("自动单位可升级" to { settings.automatedUnitsCanUpgrade }),
            "automatedUnitsChoosePromotions" to ("自动单位自动晋升" to { settings.automatedUnitsChoosePromotions }),
            "citiesAutoBombardAtEndOfTurn" to ("城市回合末自动炮击" to { settings.citiesAutoBombardAtEndOfTurn }),
            "automatedUnitsMoveOnTurnStart" to ("回合开始时自动移动单位" to { settings.automatedUnitsMoveOnTurnStart })
        )
    }

    /** Queues a change for player approval. Unknown keys are rejected. */
    fun request(key: String, value: Boolean): Boolean {
        val entry = supported()[key] ?: return false
        synchronized(pending) {
            pending.removeAll { it.key == key }
            pending.add(ChangeRequest(key, entry.first, entry.second(), value))
        }
        return true
    }

    fun poll(): ChangeRequest? = synchronized(pending) {
        if (pending.isEmpty()) null else pending.removeAt(0)
    }

    fun clear() = synchronized(pending) { pending.clear() }

    /** Applies an approved change. */
    fun apply(request: ChangeRequest): Boolean {
        val settings = UncivGame.Current.settings
        when (request.key) {
            "autoAssignCityProduction" -> settings.autoAssignCityProduction = request.requestedValue
            "autoBuildingRoads" -> settings.autoBuildingRoads = request.requestedValue
            "automatedUnitsCanUpgrade" -> settings.automatedUnitsCanUpgrade = request.requestedValue
            "automatedUnitsChoosePromotions" -> settings.automatedUnitsChoosePromotions = request.requestedValue
            "citiesAutoBombardAtEndOfTurn" -> settings.citiesAutoBombardAtEndOfTurn = request.requestedValue
            "automatedUnitsMoveOnTurnStart" -> settings.automatedUnitsMoveOnTurnStart = request.requestedValue
            else -> return false
        }
        settings.save()
        return true
    }
}
