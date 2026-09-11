# 0.7.5 完整源码打包记录

## 组成与范围

底包为 0.7.4 完整工程，SHA-256：

`46f30145a1c74a10b6a6e57e7d28c3d62baca454d927b287c5062ae4db11bfcb`

合并当前可用的 0.7.5 附件共 **24 个文件**，其中 **22 个生产源码/配置文件**、2 份历史日志。所有合并文件逐字节匹配原附件；未修改的生产源码、图标和资源保留底包字节。没有在打包时重写功能或改变动效参数。

完整工程包含 app、core、可选 auth-bridge、Gradle 配置和启动脚本、Android 清单、全部既有资源、测试源码与历史记录。新增 profile 构建和 FrameMonitor 等文件已经成组包含，不需要手工逐个合并。

本次主要新增文件是 README、本文、打包验证记录及 build-profile / verify-package075-host 启动脚本。`source-manifest.json` 已重新生成。原 0.7.4 README 保存在 `docs/README-0.7.4.md`。

## 本次实际执行

| 范围 | 结果 |
|---|---|
| 底包 ZIP CRC、底包自带逐文件清单 | 通过 |
| 24 个 0.7.5 覆盖文件逐字节核验 | 通过 |
| 未修改生产源码、图标和资源一致性 | 通过 |
| 核心 Kotlin/JVM 测试 | 76 条断言通过 |
| 扫码协议 | 20 项通过 |
| 账号访问和 QR 导出策略 | 27 项通过 |
| 可选身份授权服务 | 26 项通过 |
| 设置与动效策略 | 181 条断言通过 |
| 菜单定位 | 16 项通过 |
| 覆盖层生命周期策略 | 11 项通过 |
| 原工具源码、XML 和资源引用检查 | 89 项通过 |
| Kotlin PSI 语法检查 | 83 个 Kotlin/KTS 文件，0 个语法错误节点 |

证据文件位于同包 `tests/package-0.7.5/`。核心/授权测试使用合成响应或 localhost HTTP，不是平台在线测试；策略检查不绘制 UI，不提供帧率结论。编译器为 Kotlin/JVM 1.9.0、`-Xjdk-release=17`，运行环境为 JDK 21，与正式 Android Kotlin 2.1.20 构建不同。

有本地 JDK/Kotlin 编译器的 Linux/macOS 环境，可运行：

```bash
bash tools/verify-package075-host.sh
```

测试 JAR 创建于临时目录，结束清理，不写入应用或源码 ZIP。原 `tools/verify-fix004-host.sh`、`tools/verify-fix005-host.sh` 是历史版本脚本，不能用它们的旧源码约束推断本版性能实现错误；本包以 `verify-package075-host.sh` 为当前主机复现入口。

## 历史日志不混记

当前附件没有提供原 `PerformancePolicyChecks.kt`、`PerformancePolicyTest.kt`、`RetainedSceneDeviceTest.kt` 测试源码；本包不伪造这几份文件，也不把上一轮 25 项/86 文件日志当成本次重跑的结果。完整的生产 app/core 代码已合并，这几份缺失的是原测试源码，不是运行时业务依赖。

## 未执行与未实现的边界

本次没有执行 Android Gradle 依赖解析/完整编译、安装 APK、真机/平板性能对比，以及真实平台授权或下载验证。没有 Android SDK/Gradle 的完整构建环境；本包不附 APK，不保证编译后所有平台可用。

上一条需求里的 parse-video-py 下载引擎切换、合集下载前新增画质选择、B站已登录但下载失败的针对性修复，没有在本次打包时新增。现有引擎、会话与下载代码按本版本原样保留。B站 App/浏览器接续登录路径仍需设备验证；不把跳转成功当成获取 Cookie。

## 编译手机应用

解压后打开 `Jingliu-Android` 根目录，沿用你已成功使用的 JDK 17/JBR21、SDK35。默认不导入可选 auth-bridge，不需要为编译 app 部署授权服务器。

新目录缺少 `gradle-wrapper.jar`，运行 `setup-wrapper.bat` 或复制旧工程同版本官方 JAR 到 `gradle/wrapper/`；校验继续保留。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleProfile --console=plain --stacktrace
```

成功时输出：

```text
app\build\outputs\apk\debug\app-debug.apk
app\build\outputs\apk\profile\app-profile.apk
```

保持同一签名更新，保留原 local.properties / 正式私有配置在自己的机器上，不上传 Cookie、密钥或签名文件。
