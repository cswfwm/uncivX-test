# 来源与许可证说明

## 上游项目

本仓库是 Unciv 的一个分支，逐级来源如下：

1. **Unciv** — https://github.com/yairm210/Unciv
   作者：Yair Morgenstern 及众多贡献者
   许可证：Mozilla Public License 2.0

2. **UncivCN（文明战争中文社区版）** — https://github.com/AutumnPizazz/Unciv
   在 Unciv 基础上进行中文本地化与功能增强
   许可证：Mozilla Public License 2.0
   本仓库的代码基线取自其 `4.22.4.4` 版本

3. **uncivCN-X** — 本仓库
   在 UncivCN 基础上增加 AI 托管、音乐播放器等功能

## 我们的修改

本仓库相对上游的修改集中在以下文件，其余文件与上游保持一致：

- 新增 `core/src/com/unciv/logic/llm/` 与 `core/src/com/unciv/logic/music/`
- 新增 `core/src/com/unciv/ui/screens/worldscreen/view/` 下的面板、问答、播放器、错误窗口
- 改动 `GameSettings`、`UncivGame`、`TileView`、`CivView`、`Civilization`、`ForeignCityView`、
  `OptionsPopupPages`、`OptionsPopup`、`TileLayerUnitSprite`、`DeclareWarTargetAutomation`、
  `PlatformSpecific`、`AndroidGame`、`Constants`、`UpdateChecker`、`BuildConfig`
- 替换 `README.md`，移除上游的商店元数据目录 `fastlane/`

## 许可证遵循

本项目整体遵循 **Mozilla Public License 2.0**，完整条款见仓库根目录的 [LICENSE](LICENSE)。

按照 MPL-2.0 的要求：

- 修改过的源文件继续适用 MPL-2.0
- 分发本项目的可执行形式时，需要一并提供对应源码（本仓库即为该源码）
- 不得移除或修改源码中的许可证声明
- 未修改的上游文件保持其原有版权归属

## 版权归属

游戏本体、美术资源、音乐、规则数据与翻译的版权归 Unciv 及 UncivCN 的各自作者所有。
本项目仅分发源码与构建配置，不主张对上述内容的所有权。

## 非官方声明

本项目不是 Unciv 官方版本，也不是文明战争中文社区版的官方发布，
与其作者没有隶属或背书关系。请勿将本项目的问题反馈给上游项目。
