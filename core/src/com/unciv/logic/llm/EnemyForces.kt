package com.unciv.logic.llm

import com.unciv.logic.civilization.Civilization

/**
 * Counts the units of civilizations the player is at war with.
 *
 * Uses the real unit list, not the view layer, so the count is the same whether or not the AI
 * radar or full-map view is enabled.
 */
object EnemyForces {

    data class Strength(val civName: String, val military: Int, val civilian: Int) {
        val total get() = military + civilian
    }

    fun of(civ: Civilization): List<Strength> =
        civ.getKnownCivs()
            .filter { it != civ && !it.isDefeated() && civ.isAtWarWith(it) }
            .map { enemy ->
                val units = enemy.units.getCivUnits().toList()
                Strength(
                    civName = enemy.civName,
                    military = units.count { it.isMilitary() },
                    civilian = units.count { !it.isMilitary() }
                )
            }
            .sortedByDescending { it.total }
            .toList()

    fun summary(strengths: List<Strength>): String {
        if (strengths.isEmpty()) return "当前没有交战中的文明"
        val totalMilitary = strengths.sumOf { it.military }
        val totalCivilian = strengths.sumOf { it.civilian }
        return buildString {
            append("敌方兵力：军事 $totalMilitary，平民 $totalCivilian")
            for (strength in strengths)
                append("\n- ${strength.civName}：军事 ${strength.military}，平民 ${strength.civilian}")
        }
    }
}
