# 现代竞技俄罗斯方块规则调研笔记

> 调研日期：2026-08-22（GMT+8）

## 已核验来源

| 来源 | 链接 | 已确认的要点 |
|---|---|---|
| TETR.IO 官方首页 | https://tetr.io/ | 官方站点提供 PLAY 入口，并链接到 Tetra Channel、桌面客户端与官方问题追踪；页面标示为 BETA。 |
| Wiki for TETR.IO：Mechanics | https://tetrio.wiki.gg/wiki/Mechanics | 多人进攻的基础是由棋盘底部出现、带单孔的灰色垃圾行。单次消行产生与抵消垃圾的数量受消行行数、旋转、是否消除垃圾、B2B、连击和压力时间影响。页面称 2024-07-26 起的默认多人机制为 B2B Charging，替换旧的 B2B Chaining。 |

## 初步技术结论

1. 现代竞技对战通常不是“比总分”的单局规则，而是以**向对手施压、抵消来袭垃圾、使对手堆顶（top out）**为核心的实时攻防。
2. B2B 与连击通常同时构成进攻激励；垃圾抵消是防御与反打的必要机制。
3. TETR.IO 当前默认多人规则已有特定的 B2B Charging / Surge 设计，适合其快节奏线上对战，但复杂度较高，不能不加选择地照搬到轻量局域网产品。

## 待补充核验

- 7-bag 随机器及 Guideline 依据；
- Tetris 99 的大逃杀目标选择、徽章与胜负分层；
- Puyo Puyo Tetris 2 的在线联赛/多模式特点；
- TETR.IO 段位或 Glicko 类积分的可验证资料；
- 针对方块空间的两人、三四人局域网规则推荐。

## 新增已核验来源

| 来源 | 链接 | 已确认的要点 |
|---|---|---|
| TetrisWiki：Tetris 99 | https://tetris.wiki/Tetris_99 | Tetris 99 是以垃圾行为基础的在线大逃杀，目标为最后存活者。玩家可选择徽章、KOs、攻击者、随机四种自动目标，也可手动选人。击败玩家获得徽章，徽章提升攻击力；来袭垃圾先进延迟队列，延迟会随在场人数降低。 |
| TetrisWiki：Random Generator | https://tetris.wiki/Random_Generator | 7-bag 是遵循 Tetris Guideline 的 Tetris 品牌游戏所使用的方块生成算法：每袋恰有 I/J/L/O/S/T/Z 各一个，洗牌后依序发放。特定方块的两次出现之间最多间隔 12 块；S/Z 连续最多 4 块。 |
| Wiki for TETR.IO：TETRA LEAGUE | https://tetrio.wiki.gg/wiki/TETRA_LEAGUE | Tetra League 的单场是 1v1 多回合对局，以 FT（先胜指定回合数）决出比赛胜者；页面列出低、中、高段常用 FT3、FT5、FT7 档位。 |

## 进一步技术结论

4. **7-bag 是现代 Guideline 体系的默认刷新方式**，能显著限制 I 等关键块的极端干旱；它是竞技公平性与可练习性的基础，不应在竞技默认模式中改用完全独立均匀随机。
5. **竞技排名的结算单位应是完整“比赛”而不是单小局，也不应直接以游戏内总分定胜负。** Tetra League 的 FT 多回合结构降低单局波动，并随竞争等级加长比赛。
6. **大逃杀和 1v1/小房间应使用不同层级的规则。** Tetris 99 的徽章、攻击者目标奖励和随人数变快的垃圾延迟，服务于 99 人目标分配；不适合原样搬到 2–4 人局域网房间。

## 待补充核验

- Puyo Puyo Tetris 2 的在线联赛/多模式特点；
- TETR.IO 段位或 Glicko 类积分的可验证资料；
- 针对方块空间的两人、三四人局域网规则推荐。
