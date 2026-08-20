# 俄罗斯方块触控灵敏度资料摘录

## Tetris Effect: Connected 官方社区指南

- URL: https://tetriseffect.game/beginners-community-guide/
- ARR（Auto Repeat Rate）定义为按住左右输入时方块重复移动的频率。
- 该指南写明 Tetris Effect 的 ARR 默认值为 **30 Hz**，即约 **33.3ms/次**。
- DAS（Delayed Auto Shift）定义为按住方向输入到启动自动重复之间的等待时间。
- 该指南写明 Tetris Effect 的 DAS 默认值为 **166ms**（在 60Hz 假设下为 10 帧）。

## 游戏触屏延迟研究线索

- IEEE 2024《Performance and Reliability of Touch Screen: A Game-Oriented Research》：检索结果摘要称，其游戏场景实验的移动设备平均点击延迟为 **94–162ms**，平均滑动延迟为 **116–187ms**。IEEE 页面当前出现反自动化限制，未能读取论文全文；最终报告应以检索摘要表述并明确其可访问性限制。
- 相关链接：https://ieeexplore.ieee.org/abstract/document/10596790/

## 初步推断

- 设备与系统本身已引入显著点击延迟，因此应用层“单击”应在按下事件立即执行，不应额外设置人为延迟。
- DAS/ARR 没有全球唯一最优值。官方默认 166ms/30Hz 是可靠参照；移动触控宜根据误触和手指微调负担提供预设或调节范围。

## TetrisWiki：DAS 技术定义

- URL: https://tetris.wiki/DAS
- DAS delay 是从玩家持续按住方向键到自动横移开始之间的延迟；也称 DAS startup。
- DAS 可用毫秒或帧计量，且不同实现可能对起始帧是否计入存在差异，产品界面应明确以毫秒表示。
- Auto-repeat rate 是持续按住方向时自动重复输入的频率，也可等价用重复间隔（毫秒）表示。

这说明“快速点击”和“长按自动移动”应当在产品术语和设置上分开：单击反应时间属于输入路径问题，而 DAS/ARR 专门描述持续按住后的自动移动节奏。
