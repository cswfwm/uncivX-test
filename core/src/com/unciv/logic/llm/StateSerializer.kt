package com.unciv.logic.llm

import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit

/**
 * Renders the current game into a compact text block for the LLM.
 *
 * The map section walks the whole tile map instead of the viewing civ's explored tiles: the AI is
 * meant to reason over the full board. Nothing here writes to the game state.
 */
object StateSerializer {

    fun describe(gameInfo: GameInfo, civ: Civilization): String = buildString {
        appendLine("=== GAME ===")
        appendLine("turn=${gameInfo.turns} civ=${civ.civName} era=${civ.tech.era.name}")
        val stats = civ.stats.statsForNextTurn
        appendLine("gold=${civ.gold} science=${stats.science} culture=${stats.culture} happiness=${civ.getHappiness()}")

        appendLine()
        appendLine("=== RESEARCH ===")
        appendLine("current=${civ.tech.currentTechnologyName() ?: "none"}")
        appendLine("researchable=${researchableTechs(civ).joinToString(", ")}")

        appendLine()
        appendLine("=== CITIES (${civ.cities.size}) ===")
        for (city in civ.cities) appendLine(describeCity(city))

        appendLine()
        appendLine("=== UNITS (${civ.units.getCivUnitsSize()}) ===")
        for (unit in civ.units.getCivUnits()) appendLine(describeUnit(unit))

        appendLine()
        appendLine("=== DIPLOMACY ===")
        appendLine(describeDiplomacy(civ))

        appendLine()
        appendLine("=== MAP (full board, including unexplored) ===")
        appendLine(describeMap(gameInfo))
    }

    private fun researchableTechs(civ: Civilization): List<String> =
        civ.gameInfo.ruleset.technologies.values
            .filter { civ.tech.canBeResearched(it.name) }
            .map { it.name }

    private fun describeCity(city: City): String {
        val stats = city.cityStats.currentCityStats
        val construction = city.cityConstructions.currentConstructionName().ifEmpty { "none" }
        return "- ${city.name} pop=${city.population.population} hp=${city.health} " +
            "pos=${city.location.x},${city.location.y} building=$construction " +
            "food=${stats.food} prod=${stats.production} gold=${stats.gold} sci=${stats.science} cult=${stats.culture}"
    }

    private fun describeUnit(unit: MapUnit): String {
        val position = if (unit.hasTile()) "${unit.currentTile.position.x},${unit.currentTile.position.y}" else "?"
        return "- ${unit.name} pos=$position hp=${unit.health}/${unit.getMaxHealth()} " +
            "move=${unit.currentMovement}/${unit.getMaxMovement()}"
    }

    private fun describeDiplomacy(civ: Civilization): String = buildString {
        for (otherCiv in civ.getKnownCivs()) {
            if (otherCiv == civ) continue
            val atWar = if (civ.isAtWarWith(otherCiv)) "war" else "peace"
            append("- ${otherCiv.civName} $atWar")
            if (otherCiv.isCityState)
                append(" influence=${civ.getDiplomacyManager(otherCiv)?.getInfluence() ?: 0f}")
            appendLine()
        }
    }

    /** One text row per map latitude, with a coarse terrain glyph per tile. */
    private fun describeMap(gameInfo: GameInfo): String = buildString {
        val rows = gameInfo.tileMap.values.groupBy { it.position.y }
        for ((latitude, tiles) in rows.toSortedMap()) {
            val line = StringBuilder()
            for (tile in tiles.sortedBy { it.position.x }) {
                line.append(tileSymbol(tile.baseTerrain))
                if (tile.getOwner() != null) line.append('*')
            }
            appendLine("y=$latitude $line")
        }
    }

    private fun tileSymbol(terrain: String): Char = when {
        terrain.contains("Ocean", true) -> '~'
        terrain.contains("Coast", true) -> '-'
        terrain.contains("Mountain", true) -> '^'
        terrain.contains("Hill", true) -> 'n'
        terrain.contains("Desert", true) -> 'd'
        terrain.contains("Tundra", true) -> 't'
        terrain.contains("Snow", true) -> 's'
        else -> ','
    }
}
