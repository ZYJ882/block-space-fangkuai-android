# v1.19.0 外部更新审查与合并记录

## 审查范围

本次升级来源于外部提供的 Android 项目归档。审查首先在未执行归档内容的前提下检查其路径、链接类型、构建配置、Android 清单、依赖、源码与辅助脚本；随后仅对排除 Git 元数据、Gradle 缓存和预构建 APK 的隔离源码副本进行构建。

| 审查维度 | 结论 |
|---|---|
| 归档路径与链接 | 未发现绝对路径、路径穿越、软链接或硬链接。 |
| Git 与构建缓存 | 归档中存在 `.git` 与 `.gradle`，均未导入或执行。 |
| 预构建 APK | 归档中存在历史 APK，未被用作本次交付或验证依据。 |
| Android 权限与组件 | 仅有标准启动 Activity；未声明运行时权限、服务、接收器或内容提供器。 |
| 依赖与仓库 | 仅使用 Google、Maven Central、Gradle Plugin Portal 以及 AndroidX、Compose、JUnit。 |
| 运行时网络与动态加载 | 未发现网络客户端、WebView、动态代码加载、反射加载、外部进程执行或分析 SDK。 |
| Gradle Wrapper | Wrapper JAR 与已验证项目副本逐字节一致。 |

## 合并的性能优化

v1.19.0 将方块形状遍历优化合并进正式源码。碰撞判断、方块锁定和 T-Spin 角点判定直接遍历 `PieceLibrary` 形状坐标，避免这些规则路径创建临时 `Block` 对象。棋盘 Canvas 在同一帧中使用短列表缓冲顺序绘制落点虚影和当前方块，减少临时列表分配。该修改不改变方块形状、SRS 墙踢、计分、随机模式、锁定延迟或控制规则。

## 修复的上游编译问题

外部更新中包含一个未调用却试图修改不可变 `Block` 属性的对象池，并且缺少一个 `Block` 导入，且碰撞检测使用了未限定作用域的棋盘常量。v1.19.0 未采用这段无效对象池；其余优化在限定常量作用域和补齐导入后完成合并。另修正了外部 `.gitignore` 中误写入的 Markdown 围栏，并增加了本地签名与环境文件忽略规则。

## 验证要求

发布前必须运行：

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug
```

APK 必须使用 `apksigner` 校验 Android Debug v2 签名，并使用 `aapt` 核对包名、版本号和版本名称。
