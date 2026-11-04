package com.unciv.logic.llm

import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.utils.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns an LLM answer into game actions.
 *
 * Both granularities end in a full [NextTurnAutomation.automateCivMoves] pass, so units and cities
 * the LLM did not name (or named invalidly) still get sane orders and the turn can always be
 * finished. The LLM answer only overrides the parts it successfully specified.
 */
object LlmTurnPlanner {

    /** Tolerates the small deviations relays and models introduce. */
    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowSpecialFloatingPointValues = true
    }

    private const val SYSTEM_PROMPT =
        "You are the player of a Civilization V style 4X game (Unciv). " +
        "Answer with a single JSON object and no prose, no markdown fences. " +
        "Allowed fields: " +
        "\"research\": technology name to research next; " +
        "\"policy\": social policy or branch name to adopt; " +
        "\"cities\": array of {\"name\": city name, \"build\": construction name}; " +
        "\"units\": array of {\"id\": unit id from the state, \"move\": [x,y]} for a destination tile; " +
        "\"reasoning\": one short sentence. " +
        "\"settings\": array of {\"key\": whitelisted key, \"value\": true/false} to propose settings changes; " +
        "The game state uses Chinese names; reuse those exact names in your answer. " +
        "Every city can build any unit listed under its 可生产单位 - production per turn does not " +
        "block construction, it only changes how many turns it takes. Never claim you cannot build " +
        "something while it appears in that list. " +
        "You see the entire map including unexplored tiles: use the listed enemy units, cities and " +
        "resources. Move units toward the closest useful target, defend your cities, and send " +
        "settlers to unclaimed resources rather than wandering. " +
        "Write \"reasoning\" in Simplified Chinese. " +
        "Never assume a settings change took effect: every entry is shown to the player for approval. " +
        "Allowed settings keys: " + "autoAssignCityProduction, autoBuildingRoads, automatedUnitsCanUpgrade, " +
        "automatedUnitsChoosePromotions, citiesAutoBombardAtEndOfTurn, automatedUnitsMoveOnTurnStart."

    data class Outcome(
        val applied: List<String>,
        val fallbackUsed: Boolean,
        val error: String? = null,
        /** The model's own one-sentence explanation for this turn, in Chinese. */
        val reasoning: String? = null
    )

    suspend fun planTurn(
        civ: Civilization,
        settings: LlmSettings,
        onLog: (String) -> Unit = {}
    ): Outcome {
        val granularity = settings.granularityOrDefault()
        // Direction mode only picks research/policy, so skip the full board dump.
        val state = if (granularity == LlmDecisionGranularity.Direction)
            StateSerializer.describeBriefly(civ.gameInfo, civ)
        else
            StateSerializer.describe(civ.gameInfo, civ)
        val warPolicy = if (settings.confirmWarDeclarations)
            "Wars are proposed to the player for approval, never declared silently."
        else
            "Wars may be declared directly."
        val advice = StrategyGuide.adviceFor(civ)
        // Direction mode answers with a couple of short fields; capping output keeps the turn
        // responsive instead of waiting for the model to fill a long budget.
        val tokenBudget = if (granularity == LlmDecisionGranularity.Direction) 320 else 1400
        val answer = LlmClient.complete(
            settings, SYSTEM_PROMPT, buildPrompt(granularity, state, warPolicy, advice), tokenBudget)

        if (answer.isFailure) {
            val message = answer.exceptionOrNull()?.message ?: "unknown error"
            onLog("LLM request failed: $message - using stock automation")
            return fallback(civ, message)
        }

        val plan = parsePlan(answer.getOrNull()!!)
        if (plan == null) {
            val raw = answer.getOrNull()?.take(800) ?: ""
            onLog("模型返回的不是有效 JSON")
            return fallback(civ, "模型返回的不是有效 JSON。原始内容：\n$raw")
        }

        val applied = mutableListOf<String>()
        try {
            applyResearch(civ, plan, applied)
            applyPolicy(civ, plan, applied)
            proposeSettings(plan, applied)
            if (granularity == LlmDecisionGranularity.PerItem) {
                applyCities(civ, plan, applied)
                applyUnits(civ, plan, applied)
            }
        } catch (ex: Exception) {
            Log.error("LLM plan application failed: ${ex.message}")
            onLog("Applying the plan failed (${ex.message}) - using stock automation")
            return fallback(civ, ex.message)
        }

        // PerItem means the model owns every unit: whatever it did not name stays put rather than
        // being handed back to the automation it was meant to replace. Direction mode still needs
        // the stock pass to turn strategy into moves.
        val automationError = if (granularity == LlmDecisionGranularity.PerItem) {
            applyRemainingCityProduction(civ, applied)
            null
        } else {
            try {
                NextTurnAutomation.automateCivMoves(civ)
                null
            } catch (ex: Exception) {
                Log.error("Stock automation failed: ${ex.message}")
                ex.message
            }
        }
        if (granularity != LlmDecisionGranularity.PerItem)
            applied += "其余单位与城市交给内置自动化"
        return Outcome(applied, fallbackUsed = false, error = automationError,
            reasoning = reasoningOf(plan))
    }

    private fun buildPrompt(
        granularity: LlmDecisionGranularity,
        state: String,
        warPolicy: String,
        advice: List<String>
    ): String {
        val task = if (granularity == LlmDecisionGranularity.Direction)
            "Decide the strategic direction for this turn: research, policy and overall focus. " +
                "Answer immediately with \"research\", \"policy\" and a one-line \"reasoning\"; " +
                "do not deliberate, the answer is a short pick from the listed techs."
        else
            "Decide the full turn yourself. Give every city a build order. Keep at least two " +
                "military units per city, more when at war - put a military unit name in " +
                "\"build\" to produce troops, buildings only once that target is met. Give a destination for every unit you want " +
                "to move, identified by its id. Cities and units you omit fall back to a safe default."
        val strategy = buildString {
            appendLine(StrategyGuide.openingDoctrine)
            for (line in advice) appendLine("- $line")
        }
        return "$task $warPolicy\n\n" +
            "战略参考（按本局形势筛选）：\n$strategy\n" +
            "注意：情报中的「正在研发」是当前已投入的科技，不要改动它；" +
            "只有当「正在研发」为空时，才在 research 字段指定新科技。\n\n$state"
    }

    /** Cities the model left without a build order still need one, or the turn cannot progress. */
    /**
     * Cities the model did not name still need a build order.
     *
     * Picks a unit when the empire is short on defence, otherwise falls back to the stock
     * chooser so growth and infrastructure keep moving.
     */
    private fun applyRemainingCityProduction(civ: Civilization, applied: MutableList<String>) {
        val units = civ.units.getCivUnits().toList()
        val military = units.count { it.isMilitary() }
        // Target rises with the number of cities and when a war is running: a bare minimum of one
        // defender per city leaves the empire unable to answer an attack.
        val warBonus = if (civ.isAtWar()) 3 else 0
        val targetArmy = civ.cities.size * 2 + warBonus
        var deficit = (targetArmy - military).coerceAtLeast(0)

        for (city in civ.cities) {
            val current = city.cityConstructions.currentConstructionName()
            val buildingInProgress = current.isNotEmpty() &&
                city.getRuleset().buildings.containsKey(current)
            // A city already producing a unit keeps it. A city producing a building is switched
            // over while the army is below target: defenders matter more than another monument.
            if (current.isNotEmpty() && !(deficit > 0 && buildingInProgress)) continue
            try {
                // Build troops until the target is met; afterwards let growth and infrastructure
                // take over so the empire does not stall.
                val picked = if (deficit > 0) pickMilitaryUnit(city) else null
                if (picked != null) {
                    city.cityConstructions.setCurrentConstruction(picked)
                    applied += "${city.name} 建造 ${picked.tr()}"
                    deficit--
                } else {
                    city.cityConstructions.chooseNextConstruction()
                    applied += "${city.name} 自动选择生产"
                }
            } catch (ex: Exception) {
                Log.error("No production for ${city.name}: ${ex.message}")
            }
        }
    }

    /** Cheapest available military unit the city can actually build, or null. */
    private fun pickMilitaryUnit(city: com.unciv.logic.city.City): String? = try {
        city.cityConstructions.getConstructableUnits()
            .filter { it.isMilitary }
            .filter { unit -> city.cityConstructions.canAddToQueue(unit) }
            .minByOrNull { it.cost }
            ?.name
    } catch (ex: Exception) {
        Log.debug("No military option for ${city.name}: ${ex.message}")
        null
    }

    private fun reasoningOf(plan: JsonObject): String? =
        plan["reasoning"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    /**
     * Extracts the plan object from a model answer.
     *
     * Models routinely wrap JSON in prose or fences, add trailing commas, or use single quotes.
     * Rather than rejecting those, locate the outermost braces and repair the usual mistakes.
     */
    private fun parsePlan(raw: String): JsonObject? {
        val candidates = buildList {
            add(raw)
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start in 0 until end) add(raw.substring(start, end + 1))
        }
        for (candidate in candidates) {
            val repaired = candidate
                .replace(Regex("```(json)?"), "")
                .replace(Regex(",\\s*([}\\]])"), "$1")
                .trim()
            // Some relays prepend reasoning tags or an "assistant:" label.
            val withoutTags = Regex("(?s)^[^{]*").replace(repaired, "")
            for (text in listOf(repaired, withoutTags)) {
                try {
                    val element = lenientJson.parseToJsonElement(text)
                    if (element is JsonObject) return element
                } catch (_: Exception) {
                    // try the next form
                }
            }
        }
        Log.error("Could not parse LLM plan; raw answer: ${raw.take(400)}")
        return null
    }

    private fun applyResearch(civ: Civilization, plan: JsonObject, applied: MutableList<String>) {
        // An existing research choice owns the slot: changing it throws away every science point
        // invested so far, and the model has no reason to do that.
        val inProgress = civ.tech.currentTechnologyName() ?: civ.tech.techsToResearch.firstOrNull()
        if (inProgress != null) return

        val requested = plan["research"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val tech = requested?.let { resolveTechName(civ, it) }
        if (tech != null && civ.tech.canBeResearched(tech)) {
            civ.tech.techsToResearch.add(tech)
            applied += "研究 ${tech.tr()}"
            return
        }

        // The model omitted or named something unavailable. Leaving science idle wastes a whole
        // turn, so pick the cheapest researchable technology instead.
        val fallback = cheapestResearchable(civ) ?: return
        if (requested != null)
            Log.debug("Research '$requested' unusable, falling back to $fallback")
        civ.tech.techsToResearch.add(fallback)
        applied += "研究 ${fallback.tr()}（自动选择）"
    }

    /** Cheapest technology the civ can start right now. */
    private fun cheapestResearchable(civ: Civilization): String? =
        civ.gameInfo.ruleset.technologies.values
            .asSequence()
            .filter { civ.tech.canBeResearched(it.name) && it.cost > 0 }
            .minByOrNull { it.cost }
            ?.name

    /** Settings are never applied here: they are queued for the player to approve. */
    private fun proposeSettings(plan: JsonObject, applied: MutableList<String>) {
        val requested = plan["settings"] as? JsonArray ?: return
        for (entry in requested) {
            val obj = entry as? JsonObject ?: continue
            val key = obj["key"]?.jsonPrimitive?.content ?: continue
            val value = obj["value"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: continue
            if (SettingsChanges.request(key, value))
                applied += "建议修改设置 $key=$value（待确认）"
        }
    }

    /** Accepts either the ruleset key or its translated name. */
    private fun resolveTechName(civ: Civilization, requested: String): String? {
        val technologies = civ.gameInfo.ruleset.technologies
        if (requested in technologies) return requested
        return technologies.values.firstOrNull { it.name.tr() == requested }?.name
    }

    private fun applyPolicy(civ: Civilization, plan: JsonObject, applied: MutableList<String>) {
        val name = plan["policy"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        if (!civ.policies.canAdoptPolicy()) return

        if (name == null) {
            // No explicit pick this turn: adopting something is better than banking points.
            adoptAnyPolicy(civ, applied)
            return
        }
        val ruleset = civ.gameInfo.ruleset
        val policy = ruleset.policies[name]
            ?: ruleset.policyBranches[name]
            ?: ruleset.policies.values.firstOrNull { it.name.tr() == name }
            ?: ruleset.policyBranches.values.firstOrNull { it.name.tr() == name }
        if (policy != null && civ.policies.isAdoptable(policy)) {
            civ.policies.adopt(policy)
            applied += "采纳政策 $name"
            return
        }
        // Unknown or still-locked name: fall back rather than silently doing nothing.
        adoptAnyPolicy(civ, applied)
    }

    /** Adopts the cheapest available policy; used when the model omitted or misspelled one. */
    private fun adoptAnyPolicy(civ: Civilization, applied: MutableList<String>) {
        val branch = civ.policies.adoptableBranches.firstOrNull()
        if (branch != null) {
            civ.policies.adopt(branch)
            applied += "采纳政策分支 ${branch.name}"
            return
        }
        val policy = civ.policies.incompleteBranches.asSequence()
            .flatMap { it.policies.asSequence() }
            .firstOrNull { civ.policies.isAdoptable(it) } ?: return
        civ.policies.adopt(policy)
        applied += "采纳政策 ${policy.name}"
    }

    private fun applyCities(civ: Civilization, plan: JsonObject, applied: MutableList<String>) {
        val requested = plan["cities"] ?: return
        for (entry in requested.jsonArray) {
            val name = entry.jsonObject["name"]?.jsonPrimitive?.content ?: continue
            val build = entry.jsonObject["build"]?.jsonPrimitive?.content ?: continue
            val city = civ.cities.firstOrNull { it.name == name }
                ?: civ.cities.firstOrNull { it.name.tr() == name } ?: continue
            val resolved = resolveConstructionName(city, build) ?: continue
            city.cityConstructions.setCurrentConstruction(resolved)
            applied += "$name builds $build"
        }
    }

    /**
     * The model sees translated names, so a build order may arrive in Chinese. Map it back to the
     * ruleset key before touching game state.
     */
    private fun resolveConstructionName(city: City, requested: String): String? {
        val ruleset = city.getRuleset()
        if (requested in ruleset.buildings || requested in ruleset.units) return requested
        val unit = ruleset.units.values.firstOrNull { it.name.tr() == requested }
        if (unit != null) return unit.name
        val building = ruleset.buildings.values.firstOrNull { it.name.tr() == requested }
        return building?.name
    }

    private fun applyUnits(civ: Civilization, plan: JsonObject, applied: MutableList<String>) {
        val requested = plan["units"] ?: return
        for (entry in requested.jsonArray) {
            // Units are identified by their id: several units of the same type share a name.
            val id = entry.jsonObject["id"]?.jsonPrimitive?.content?.toIntOrNull()
            val coords = entry.jsonObject["move"] ?: continue
            val x = coords.jsonArray.getOrNull(0)?.jsonPrimitive?.content?.toIntOrNull() ?: continue
            val y = coords.jsonArray.getOrNull(1)?.jsonPrimitive?.content?.toIntOrNull() ?: continue
            val unit = civ.units.getCivUnits().firstOrNull { it.hasTile() && (id == null || it.id == id) }
                ?: continue
            val name = unit.name
            val tileMap = unit.currentTile.tileMap
            if (!tileMap.contains(x, y)) continue
            val target = tileMap[x, y]
            // A single unreachable destination must not abort the whole AI turn: headTowards
            // throws UnreachableDestinationException and the stock automation still needs to run
            // for every other unit.
            val reached = try {
                unit.movement.headTowards(target)
            } catch (ex: Exception) {
                Log.debug("$name could not reach $x,$y: ${ex.message}")
                continue
            }
            if (reached == target) applied += "$name reaches $x,$y"
        }
    }

    private fun fallback(civ: Civilization, error: String?): Outcome {
        NextTurnAutomation.automateCivMoves(civ)
        return Outcome(listOf("stock automation"), fallbackUsed = true, error = error)
    }
}
