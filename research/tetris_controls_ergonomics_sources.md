# 俄罗斯方块键位与触控人体工学资料

## Android 触控目标规范

来源：Google Android Accessibility Help，<https://support.google.com/accessibility/android/answer/7101858?hl=en>

Google 建议可交互元素的宽高至少为 48dp；48×48dp 约为 9mm 的物理尺寸，推荐的触控对象尺寸范围为 7–10mm。对于相邻的触控目标，建议至少保留 8dp 间距。文档还说明，触控命中区域可以大于视觉图标边界；在 Jetpack Compose 中，应避免让相邻按钮的可触区域重叠。

## 竞技控制资料获取

- Liquipedia 的 Tetris keybind 页面在当前会话显示人机验证，未将其内容作为事实依据。
- 搜索结果表明，社区常讨论 A/D 或方向键的水平移动、不同旋转键位以及 DAS / ARR；需要通过可正常访问的技术资料或原始游戏设置文档继续核实。

## DAS / ARR 技术资料

来源：Hard Drop Tetris Wiki，<https://harddrop.com/wiki/DAS>

DAS（Delayed Auto Shift）描述按住左右方向后，自动横移开始前的等待时间；ARR（Auto Repeat Rate）描述自动横移开始后的重复频率。该资料说明 DAS 与 ARR 会改变方块移动策略，并指出过快的 DAS 在需要手动松开以停在墙边附近时会增加误放风险。

来源：Tetris Effect: Connected Beginner's Community Guide，<https://tetriseffect.game/beginners-community-guide/>

该官方社区指南给出 Tetris Effect 的默认设置：DAS 为 166ms，ARR 为 30Hz，即约每 33ms 重复一次。此参数可作为高速度现代作品的参考点，而不应直接认定为所有移动端玩家的最佳参数。

