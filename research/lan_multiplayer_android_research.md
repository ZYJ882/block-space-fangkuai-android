# Android 局域网联机调研记录

## 结论

方块空间的局域网双人对战采用 Android **Network Service Discovery（NSD / DNS-SD / mDNS）** 进行同一 Wi‑Fi 或热点内的房间发现，并用标准 **TCP Socket** 在已解析的主机地址与端口之间建立点对点会话。该组合无需外部服务器、账号、云端数据库或互联网连接。

| 领域 | 方案 | 原因 |
|---|---|---|
| 房间发现 | `NsdManager`，服务类型 `_blockspace._tcp.` | 官方 NSD 文档明确将其用于本地服务发现和多人游戏；注册服务会广播端口，发现端通过解析获得主机与端口。 |
| 实时通信 | 主机监听 `ServerSocket(0)`；客户端在解析出的地址与端口连接 | 由系统分配空闲端口，避免端口硬编码冲突；TCP 提供顺序、可靠的状态和攻击行同步。 |
| 发现可靠性 | 仅在房间页持有 `WifiManager.MulticastLock` | Wi‑Fi 通常会过滤未明确寻址的组播包；组播锁允许接收 mDNS 相关组播，但会增加耗电，因此页面离开或会话结束即释放。 |
| 生命周期 | 离开房间页、应用暂停或销毁时停止发现、注销服务并关闭 Socket | 官方 NSD 文档要求及时停止发现和注销服务，避免其他设备看到陈旧房间及持续耗电。 |
| 隐私与权限 | Android 35 使用 `INTERNET`、`ACCESS_NETWORK_STATE`、`CHANGE_WIFI_MULTICAST_STATE`；不扫描 IP 段、不上传数据 | 当前项目 targetSdk 35。官方 2026 本地网络权限说明指出 targetSdk 36 及以下仍通过 `INTERNET` 获得局域网访问；未来升级至 targetSdk 37 时需适配 `ACCESS_LOCAL_NETWORK` 运行时权限。 |

## 协议安全边界

联机层只接受长度受限的 UTF-8 行消息；消息包含版本、房间令牌、类型和有限数值字段。主机仅接受一个对手连接；客户端仅连接通过 NSD 解析出的服务。无任意地址扫描、无互联网回退、无远程执行、无文件传输、无聊天内容持久化。

会话消息使用主机权威的开局种子和开始时间。双方本地运行同一游戏规则；每次锁定、消行、暂停、结束和周期性心跳同步可见状态。主机关闭、读写失败或超时会将双方返回房间状态并给出断线提示。

## 资料

1. Android Developers, [Use network service discovery](https://developer.android.com/develop/connectivity/wifi/use-nsd), accessed 2026-08-21. 官方文档说明 NSD 使用 DNS-SD，提供服务注册、异步发现、解析主机/端口，以及应在生命周期中停止发现和注销服务。
2. Android Developers, [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission), updated 2026-07-13. 文档说明 LAN Socket 与 `NsdManager` 的本地网络权限影响，并列明 Android 17 / targetSdk 37 的未来权限迁移路径。
3. Android Developers, [WifiManager.MulticastLock](https://developer.android.com/reference/kotlin/android/net/wifi/WifiManager.MulticastLock), accessed 2026-08-21. API 参考说明该锁可让应用接收 Wi‑Fi 组播数据。
