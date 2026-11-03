package com.unciv.logic.llm

import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.translations.tr

/**
 * Renders the current game into a compact text block for the LLM.
 *
 * The map section walks the whole tile map instead of the viewing civ's explored tiles: the AI is
 * meant to reason over the full board. Nothing here writes to the game state.
 */
object StateSerializer {

    /**
     * Short form for direction-mode turns: economy, research and city list only.
     *
     * The full board is what makes prompts expensive and slow, and strategy selection does not
     * need it - the stock automation still handles placement.
     */
    fun describeBriefly(gameInfo: GameInfo, civ: Civilization): String = buildString {
        appendLine("=== GAME ===")
        appendLine("turn=${gameInfo.turns} civ=${civ.civName}")
        val stats = civ.stats.statsForNextTurn
        appendLine("gold=${civ.gold} science=${stats.science} happiness=${civ.getHappiness()}")
        appendLine("城市数=${civ.cities.size} 单位数=${civ.units.getCivUnitsSize()}")

        appendLine()
        appendLine("=== RESEARCH ===")
        val current = civ.tech.currentTechnologyName()
        if (current == null) appendLine("正在研发：无（需要选择）")
        else appendLine("正在研发：${current.tr()}")
        appendLine("可研究：${researchableTechs(civ).joinToString(", ") { it.tr() }}")

        appendLine()
        appendLine("=== CITIES ===")
        for (city in civ.cities) appendLine("- ${city.name} 人口=${city.population.population}")

        appendLine()
        appendLine("=== DIPLOMACY ===")
        appendLine(describeDiplomacy(civ))
    }

    fun describe(gameInfo: GameInfo, civ: Civilization): String = buildString {
        appendLine("=== GAME ===")
        appendLine("turn=${gameInfo.turns} civ=${civ.civName} era=${civ.tech.era.name}")
        val stats = civ.stats.statsForNextTurn
        appendLine("gold=${civ.gold} science=${stats.science} culture=${stats.culture} happiness=${civ.getHappiness()}")

        appendLine()
        appendLine("=== RESEARCH ===")
        val currentTech = civ.tech.currentTechnologyName()
        if (currentTech == null) {
            appendLine("正在研发：无（需要选择）")
        } else {
            val invested = civ.tech.researchOfTech(currentTech)
            val cost = civ.tech.costOfTech(currentTech)
            val remaining = (cost - invested).coerceAtLeast(0)
            appendLine("正在研发：${currentTech.tr()}（已投入 $invested / $cost，剩余约 $remaining 科研点，" +
                "预计 ${civ.tech.turnsToTech(currentTech)} 回合）")
        }
        appendLine("后续队列：${civ.tech.techsToResearch.drop(1).joinToString(", ") { it.tr() }.ifEmpty { "空" }}")
        appendLine("可研究：${researchableTechs(civ).joinToString(", ") { it.tr() }}")

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
        if (LlmSettingsHolder.isRevealMapEnabled())
            appendLine("你拥有全图视野：以下内容包含未探索区域与所有可见文明的位置。")
        else
            appendLine("你只能看到已探索区域，未探索部分的信息不可用。")
        appendLine(describeMap(gameInfo, civ))
    }

    private fun researchableTechs(civ: Civilization): List<String> =
        civ.gameInfo.ruleset.technologies.values
            .filter { civ.tech.canBeResearched(it.name) }
            .map { it.name }

    private fun describeCity(city: City): String {
        val stats = city.cityStats.currentCityStats
        val construction = city.cityConstructions.currentConstructionName().ifEmpty { "（空闲，必须指定）" }
        val buildable = city.cityConstructions.getConstructableUnits()
            .map { it.name.tr() }
            .toList()
        return "- ${city.name} 人口=${city.population.population} 城防=${city.health} " +
            "坐标=${city.location.x},${city.location.y} 正在建造：$construction " +
            "每回合 食物=${stats.food} 生产=${stats.production} 金币=${stats.gold} " +
            "科研=${stats.science} 文化=${stats.culture}\n" +
            "  可生产单位：${if (buildable.isEmpty()) "无可造单位" else buildable.joinToString("、")}"
    }

    private fun describeUnit(unit: MapUnit): String {
        val position = if (unit.hasTile()) "${unit.currentTile.position.x},${unit.currentTile.position.y}" else "?"
        return "- id=${unit.id} ${unit.name.tr()} pos=$position hp=${unit.health}/${unit.getMaxHealth()} " +
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
    /**
     * Terrain grid plus a legend of everything strategically relevant.
     *
     * The model can see the whole board here, so enemy positions, cities and resources are all
     * listed: without them "full map" would just be a picture of terrain.
     */
    private fun describeMap(gameInfo: GameInfo, civ: Civilization): String = buildString {
        val rows = gameInfo.tileMap.values.groupBy { it.position.y }
        for ((latitude, tiles) in rows.toSortedMap()) {
            val line = StringBuilder()
            for (tile in tiles.sortedBy { it.position.x }) {
                line.append(tileSymbol(tile.baseTerrain))
                if (tile.getOwner() != null) line.append('*')
            }
            appendLine("y=$latitude $line")
        }

        appendLine()
        appendLine("我方单位：")
        for (unit in civ.units.getCivUnits()) {
            if (!unit.hasTile()) continue
            appendLine("- ${unit.name.tr()} id=${unit.id} (${unit.currentTile.position.x},${unit.currentTile.position.y})")
        }

        appendLine()
        val reveal = LlmSettingsHolder.isRevealMapEnabled()
        appendLine(if (reveal) "敌方与中立目标（全图可见）：" else "敌方与中立目标（仅已探索）：")
        fun visible(tile: com.unciv.logic.map.tile.Tile) = reveal || tile.isExplored(civ)
        val enemyCities = gameInfo.civilizations
            .filter { it != civ && !it.isDefeated() }
            .flatMap { it.cities }
            .filter { city -> city.getCenterTileOrNull()?.let { visible(it) } == true }
        for (city in enemyCities) {
            appendLine("- ${city.civ.civName} 城市 ${city.name.tr()} " +
                "(${city.location.x},${city.location.y}) 城防 ${city.health}")
        }
        // Every enemy unit carries its coordinates so the model can reason about positions, and
        // units near our cities are flagged inline instead of moving to a separate list that the
        // model could read as "not relevant".
        for (unit in gameInfo.civilizations.filter { it != civ }.flatMap { it.units.getCivUnits() }) {
            if (!unit.hasTile() || !visible(unit.currentTile)) continue
            val owner = unit.civ.civName
            val x = unit.currentTile.position.x
            val y = unit.currentTile.position.y
            val nearest = civ.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(unit.currentTile) }
            val distance = nearest?.getCenterTile()?.aerialDistanceTo(unit.currentTile)
            val note = if (distance == null) "" else "，距 ${nearest!!.name.tr()} $distance 格"
            val alert = if (distance != null && distance <= 3 && unit.isMilitary()) " ⚠逼近我方城市" else ""
            appendLine("- $owner ${unit.name.tr()} ($x,$y)$note$alert")
        }

        appendLine()
        appendLine("资源点：")
        val resources = gameInfo.tileMap.values.filter { it.resource != null && visible(it) }
        for (tile in resources.take(60)) {
            val owner = tile.getOwner()?.civName ?: "无主"
            appendLine("- ${tile.resource} (${tile.position.x},${tile.position.y}) 归属 $owner")
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
