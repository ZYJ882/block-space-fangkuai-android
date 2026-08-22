# v1.24.0 40 行竞速公平性审查记录

> 审查日期：2026-08-22（GMT+8）
>
> 目标版本：`1.24.0`（`versionCode 38`）
>
> 包名：`com.blockspace.tetris`

## 问题与改动

v1.23.0 的默认无垃圾 Race 使用“最后存活者”逻辑。由于个人消行数会影响个人等级，而硬降会加快个人锁块与消行，这会让积极推进的一方更快承担失误与堆顶风险；另一方虽不能无限阻止自动下落，却可通过低清行速度显著拖延对局。普通得分又不参与胜负，因此该规则既不是严格竞速，也不是标准攻防。

v1.24.0 将 Race 改为明确的 **40 行竞速**。先在不堆顶的前提下累计消除 40 行的玩家立即赢下本局；在达到 40 行前发生真实堆顶者立即失败，即使其当时累计行数较多。这使硬降成为合理的速度与风险选择，而不是不对称的负担。

| 情形 | 结算 | 设计目的 |
|---|---|---|
| 任一玩家安全消除 40 行 | 该玩家立即赢本局 | 固定、可观察的完成目标 |
| 玩家在完成前堆顶 | 该玩家输；其他存活玩家继续 | 防止堆高换取领先行数后仍要求比分裁决 |
| 2 分 30 秒无人完成 | 房主按消行数更多、棋盘高度更低、分数更高依次裁决 | 防止慢速拖延，同时让主要指标始终是竞速进度 |
| 三项超时指标完全相同 | 本局平局，原局重赛；不计 FT 胜场 | 不以网络消息到达顺序、玩家 ID 或随机数决定胜者 |
| 来宾断线或心跳超时 | 取消整场 FT3，不宣布胜者 | 延续 v1.22.1 的网络可靠性保护 |

## 同步与安全边界

协议升级为 `5`。所有多人房间仍强制标准重力、7-Bag 与房主下发的固定种子。来宾只同步棋盘、消行、得分等状态；房主依据已接收的状态快照检测 40 行完成，并负责超时裁决。客户端不会以独立“我已完成”声明直接决定胜负。

Race 定时器只由房主启动。它在新局开始后等待 150,000ms；常规胜者、断线取消、会话关闭和下一局重置都会取消旧定时器，避免过期协程改变后续回合。超时裁决只考察仍存活玩家；完全同分时广播 `ROUND_TIE`，所有客户端先进入回合间隔，再以新种子重置原局。

## 自动化验证范围

`RaceRulesTest` 覆盖完成目标、超时裁决的行数优先级、较低堆高次级优先级、分数末级优先级、完全平局重赛和棋盘高度计算。完整 Gradle 回归将在最终发布构建时执行，并更新测试总数、签名和 APK SHA-256。

## 参考依据

现代垃圾攻防不以普通分数的追赶决定本局胜负，而以垃圾、存活和持续压力推进；积分追赶是独立的 Score Attack 类模式。详见 [`versus_win_condition_research_2026.md`](versus_win_condition_research_2026.md) 和以下来源。

1. [TETR.IO Mechanics](https://tetrio.wiki.gg/wiki/Mechanics)
2. [TETR.IO 条目：多人压力与动态机制](https://tetris.wiki/TETR.IO)
3. [Tetris Effect: Connected Beginner’s Community Guide](https://tetriseffect.game/beginners-community-guide/)
4. [Tetris Effect: Connected Score Attack 规则说明](https://gamefaqs.gamespot.com/pc/296457-tetris-effect-connected/faqs/78800/multiplayer)

## 最终验证结果

| 检查项 | 结果 |
|---|---|
| `RaceRulesTest` | 6 项通过：40 行目标、行数优先、堆高次级、分数末级、完全平局、堆高计算 |
| 全部 Debug 单元测试 | 43 项通过；0 failures；0 errors；0 skipped |
| Gradle 命令 | `clean testDebugUnitTest assembleDebug` 成功 |
| APK 签名 | Android Debug APK Signature Scheme v2 验证通过 |
| APK SHA-256 | `9c5bfa2a801526dd4c856813afdfc579f2ff4d0e940fc8f0bd66c22e70a10a01` |
