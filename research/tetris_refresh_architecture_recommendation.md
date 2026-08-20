# 俄罗斯方块刷新架构研究建议

## 结论

对于《方块空间》这类 Android Compose 单机俄罗斯方块，最科学的方案不是“固定每 16ms 强制刷新整个界面”，而是将三个节奏解耦：

1. **规则/输入时钟**使用单调时间和毫秒级事件调度，保持重力、锁定延迟、DAS、ARR 和直降保护在不同显示刷新率下拥有一致结果。
2. **渲染**仅在棋盘状态变化或一个短动画实际运行时交给 Compose 的帧时钟按 VSYNC 刷新，不在静止阶段轮询重绘整屏。
3. **显示刷新率**不假设固定 60Hz；Compose UI 路径由 Android UI/Choreographer 处理，只有迁移到 OpenGL/Vulkan/SurfaceView 自定义渲染器时才需要引入 Android Frame Pacing（Swappy）。

## 外部证据

| 主题 | 关键发现 | 对方块空间的含义 |
|---|---|---|
| Android 游戏循环 | 官方指出“更新一帧、渲染一帧、sleep”的朴素循环会与不同/动态显示刷新率错配，导致重复旧帧或可见异常；Choreographer 回调提供 VSYNC 时间。 | 不能把 `delay(16)` 当作渲染刷新率；它最多可作为逻辑计时的唤醒机制。 |
| Android Frame Pacing | 官方 Frame Pacing 文档说明其目标是协调自定义 OpenGL/Vulkan 游戏逻辑、渲染与显示硬件；不同刷新率、短帧/长帧和队列填充都会造成卡顿或输入延迟。 | 当前 Compose Canvas 不应为了这一点迁移到 GL；日后若使用 SurfaceView/GL/Vulkan，优先采用 Frame Pacing。 |
| 固定时间步 | 固定步长/累加器能够保证规则一致性；对渲染帧率与规则频率不整除造成的视觉不连续，可在前后状态间插值。 | 俄罗斯方块的离散格规则可采用“真实经过时间 + 累计重力 + 到期事件”保证确定性；视觉下落通过独立动画插值，而非让逻辑每帧变更。 |
| Compose 动画 | 官方文档说明 `Animatable`/`animate*AsState` 会在动画期间每帧提供更新值；只动画绘制阶段通常比触发布局更高效。 | 保留当前方块 `Animatable` 插值；避免整个游戏屏幕因空帧读写状态而重组。 |
| DAS/ARR | DAS 是按住方向到自动平移开始的延迟，ARR 是后续重复间隔，均可用毫秒或帧描述。 | DAS/ARR 继续以毫秒的单调时间实现，不依赖设备 60/90/120Hz 刷新率。 |

## 推荐架构

### 规则层：事件驱动、单调时间

* 使用 `System.nanoTime()` 或帧回调给出的单调时间计算真实经过时间。
* 对每个事件维护到期时间：重力下一格、锁定截止、DAS 触发、ARR 下一次重复、出生直降保护结束。
* 每次唤醒按经过时间结算全部到期事件；发生阻塞或后台切换时对单次增量设置上限，并限制追赶步数，避免“螺旋死亡”。
* 输入立即结算；不等待下一次视觉刷新。

### 视觉层：VSYNC 驱动、状态改变后短动画

* 棋盘数据、下一块、分数、行数只在实际规则状态改变时发布新状态。
* 单格下落/横移启动 50–80ms 的 `Animatable` 插值；`Animatable` 自身只在动画期间请求帧。
* 普通锁定、直降和自动落地不使用全棋盘闪光；消行如需反馈，只绘制被清除行的低亮度局部效果。
* 分数事件只在得分变化时短暂出现，完成后自动停止动画。

### 当前实现的优先级

1. 已完成：移除普通落地和直降全棋盘覆盖层。
2. 已完成：`advanceTime` 仅在方块实际移动或锁定时请求游戏 UI revision，避免空帧重组。
3. 建议下一步：将 `delay(16)` 的常驻唤醒进一步替换为“下一个到期事件”唤醒；即使系统唤醒有误差，也始终用实际经过时间校正规则。
4. 不建议现在引入 Swappy/GL：当前为 Compose Canvas 2D 游戏，迁移成本高而收益有限。只有自定义 Surface/OpenGL/Vulkan 渲染器才适用。
5. 真机验证：在真实 Android 设备上使用 Macrobenchmark 和 Perfetto/Composition Tracing 测量 `frameDurationCpuMs`、`frameOverrunMs` 与重组范围；模拟器不能代表真实帧时间。

## 参考资料

[1] Android Developers, “Learn about rendering in game loops” — https://developer.android.com/games/develop/gameloops

[2] Android Developers, “Frame Pacing library” — https://developer.android.com/games/sdk/frame-pacing

[3] Glenn Fiedler, “Fix Your Timestep!” — https://gafferongames.com/post/fix_your_timestep/

[4] Android Developers, “Value-based animations” — https://developer.android.com/develop/ui/compose/animation/value-based

[5] Android Developers, “Quick guide to Animations in Compose” — https://developer.android.com/develop/ui/compose/animation/quick-guide

[6] Android Developers, “Practical performance problem solving in Jetpack Compose” — https://developer.android.com/codelabs/jetpack-compose-performance

[7] Android Developers Blog, “High refresh rate rendering on Android” — https://android-developers.googleblog.com/2020/04/high-refresh-rate-rendering-on-android.html

[8] TetrisWiki, “DAS” — https://tetris.wiki/DAS
