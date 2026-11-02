package com.unciv.logic.llm

import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.utils.Log
import kotlinx.serialization.json.Json
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

    private const val SYSTEM_PROMPT =
        "You are the player of a Civilization V style 4X game (Unciv). " +
        "Answer with a single JSON object and no prose, no markdown fences. " +
        "Allowed fields: " +
        "\"research\": technology name to research next; " +
        "\"policy\": social policy or branch name to adopt; " +
        "\"cities\": array of {\"name\": city name, \"build\": construction name}; " +
        "\"units\": array of {\"id\": unit id from the state, \"move\": [x,y]} for a destination tile; " +
        "\"reasoning\": one short sentence. " +
        "Only use names that appear in the provided game state. Omit anything you are unsure about. " +
        "Write \"reasoning\" in Simplified Chinese."

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
        val state = StateSerializer.describe(civ.gameInfo, civ)
        val warPolicy = if (settings.confirmWarDeclarations)
            "Wars are proposed to the player for approval, never declared silently."
        else
            "Wars may be declared directly."
        val advice = StrategyGuide.adviceFor(civ)
        val answer = LlmClient.complete(
            settings, SYSTEM_PROMPT, buildPrompt(granularity, state, warPolicy, advice))

        if (answer.isFailure) {
            val message = answer.exceptionOrNull()?.message ?: "unknown error"
            onLog("LLM request failed: $message - using stock automation")
            return fallback(civ, message)
        }

        val plan = parsePlan(answer.getOrNull()!!)
        if (plan == null) {
            onLog("LLM answer was not valid JSON - using stock automation")
            return fallback(civ, "invalid JSON")
        }

        val applied = mutableListOf<String>()
        try {
            applyResearch(civ, plan, applied)
            applyPolicy(civ, plan, applied)
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
                "Fill in only \"research\", \"policy\" and \"reasoning\"."
        else
            "Decide the full turn yourself: give a build order for every city and a destination " +
                "for every unit you want to move, identified by its id. Units you omit stay where they are."
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
    private fun applyRemainingCityProduction(civ: Civilization, applied: MutableList<String>) {
        for (city in civ.cities) {
            if (city.cityConstructions.currentConstructionName().isNotEmpty()) continue
            try {
                city.cityConstructions.chooseNextConstruction()
                applied += "${city.name} 自动选择生产"
            } catch (ex: Exception) {
                Log.error("No production for ${city.name}: ${ex.message}")
            }
        }
    }

    private fun reasoningOf(plan: JsonObject): String? =
        plan["reasoning"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private fun parsePlan(raw: String): JsonObject? = try {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) null
        else Json.parseToJsonElement(cleaned.substring(start, end + 1)).jsonObject
    } catch (ex: Exception) {
        Log.error("Could not parse LLM plan: ${ex.message}")
        null
    }

    private fun applyResearch(civ: Civilization, plan: JsonObject, applied: MutableList<String>) {
        val tech = plan["research"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return
        if (!civ.tech.canBeResearched(tech)) return

        // A technology already being researched owns the slot: changing it throws away every
        // science point invested so far, and the model has no reason to do that.
        val inProgress = civ.tech.currentTechnologyName() ?: civ.tech.techsToResearch.firstOrNull()
        if (inProgress != null) {
            if (inProgress != tech)
                Log.debug("Ignoring research change from $inProgress to $tech")
            return
        }
        civ.tech.techsToResearch.add(tech)
        applied += "研究 $tech"
    }

    private fun applyPolicy(civ: Civilization, plan: JsonObject, applied: MutableList<String>) {
        val name = plan["policy"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return
        if (!civ.policies.canAdoptPolicy()) return
        val policy = civ.gameInfo.ruleset.policies[name]
            ?: civ.gameInfo.ruleset.policyBranches[name]
            ?: return
        if (!civ.policies.isAdoptable(policy)) return
        civ.policies.adopt(policy)
        applied += "policy $name"
    }

    private fun applyCities(civ: Civilization, plan: JsonObject, applied: MutableList<String>) {
        val requested = plan["cities"] ?: return
        for (entry in requested.jsonArray) {
            val name = entry.jsonObject["name"]?.jsonPrimitive?.content ?: continue
            val build = entry.jsonObject["build"]?.jsonPrimitive?.content ?: continue
            val city = civ.cities.firstOrNull { it.name == name } ?: continue
            if (!isBuildable(city, build)) continue
            city.cityConstructions.setCurrentConstruction(build)
            applied += "$name builds $build"
        }
    }

    private fun isBuildable(city: City, constructionName: String): Boolean {
        val ruleset = city.getRuleset()
        if (constructionName !in ruleset.buildings && constructionName !in ruleset.units) return false
        return !city.cityConstructions.isBuilt(constructionName)
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
