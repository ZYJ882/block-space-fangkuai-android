# 方块空间 / Block Space

**方块空间 / Block Space** 是一个使用 **Kotlin + Jetpack Compose** 实现的 Android 原生单机俄罗斯方块应用。项目采用完整的 Gradle Wrapper，能够直接导入 Android Studio；同时随交付提供已构建并验证的调试版 APK。

> 应用包名：`com.manus.tetris`  
> 当前版本：`1.2.0`  
> 最低 Android 版本：Android 8.0（API 26）

## 已实现功能

| 模块 | 内容 |
|---|---|
| 经典玩法 | 七种四格方块、随机生成、碰撞检测、固定入栈与满行消除。 |
| 操作 | 左移、右移、顺时针旋转、软降、硬降、暂停/继续与重新开始。 |
| 游戏规则 | 随消行数量累积得分；每消除 10 行提升一级，自动下落速度随等级加快。 |
| 游戏信息 | 实时展示得分、等级、消行数、**未来三个方块**与落点虚影。 |
| 界面 | 蓝色竖屏游戏厅布局，包含中央棋盘、侧边信息卡、右侧未来方块栏和高对比度面板。 |
| 触控操作 | 大尺寸圆形左移、软降、旋转、右移与直落按钮，便于单手快速操作。 |
| 启动体验 | 打开应用后先展示中英双语开始界面；点击“开始游戏”后，方块才会开始下落。 |
| 状态管理 | 游戏结束覆盖层、暂停覆盖层与一键重新开始。 |
| 应用图标 | 原创霓虹 T 形方块启动图标，已提供完整 Android 多密度资源。 |

## 项目结构

```text
TetrisNative/
├── app/
│   └── src/main/
│       ├── java/com/manus/tetris/
│       │   ├── MainActivity.kt          # 应用入口
│       │   ├── TetrisApp.kt             # Compose 界面与游戏循环
│       │   ├── game/TetrisGame.kt       # 玩法规则与状态机
│       │   └── ui/theme/                # Material 3 主题
│       ├── res/values/themes.xml
│       └── AndroidManifest.xml
├── gradle/
├── gradlew / gradlew.bat                # Gradle Wrapper
├── build.gradle.kts
└── settings.gradle.kts
```

## 在 Android Studio 中运行

使用 Android Studio 选择 **Open**，打开本项目根目录 `TetrisNative`。等待 Gradle 同步完成后，选择真实 Android 设备或模拟器，点击运行按钮即可。

| 配置项 | 设定 |
|---|---|
| 开发语言 | Kotlin |
| UI 框架 | Jetpack Compose / Material 3 |
| `compileSdk` / `targetSdk` | 35 / 35 |
| `minSdk` | 26 |
| Gradle Wrapper | 8.11.1 |
| Android Gradle Plugin | 8.7.3 |

## 命令行构建

本机已经使用下列命令成功构建调试 APK：

```bash
./gradlew assembleDebug
```

构建后的文件位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装 APK

将交付的 `TetrisNative-debug.apk` 传到 Android 设备后打开安装。若系统提示来源未知，请仅对您使用的文件管理器或浏览器授予一次“允许安装未知应用”权限。此文件为 **Debug 签名** 安装包，适合测试、侧载和内部体验；若要发布到应用商店，请在 Android Studio 中配置您自己的正式签名密钥并构建 Release 版本。

## 已完成验证

| 验证项 | 结果 |
|---|---|
| Gradle 调试构建 | 通过 |
| APK 输出 | `app-debug.apk` 已生成 |
| APK 签名 | Android Debug 签名校验通过 |
| 包名 | `com.manus.tetris` |
| 应用名称 | 方块空间 |
| APK SHA-256 | `f186ab94df6d6887384669c2c26d21ac8a83d30d4710d61c63e8f9c3530f555e` |

## 操作说明

| 按钮 | 效果 |
|---|---|
| 左移 / 右移 | 将当前方块向对应方向平移一格。 |
| 旋转 | 将当前方块顺时针旋转，并尝试进行基础墙踢修正。 |
| 下落 | 当前方块向下移动一格，并增加少量软降分。 |
| 直落 | 当前方块直接落至可放置的最低位置并固定。 |
| 暂停 | 暂停或恢复自动下落。 |
| 重开 | 清空棋盘和得分，开启新的对局。 |

