# UncivX (uncivX-test)

Unciv 的 AI 玩家代理构建。基于 [UncivCN 4.22.4.4](https://github.com/AutumnPizazz/Unciv)（文明战争中文社区版），
在上游代码上增加了一层由大语言模型驱动的托管功能，可直接编译成 Android 5.1 (API 21) 32 位 APK。

上游说明见 [README.md](README.md)。

## 功能

| 功能 | 说明 |
|---|---|
| AI 托管 | 右侧悬浮栏可让 AI 操作当前回合，支持“定方向”（只定科技/政策，落子交给内置自动化）与“逐项”（指定每个城市生产、每个单位移动）两种粒度 |
| 中转站接入 | 设置页填写 OpenAI 兼容地址、Key、模型名，三项都支持从剪贴板粘贴；带“测试连接” |
| 全图输入 | 独立开关，把整张地图（含未探索区域）同时给 AI 与你自己的屏幕；不修改 `DebugUtils`，不影响内置 AI 判定 |
| 敌方雷达 | 在主地图上标出敌方单位位置，不受探索范围限制 |
| 单位名称 | 可切换在地图上显示单位名称 |
| 敌方兵力 | 悬浮栏显示正在交战的敌国军事/平民单位数量 |
| AI 问答 | 独立对话框，可就当前局势提问；每次提问自动附带完整局面与兵力明细 |
| 宣战确认 | AI 判定要开战时先弹窗询问，玩家同意才真正宣战 |

## 构建

工作流 [.github/workflows/build-ai-apk.yml](.github/workflows/build-ai-apk.yml) 在 push 到 `main` 时自动构建。

签名使用仓库 Secret 中的固定密钥（`CI_KEYSTORE_BASE64`、`CI_KEYSTORE_PASSWORD`、`CI_KEY_ALIAS`、`CI_KEY_PASSWORD`），
因此每次产出的 APK 签名一致，可以直接覆盖安装。缺少这些 Secret 时会构建出未签名的 release 包。

产物：`android/build/outputs/apk/release/UncivCN-release.apk`

### 本地构建

需要 JDK 17、Gradle 9.4.1、Android SDK（compileSdk 36、build-tools 36.0.0）。

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :android:assembleRelease
```

## 代码位置

新增代码集中在两处：

- `core/src/com/unciv/logic/llm/` — 设置、中转站客户端、局面序列化、回合决策、敌方兵力统计、宣战队列
- `core/src/com/unciv/ui/screens/worldscreen/view/` — 右侧悬浮栏与 AI 问答对话框

上游文件的改动点：`GameSettings`（设置字段）、`UncivGame`（启动时加载设置）、`TileView`（全图可见性）、
`OptionsPopupPages`/`OptionsPopup`（新设置页签）、`TileLayerUnitSprite`（雷达与单位名称）、
`DeclareWarTargetAutomation`（宣战前询问）。

## 已知限制

- AI 只执行当前回合，不会代替玩家点“I'm done”，也不会代替玩家上传联机存档
- 设置页里的 Key 只保存在本地设置文件，不写入存档、不随联机上传
