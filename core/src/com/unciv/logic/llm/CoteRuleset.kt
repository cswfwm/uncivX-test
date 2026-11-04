package com.unciv.logic.llm

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.utils.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Player-supplied AI rule set, loaded from a `.cote` file.
 *
 * The extension marks it as a configuration file while the content stays ordinary JSON, so anyone
 * can write or edit one by hand and the AI can generate one directly.
 */
@Serializable
data class CoteRuleset(
    /** Format marker, must be "cote" so a renamed file cannot be mistaken for a rule set. */
    val format: String = "cote",
    /** Schema version, currently 1. */
    val version: Int = 1,
    val name: String = "自定义规则集",
    val description: String = "",
    /** Personality sliders, each 0..100. Absent fields keep the built-in defaults. */
    val tendencies: CoteTendencies = CoteTendencies(),
    /** Free-form rules, injected into the prompt in order. */
    val rules: List<CoteRule> = emptyList()
)

@Serializable
data class CoteTendencies(
    /** 0 = 龟缩防守, 100 = 全力进攻 */
    val aggression: Int = 50,
    /** 0 = 只求生存, 100 = 全力扩张铺城 */
    val expansion: Int = 50,
    /** 0 = 从不交易, 100 = 尽量谈判成交 */
    val diplomacy: Int = 50,
    /** 0 = 见好就收, 100 = 交易中尽可能抬价 */
    val tradeProfit: Int = 50,
    /** 0 = 严守承诺, 100 = 为利益可以毁约 */
    val betrayal: Int = 20,
    /** 0 = 建筑优先, 100 = 军事优先 */
    val militaryFocus: Int = 50,
    /** 0 = 忽视科研, 100 = 科研优先 */
    val scienceFocus: Int = 50
)

@Serializable
data class CoteRule(
    /** Short identifier shown in the UI when the rule is applied. */
    val id: String,
    /** One sentence the model reads. Keep it a concrete instruction, not a vague goal. */
    val text: String,
    /** Optional condition key; see [CoteRulesetLoader.CONDITIONS] for the supported values. */
    val when: String? = null
)

object CoteRulesetLoader {

    const val EXTENSION = "cote"

    /** Directory the game reads rule sets from. */
    private const val FOLDER = "rulesets"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    /** Condition keys a rule may reference in its `when` field. */
    val CONDITIONS = mapOf(
        "earlyGame" to "开局阶段（前 60 回合）",
        "atWar" to "正在交战",
        "happinessNegative" to "幸福度为负",
        "goldNegative" to "金币收入为负",
        "fewCities" to "城市数少于 3",
        "always" to "任何情况"
    )

    /** Currently loaded rule set, or null when the player uses the built-in guidance. */
    @Volatile
    var active: CoteRuleset? = null
        private set

    fun folder(): FileHandle {
        val handle = UncivGame.Current.files.getLocalFile(FOLDER)
        if (!handle.exists()) handle.mkdirs()
        return handle
    }

    /** Parses text without touching the active rule set; used for validation and preview. */
    fun parse(text: String): Result<CoteRuleset> = try {
        val ruleset = json.decodeFromString(CoteRuleset.serializer(), text)
        validate(ruleset)
    } catch (ex: Exception) {
        Result.failure(ex)
    }

    private fun validate(ruleset: CoteRuleset): Result<CoteRuleset> {
        if (ruleset.format != "cote")
            return Result.failure(IllegalArgumentException("format 必须是 \"cote\""))
        if (ruleset.version != 1)
            return Result.failure(IllegalArgumentException("不支持的版本：${ruleset.version}"))
        val unknown = ruleset.rules.mapNotNull { it.when }.filter { it !in CONDITIONS }
        if (unknown.isNotEmpty())
            return Result.failure(IllegalArgumentException("未知的条件：${unknown.joinToString(", ")}"))
        val ids = ruleset.rules.map { it.id }
        if (ids.size != ids.toSet().size)
            return Result.failure(IllegalArgumentException("规则 id 不能重复"))
        return Result.success(ruleset)
    }

    /** Loads and activates a file from the game's ruleset folder. */
    fun load(file: FileHandle): Result<CoteRuleset> {
        val parsed = parse(file.readString("UTF-8"))
        parsed.onSuccess { active = it }
        return parsed
    }

    fun clear() {
        active = null
    }

    /** Lists `.cote` files the player dropped into the ruleset folder. */
    fun available(): List<FileHandle> = try {
        folder().list().filter { !it.isDirectory && it.extension().equals(EXTENSION, true) }
            .sortedBy { it.name() }
    } catch (ex: Exception) {
        Log.debug("Could not list rulesets: ${ex.message}")
        emptyList()
    }

    /** Tendency description handed to the model, empty when no rule set is active. */
    fun tendencyText(): String {
        val ruleset = active ?: return ""
        val t = ruleset.tendencies
        return buildString {
            appendLine("玩家自定义倾向（0-100）：")
            appendLine("- 进攻性 ${t.aggression}：越高越应主动寻找战机")
            appendLine("- 扩张 ${t.expansion}：越高越应优先铺城抢地")
            appendLine("- 外交接触 ${t.diplomacy}：越高越应主动谈判")
            appendLine("- 交易抬价 ${t.tradeProfit}：越高越应在交易中争取更高回报")
            appendLine("- 毁约倾向 ${t.betrayal}：越高越可以背弃协议换取利益")
            appendLine("- 军事优先 ${t.militaryFocus}：越高越应优先造兵")
            appendLine("- 科研优先 ${t.scienceFocus}：越高越应优先科研")
        }
    }

    /** Rules whose condition matches the current context. */
    fun activeRules(): List<CoteRule> {
        val ruleset = active ?: return emptyList()
        val civ = currentCivOrNull() ?: return ruleset.rules.filter { it.when == null || it.when == "always" }
        val context = StrategyGuide.contextOf(civ)
        return ruleset.rules.filter { rule ->
            when (rule.when) {
                null, "always" -> true
                "earlyGame" -> context.isEarlyGame
                "atWar" -> context.isAtWar
                "happinessNegative" -> context.happiness < 0
                "goldNegative" -> context.goldPerTurn < 0
                "fewCities" -> context.cityCount < 3
                else -> false
            }
        }
    }

    private fun currentCivOrNull(): com.unciv.logic.civilization.Civilization? =
        try {
            UncivGame.Current.gameInfo?.currentPlayerCiv
        } catch (ex: Exception) {
            null
        }

    /** The documented example shipped for players to copy. */
    fun example(): CoteRuleset = CoteRuleset(
        name = "示例：稳健扩张",
        description = "示例规则集，展示所有可用字段",
        tendencies = CoteTendencies(
            aggression = 35, expansion = 70, diplomacy = 60,
            tradeProfit = 65, betrayal = 10, militaryFocus = 45, scienceFocus = 60
        ),
        rules = listOf(
            CoteRule("open-settle", "前 60 回合优先生产开拓者，尽量在优质地块建城。", "earlyGame"),
            CoteRule("guard-capital", "首都始终保留至少一个军事单位驻守。", "always"),
            CoteRule("war-economy", "交战时把产能优先给军事单位。", "atWar")
        )
    )

    fun exampleText(): String = json.encodeToString(CoteRuleset.serializer(), example())

    /** Writes the example into the ruleset folder so players have a starting point. */
    fun writeExample(): FileHandle {
        val file = folder().child("example.$EXTENSION")
        file.writeString(exampleText(), false, "UTF-8")
        return file
    }
}
