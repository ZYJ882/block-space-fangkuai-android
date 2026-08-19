# 俄罗斯方块计分规则研究笔记

## 来源与已核实内容

1. TetrisWiki，<https://tetris.wiki/Scoring>
   - 近期 Guideline 兼容游戏常用基础分：单消 100×等级、双消 300×等级、三消 500×等级、四消 800×等级。
   - Mini T-Spin / T-Spin、Back-to-Back、Combo、软降与硬降均为独立计分来源。
   - Back-to-Back 困难消行通常为动作基础分 ×1.5；Combo 通常为 50×连击次数×等级；软降为每格 1 分，硬降为每格 2 分。

2. Hard Drop Tetris Wiki，<https://harddrop.com/wiki/Tetris_(Game_Boy)>
   - Game Boy 经典规则在等级 0 时：单消 40、双消 100、三消 300、四消 1200。
   - 高等级时，消行基础分乘以（等级+1）；软降得分不受等级倍数影响。

3. TetrisWiki，<https://tetris.wiki/Tetris_Effect>
   - Tetris Effect 的 Zone 期间，完成 Octoris（8 行）或更多时，每次增加 +1 倍数；满能量进入 Zone 额外 +1 倍，最高 3 倍。
   - Zone 中消行分数在 Zone 结束后结算，并额外给每条消除行 +100 分。
   - Zone 的 4–7 行称 Tetris，8–11 行称 Octoris，12–15 行称 Dodecatris，20 行称 Ultimatris。

> 说明：不同发行版本的具体数值并不统一。最终报告必须按版本区分，不能将 Guideline、任天堂经典版和特定现代作品的规则视为同一标准。

4. Hard Drop Tetris Wiki，<https://harddrop.com/wiki/Tetris_99>
   - Tetris 99 的核心竞技结算是“发送垃圾行”而非传统积分榜分数；Tetris 与 T-Spin 等动作配合 Back-to-Back、连击产生攻击行。
   - 徽章使攻击力获得 0%、25%、50%、75% 或 100% 加成，分别对应 1.00、1.25、1.50、1.75、2.00 倍；加成后向下取整。
   - 多名攻击者还会提供额外攻击行。攻击结算会受上限限制，因此适合单独作为对战资源体系，而不应与单机高分简单等同。

## 初步分类

| 体系 | 核心奖励目标 | 代表版本 |
|---|---|---|
| 经典消行 | 奖励一次多消，尤其四消 | BPS、任天堂 NES / Game Boy |
| 等级阶梯 | 随速度 / 等级提高整体得分 | 任天堂、Sega |
| 技术动作 | 奖励 T-Spin、连击与 B2B | Guideline 兼容现代作品 |
| 特殊状态 | 奖励特定机制中超大消行或乘区 | Tetris Effect Zone |
| 对战资源 | 将消行转换为攻击行和倍率 | Tetris 99 |

