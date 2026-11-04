package com.unciv.logic.llm

import com.unciv.UncivGame

/**
 * What this build is, for the in-game About page.
 *
 * The changelog is bundled rather than fetched so the About page works offline.
 */
object ReleaseNotes {

    data class Entry(val version: String, val date: String, val items: List<String>)

    fun currentVersion(): String = UncivGame.VERSION.text

    fun currentBuild(): Int = UncivGame.VERSION.number

    /** Newest first. Keep this in sync with CHANGELOG-CN-X.md when releasing. */
    val changelog: List<Entry> = listOf(
        Entry(
            "1.1.1", "2026-11-03",
            listOf(
                "新增关于页：显示版本、日期与内置更新日志",
                "点击版本号可检查更新，有新版时显示更新内容与更新建议",
                "更新提示会说明：直接覆盖安装可保留存档，卸载重装会丢失存档",
                "敌方单位情报补充坐标与最近城市距离",
                "城市信息附带可生产单位清单，修正“生产力不够”的误判",
                "地图上单位名称显示为中文"
            )
        ),
        Entry(
            "1.1.0", "2026-11-03",
            listOf(
                "AI 决策：城市会按战略需要自行生产单位，不再一律交给内置自动化",
                "AI 情报：列出全图每个敌方单位及其坐标、与最近城市的距离，逼近的敌军会标警",
                "AI 情报：每个城市附带可生产单位清单，避免误判“生产力不够”",
                "AI 决策：科技若未选定会自动选择，不再空转一回合",
                "AI 决策：定方向模式改用精简情报，响应更快",
                "AI 决策：宣战、修改设置、下载模组均需玩家确认后执行",
                "AI 决策：联机时可自动点“我完成了”（默认关闭，需手动开启）",
                "AI 指导：新增主菜单入口，可像终端一样下命令",
                "AI 指导：支持真实模组搜索，走你配置的下载源与加速源",
                "AI 指导：对话自动保存，重新进入可接着聊",
                "模组：新增下载列表，可查看排队、下载、解压、完成状态",
                "模组：下载后不会自动启用",
                "语音/文本：输入框修复为正常键盘，不再弹出安全键盘",
                "显示：全图视野、敌方雷达、单位名称，单位名称显示为中文",
                "显示：面板设置（粒度、全图、雷达、名称）会持久保存",
                "其他：新增关于页，可查看版本、更新日志并检查更新"
            )
        ),
        Entry(
            "1.0.0", "2026-11-02",
            listOf(
                "首个版本，基于 UncivCN 4.22.4.4",
                "AI 托管：右侧悬浮球，可让 AI 操作当前回合",
                "AI 托管：定方向与逐项两种粒度",
                "AI 托管：OpenAI 兼容中转站，支持剪贴板粘贴与连接测试",
                "全图视野：独立开关，不影响内置 AI 判定",
                "敌方雷达与单位名称显示",
                "AI 问答：可就当前局势提问",
                "音乐播放器：支持多种音频格式",
                "错误日志窗口：可滚动、可一键复制",
                "应用更名为 uncivCN-X"
            )
        )
    )

    /** One-line summary for the About page header. */
    fun headline(): String = "uncivCN-X ${currentVersion()}（构建 ${currentBuild()}）"
}
