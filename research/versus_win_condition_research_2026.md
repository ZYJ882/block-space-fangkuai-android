# 现代俄罗斯方块对战胜负规则调研记录

> 调研日期：2026-08-22（GMT+8）
>
> 问题：对战中一名玩家堆顶后，是否应立刻输，还是等待对手超过其分数？

## 已核验结论

| 产品或场景 | 核心目标 | 一人堆顶后的结果 | 与方块空间的关联 |
|---|---|---|---|
| TETR.IO 的标准多人攻防 | 通过消行发送灰色垃圾，迫使对手堆顶 | 攻防模式的胜负目标是造成对手 top out；不是比较普通得分 | 标准 Attack 模式的主要参考 |
| TETRA LEAGUE 排位 | 1v1 多局赛；不同段位使用先胜 3/5/7 局 | 每一局的胜者累积一局；TR 按整场胜负变动，不按单局普通分数 | FT3 是合理的休闲 LAN 简化版 |
| Tetris 99 大逃杀 | 垃圾攻防、生存淘汰 | 目标为最后存活；堆顶即被淘汰 | 适合 3–4 人 Race / Attack 的存活判定 |
| Tetris Effect Connected Zone Battle | 传统 Vs. 攻防，目标是向对手发送垃圾并令其堆顶 | 堆顶服务于攻防胜负；可配置多局胜场数 | Attack 模式可参考；不应混入 Score Attack 规则 |
| Tetris Effect Connected Score Attack / Classic Score Attack | 得分竞赛 | 领先者先堆顶后，落后者可在 2 分钟内追平或超过 | 合理但**专属**于积分竞赛，不适用于垃圾攻防 |

## 原因分析

现代竞技攻防模式的“分数”主要作为表现信息，而不作为本局胜负的后备判定。胜负由存活状态决定：攻击被抵消、延迟进入棋盘并最终造成真实堆顶；堆顶者负，仍存活者胜。这样目标单一、观战可读性高，且不会出现“故意提前堆顶以守住分数”的反向激励。

积分追赶规则则属于不同游戏类型。Tetris Effect Connected 的 Score Attack 明确以取得比对手更高的分数为目标，因此才设计领先者堆顶后给对手最多两分钟追分。它没有将灰色垃圾攻防作为主要胜负路径。

## 可复核来源

1. TETR.IO Mechanics：https://tetrio.wiki.gg/wiki/Mechanics
2. TETRA LEAGUE：https://tetrio.wiki.gg/wiki/TETRA_LEAGUE
3. Tetris 99：https://tetris.wiki/Tetris_99
4. Tetris Effect: Connected Beginner’s Community Guide：https://tetriseffect.game/beginners-community-guide/
5. Tetris Effect: Connected Multiplayer Guide：https://gamefaqs.gamespot.com/pc/296457-tetris-effect-connected/faqs/78800/multiplayer
6. Puyo Puyo Tetris 2 官方页面：https://puyo.sega.com/tetris2/

## 节奏公平与防拖延补充

当前方块空间中，硬降每格加 2 分、软降每格加 1 分，且游戏等级只按各自消行数提升；局域网 Race 不发送攻击，也没有目标行数、统一计时或动态压力。因此，硬降会加快该玩家的锁块、消行与个人等级推进，但其额外分数不会决定胜负；相对慢速的玩家无法无限悬空（自动重力、500ms 锁定延迟和最多 15 次落地重置会推进方块），但仍可能通过低清行速度显著降低自身风险并拖延 Race 对局。

TETR.IO 等现代攻防模式通常不使用普通分数作为拖延处理：其核心压力来自垃圾攻防，并可用 garbage/gravity margin、阶段加速、疲劳或目标推进增加持续时间过长时的压力。Tetris Effect 的 Zone Battle 也以垃圾攻防和阶段推进为核心；相反，Score Attack 才以分数和明确追分窗口为核心。这支持将“竞速”改为有明确完成目标或时间上限的独立模式，而不是用“最后存活者”定义无垃圾 Race。

### 针对方块空间的建议

| 模式 | 建议结算 | 硬降与分数的地位 | 防拖延措施 |
|---|---|---|---|
| 标准攻击 / Attack | 堆顶即负；FT3 | 硬降是节奏/风险选择；普通分数只展示 | 3 分钟后逐步提高垃圾压力或重力；不以分数裁决 |
| 竞速 / Sprint Race | 首先完成 40 行者赢；FT3 | 硬降直接提高完成速度；分数不参与胜负 | 2:30 上限；时间到按完成行数、再按较低堆高、最后按较高分数排序 |
| 积分追赶 / Score Attack（未来独立模式） | 固定时长最高分，或领先者堆顶后给对方 120 秒追分 | 硬降分数可计入，和消行分共同决定名次 | 固定时长或 120 秒追分窗口；不发送垃圾 |

### 来源补充

7. TETR.IO 条目（多人中包含垃圾/重力 margin 的说明）：https://tetris.wiki/TETR.IO
8. TETR.IO Patch Notes（规则与动态压力相关更新）：https://tetr.io/about/patchnotes/
