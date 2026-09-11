# FIX-005：JDK 工具链、二维码导出、账号连接语义

## 1. 截图中的根问题

`:auth-bridge:compileJava` 的 `NoToolchainAvailableException` / `ToolchainDownloadFailedException` 指向找不到匹配 JDK 17 的工具链。0.7.3 的 `kotlin { jvmToolchain(17) }` 并非“至少 Java17”；它指定精确 major 17。即便 Gradle 正用 JBR 21，也不能满足这个工具链请求。未配置下载仓库时 Gradle不会自动换一个版本。

“Project source sets cannot be resolved”导致 IDE 没有完整脚本类路径，`application`、`kotlin`、`implementation` 等红线可能连带出现。先恢复 Gradle Sync，不要逐条把正常 DSL 改坏。

修改：
- `settings.gradle.kts` 的 auth-bridge 无条件 include 改为 `-PincludeAuthBridge=true` 显式启用。
- `auth-bridge/build.gradle.kts` 不再强制查找独立 JDK 17；使用当前 Gradle JDK，Java release 17 / Kotlin JVM17 与 `-Xjdk-release=17`。
- core 也固定 Java release 17 与 Kotlin API 上限17；未更换 Kotlin/AGP/Compose/Haze 版本。
- app 版本提高到 0.7.4 / versionCode 11，包名不改。

这里的纯 JVM 输出级别不等于修改手机的 Android API 等级。Android Studio 与终端可能使用不同 JDK；前者看 Gradle JDK 设置，后者看 JAVA_HOME/PATH。

## 2. 二维码截图限制是本应用上版设置的

上版所有扫码框走 `GlassAlertDialog(secure=true)`，同窗口覆盖层因此对 Activity 加 FLAG_SECURE。新 `LoginQrDialog.kt` 只对二维码调用 `secure=false`。没有全局删除 FLAG_SECURE，CookieImportDialog 仍是 secure=true，且保留原窗口已有安全策略的恢复逻辑。

黑白 QR 使用不透明白底与 4 个模块 quiet zone，画面不包含 Cookie 明文。直接二维码新增 PNG 导出；官方 QR 网页没有原始码数据时只允许截图，不把 OAuth 页面链接重新编码成一个所谓“官方二维码”。

Android 29+ 使用 MediaStore.Images 保存自己创建的图片，不额外请求整个相册的读取权限。保存前确认、保存中检测过期/取消、IS_PENDING 写入并发布、失败尽力清理。旧系统使用截图路径，没有承诺旧系统也有同样的相册保存按钮。

图片保存成功后用户应自行删除，应用不会擅自删除用户相册已有内容。相册云备份可能同步二维码，界面会提示。系统杀死进程、MediaStore异常、其他安全策略仍需真机检验，不宣称已经完全事务化或永久可截图。

## 3. “打开 App”“OAuth”“下载 Cookie”不是同一件事

普通启动 Intent 不包含 OAuth 客户端配置、scope、state/PKCE 或回调；启动成功不能证明身份，更不能读其他 App 的私有 Cookie。不存在通用的 Android“授权所有第三方读取 Cookie”权限。

本次是把功能与界面描述对应起来，而不是补造授权：
- B站：原有真实扫码 → 平台返回会话 → 校验资料 → 本机加密存储。新增同机截图/保存引导。
- 其他平台下载：仍使用用户主动导入自己的浏览器 Cookie。没有新增通用自动获取下载 Cookie 的 SDK。
- 抖音/快手/TikTok 开放平台身份：保留此前可选服务路径，必须有开发者应用和部署配置。资料与 Cookie 分开保存；未配置给出明确说明。
- 打开 App 移到次级操作，并增加“只打开，不授权/不获取 Cookie”的确认说明。这个确认不是平台授权页。

账号页的说明不建议在聊天/问题反馈中发送 Cookie、账号密码、授权 code/token。开发者密钥不得写入 APK。

## 4. 本次执行的检查

| 项目 | 结果 | 日志 |
|---|---|---|
| 实际主机 core + 可选 bridge 编译 | 成功，JDK21/Kotlin1.9，API/字节码目标17 | core-bridge-compile.log |
| 新增账号语义、QR存活/导出/失效规则 | 27 项通过 | access-policy.log |
| 核心（合成数据和 localhost HTTP） | 76 条断言通过 | core.log |
| 扫码协议（无真实平台账号） | 20 项通过 | qr-protocol.log |
| 授权服务协议/localhost HTTP | 26 项通过 | bridge.log |
| 设置/动效策略 | 181 条通过 | policy.log |
| 菜单定位 / 覆盖层生命周期 | 16 / 11 项通过 | menu.log / overlay.log |
| 新修复源码约束与实际 class 版本检查 | 21 项通过；232 个本项目类 major=61 | source-contracts.log/json |
| 原采样/动效结构约束 | 18 项通过 | render-contract.log |
| XML/资源/设置/Manifest引用 | 89 项通过 | source.log |
| 全包 Kotlin/KTS 语法树 | 77 文件、0 语法错误 | syntax.log |
| Android全工程编译 | **未完成**，Wrapper 下载 DNS 失败 | android-build-attempt.log |
| Android仪器测试、真实相册、截图、账号 | **未执行** | 仅有测试源码 |

日志均在 `tests/fix-005/`。主机编译 API 上限17验证了可选服务不依赖新JDK API，不等于 Gradle插件或Android依赖已通过语义编译。语法检查不解析 Android/Compose 类型。

本次尝试的 `sh ./gradlew :app:assembleDebug :app:testDebugUnitTest :core:test` 退出1，两个官方 Wrapper 源均 DNS 失败。未生成 APK。新增设备测试 `QrExportDeviceTest.kt` 和 `QrCaptureWindowTest.kt` 未在本环境执行。

## 5. 设备复核

1. 使用 JDK17 或 JBR21 同步默认项目，确认只载入 app/core，再构建 app。
2. 仅需要部署服务时用 `-PincludeAuthBridge=true` 运行桥接测试/分发任务。
3. B站二维码截图不再被本应用阻止；Cookie编辑框仍然阻止截图，返回后保护正常恢复。
4. 活码保存PNG→官方App相册识别；过期、刷新、关闭、登录完成时不保存旧码。二维码图片会写入相册，请测试结束后删除。
5. 每个平台的仅启动入口不显示假授权成功；未配置的开放平台授权不发起假登录；登录会话来自真实扫码结果或本人Cookie导入。
6. 在真实手机/平板上核对相册写入、横竖屏、后台进程、提示与键盘等。企业管控/别的App禁止截图不由本包解除。

## 参考（2026-09-09）

- Gradle工具链与release区别：https://docs.gradle.org/current/userguide/toolchains.html
- Android构建JDK选择：https://developer.android.com/build/jdks
- FLAG_SECURE：https://developer.android.com/security/fraud-prevention/activities
- Android应用沙盒：https://developer.android.com/privacy-and-security/security-tips
- 抖音总体授权说明：https://open.douyin.com/platform/resource/docs/develop/permission/overall-permission
