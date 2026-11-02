# uncivCN-X

一个基于 [UncivCN（文明战争中文社区版）](https://github.com/AutumnPizazz/Unciv) 的 Android 客户端分支，
在中文社区版的基础上增加了由大语言模型驱动的**AI 玩家代理**功能。

当前版本 **V1.0.0**，仅提供 **Android** 构建，目标为 Android 5.1 (API 21) 及以上，包含 32 位 `armeabi-v7a` 与 64 位 `arm64-v8a`。

本项目自身的变更记录见 [CHANGELOG-CN-X.md](CHANGELOG-CN-X.md)，上游游戏的变更记录见 [changelog.md](changelog.md)。

## 来源声明

本项目**不是** Unciv 官方版本，也不是文明战争中文社区版的官方发布。

- 上游游戏：Unciv，作者 [yairm210](https://github.com/yairm210/Unciv)，许可证 MPL-2.0
- 中文社区版：UncivCN，作者 [AutumnPizazz](https://github.com/AutumnPizazz/Unciv)，许可证 MPL-2.0
- 本项目在其源码上修改，修改部分同样以 MPL-2.0 发布，详见 [LICENSE](LICENSE)

游戏本体、美术、音乐与规则数据的版权归各自作者所有。本仓库只包含源码与构建配置，
不包含原版的商店页面、社区渠道或官方下载地址。

## 增加的功能

| 功能 | 说明 |
|---|---|
| AI 托管 | 右侧悬浮球展开后可让 AI 操作当前回合，支持“定方向”与“逐项”两种粒度 |
| 中转站接入 | 设置页填写 OpenAI 兼容地址、Key、模型名，三项支持从剪贴板粘贴，带连接测试 |
| 全图输入 | 独立开关，把完整地图同时提供给 AI 与本地显示，不修改调试开关，不影响内置 AI 判定 |
| 敌方雷达 | 在地图上标出敌方单位位置，不受探索范围限制 |
| 单位名称 | 可切换在地图上显示单位名称 |
| 敌方兵力 | 悬浮球面板显示交战方的军事与平民单位数量 |
| AI 问答 | 独立对话框，可就当前局势提问，每次提问自动附带完整局面 |
| 宣战确认 | AI 判定要开战时先征求玩家同意 |
| 音乐播放器 | 可导入本地音乐、管理歌单，支持 mp3/ogg/flac/m4a/opus 等格式 |
| 错误日志 | 出错弹出可滚动、可一键复制的完整堆栈窗口 |

## 构建

仓库自带 GitHub Actions 工作流 [.github/workflows/build-ai-apk.yml](.github/workflows/build-ai-apk.yml)，
推送到 `main` 即自动构建：

1. 编译 `:android:assembleRelease`
2. 产物写入 `release/`，仅保留最近 5 个 APK
3. 同步创建 GitHub Release 并上传附件，供应用内更新检查

签名使用仓库内固定的密钥（`signing/keystore.b64`），因此相邻版本可以直接覆盖安装。

### 本地构建

需要 JDK 17、Gradle 9.4.1、Android SDK（compileSdk 36、build-tools 36.0.0）：

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :android:assembleRelease
```

产物：`android/build/outputs/apk/release/*.apk`

## 代码位置

新增代码集中在两处：

- `core/src/com/unciv/logic/llm/` — 设置、中转站客户端、局面序列化、回合决策、敌方兵力统计、宣战队列
- `core/src/com/unciv/logic/music/` — 音乐播放器
- `core/src/com/unciv/ui/screens/worldscreen/view/` — 悬浮球、AI 问答、音乐播放器、错误日志窗口

对上游文件的改动点：`GameSettings`（设置字段）、`UncivGame`（启动加载设置）、`TileView` / `CivView` /
`Civilization` / `ForeignCityView`（全图可见性）、`OptionsPopupPages` / `OptionsPopup`（设置页签）、
`TileLayerUnitSprite`（雷达与单位名称）、`DeclareWarTargetAutomation`（宣战前询问）、
`PlatformSpecific` / `AndroidGame`（播放与键盘）、`Constants` / `UpdateChecker`（更新来源）。

## 已知限制

- AI 只执行当前回合，不会代替玩家结束回合，也不会代替玩家上传联机存档
- 设置中的 API Key 仅保存在本地设置文件，不写入存档，不随联机上传
- 全图开关只改变显示与 AI 输入，不修改存档数据
- 仓库内包含固定签名密钥，任何人可据此产出同包名安装包；如介意请自行替换 `signing/keystore.b64`

## 许可证

本项目遵循 **Mozilla Public License 2.0**，与上游一致。完整条款见 [LICENSE](LICENSE)。

修改过的源文件继续适用 MPL-2.0；你可以自由使用、修改与再分发，但分发修改后的源码时需一并提供对应源码。
