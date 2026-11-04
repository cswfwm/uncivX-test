package com.unciv.logic.llm

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.UniqueType

/**
 * Strategy knowledge handed to the model alongside the board state.
 *
 * The stock AI has no explicit opening doctrine: it will happily keep a warrior home while the
 * map fills up. These are the rules a competent human player applies, kept here as data so they
 * can be tuned without touching the prompt logic.
 */
object StrategyGuide {

    private class Rule(
        val applies: (StrategyContext) -> Boolean,
        val text: String
    )

    /** Everything the rules branch on, computed once per turn. */
    data class StrategyContext(
        val turn: Int,
        val cityCount: Int,
        val settlerCount: Int,
        val militaryCount: Int,
        val workerCount: Int,
        val isEarlyGame: Boolean,
        val isAtWar: Boolean,
        val isBehindInCities: Boolean,
        val happiness: Int,
        val goldPerTurn: Int,
        val totalPopulation: Int
    )

    fun contextOf(civ: Civilization): StrategyContext {
        val units = civ.units.getCivUnits().toList()
        val settlerCount = units.count { it.hasUnique(UniqueType.FoundCity) }
        val workerCount = units.count { it.hasUnique(UniqueType.BuildImprovements) }
        val militaryCount = units.count { it.isMilitary() }
        val cities = civ.cities.size
        // A 1-3 city empire around turn 50 is normal on standard speed; fewer means falling behind.
        val behindInCities = civ.gameInfo.turns > 40 && cities < 3
        return StrategyContext(
            turn = civ.gameInfo.turns,
            cityCount = cities,
            settlerCount = settlerCount,
            militaryCount = militaryCount,
            workerCount = workerCount,
            isEarlyGame = civ.gameInfo.turns <= 60,
            isAtWar = civ.isAtWar(),
            isBehindInCities = behindInCities,
            happiness = civ.getHappiness(),
            goldPerTurn = civ.stats.statsForNextTurn.gold.toInt(),
            totalPopulation = civ.cities.sumOf { it.population.population }
        )
    }

    private val rules = listOf(
        Rule(
            applies = { it.isEarlyGame && it.settlerCount == 0 && it.cityCount < 4 },
            text = "开局首要任务是抢地：尽早产出开拓者并铺城。城市数量决定长期国力，" +
                "空地一旦被邻国占据就无法逆转。优先让首都生产开拓者，其次才是兵营类建筑。"
        ),
        Rule(
            applies = { it.isEarlyGame && it.militaryCount <= 1 },
            text = "开局至少保留 1-2 个单位护卫首都与新城市，防止蛮族和突袭。" +
                "侦察单位要持续探图，寻找优质城址与邻国方位。"
        ),
        Rule(
            applies = { it.settlerCount > 0 },
            text = "已有开拓者时，优先把它送到有淡水、有奢侈品或战略资源的位置建城，" +
                "不要长时间滞留原地。"
        ),
        Rule(
            applies = { it.isEarlyGame && it.workerCount == 0 && it.cityCount >= 2 },
            text = "两城之后应尽快产出工人改良地块，落后的人口增长会拖慢一切。"
        ),
        Rule(
            applies = { !it.isEarlyGame && it.cityCount <= 3 && !it.isAtWar },
            text = "中期仍有扩张空间时，继续铺城或吞并城邦，不要过早转入纯内政。"
        ),
        Rule(
            applies = { it.isBehindInCities },
            text = "你的城市数明显少于正常节奏，应把资源倾斜到扩张而不是奇观。"
        ),
        Rule(
            applies = { it.isAtWar },
            text = "战时优先保证军事生产与前线的单位补充，避免在后方堆叠无用的建筑。"
        ),
        Rule(
            applies = { it.happiness < 0 },
            text = "幸福度为负会直接削减产能与人口增长，此时优先解决幸福：" +
                "连奢侈品、造竞技场一类建筑，停止继续铺城，直到回到 0 以上。"
        ),
        Rule(
            applies = { it.happiness in 0..3 && it.cityCount >= 3 },
            text = "幸福度接近临界，铺城或涨人口前先确认有新的奢侈品来源，" +
                "否则新增城市会立刻把幸福拖成负数。"
        ),
        Rule(
            applies = { it.goldPerTurn < 0 },
            text = "金币为负会开始解散单位并拖慢科研，应减少军事维持、卖建筑或提高贸易收入。"
        ),
        Rule(
            applies = { it.totalPopulation < it.cityCount * 3 && it.happiness >= 3 },
            text = "人口偏少说明城市没有在长人口，检查是否粮食不足或被产能偏移带偏；" +
                "幸福有余量时优先水车、粮仓一类增长类建筑。"
        ),
        Rule(
            applies = { true },
            text = "平衡原则：产能应与人口匹配。人口少时堆产能是浪费，人口多而产能低会长期落后；" +
                "每座城市至少保证有粮食来源，再考虑专业化。"
        )
    )

    /** Diplomacy doctrine, always included: the game rewards trading and punishes blind aggression. */
    val diplomacyDoctrine: String =
        "外交原则：
" +
            "- 交易时先判断对方是否急需，急需的物品可以抬价；和平时期多签订开放边界、研究协定，" +
            "这些长期收益高于一次性金币。\n" +
            "- 每一笔交易都要保证自己不亏：用富余的奢侈品换对方稀缺资源，避免用战略资源换金币。\n" +
            "- 不要同时与多方交恶。宣战前确认目标孤立、无强力盟友，且自己已有兵力与后方保障。\n" +
            "- 盟友价值会随局势变化：当盟友变弱或占住你需要的土地时，可以结束同盟，但不要在" +
            "自己兵力不足时翻脸。\n" +
            "- 背叛要有收益：只有在能实际拿到城市、资源或决定性优势时才考虑背弃协议，" +
            "否则损失的外交信誉会拖累整局。"

    /** Rules matching this turn, in priority order. */
    fun adviceFor(civ: Civilization): List<String> {
        val ctx = contextOf(civ)
        return rules.filter { it.applies(ctx) }.map { it.text }
    }

    /** Opening doctrine, always included so the model has a stable baseline. */
    val openingDoctrine: String =
        "通用原则：前期扩张优先于奇观；科技优先农业、畜牧、陶器这类能解锁人口与资源的早期科技；" +
            "不要在城市数量不足时把所有产能投入军事；外交上避免同时与多方开战。"
}
