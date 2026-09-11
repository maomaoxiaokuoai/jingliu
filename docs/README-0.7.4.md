# 镜流 GlassFlow · 0.7.4 构建与扫码使用修复

**完整源码，不含 APK。** 基于 0.7.3 修改 Gradle 模块加载、二维码截图/保存和账号入口说明。没有重写液态玻璃或下载引擎，没有清除设置、Cookie、账号或下载文件。

[本次修复与验证](docs/FIX-005-build-qr-access.md) · [登录方式说明](docs/LOGIN-METHODS.md) · [可选开放平台授权服务](auth-bridge/README.md)

## 修复截图中的 Gradle Sync

原先 `auth-bridge` 虽然称作“可选服务”，却无条件加入根工程，并使用 `jvmToolchain(17)` 强制寻找本机 JDK 17。截图中的 `NoToolchainAvailableException` 是这个精确工具链找不到，不是每一个 `implementation`、`application` 都要重新编写。

新版默认只加载 `app` 和 `core`。可选服务仅在 `-PincludeAuthBridge=true` 时加载；同时移除其精确 JDK 17 查找，使用运行 Gradle 的 JDK，将 Java 与 Kotlin 的输出兼容级别统一为 17。

### Android Studio

1. 打开包含 `settings.gradle.kts` 的 `Jingliu-Android` 根目录，不是 `auth-bridge` 或单独的 `app`。
2. File → Settings → Build, Execution, Deployment → Build Tools → Gradle，选择 **JDK 17 或 JBR/JDK 21**。不要把 JDK 路径指到只有 Java 运行器而无编译器的目录。保持本包的 Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.20、SDK 35，不需要为此升级所有依赖。
3. 点击 Try Again / Sync Project with Gradle Files。同步成功后运行 `app`。
4. 新目录未包含二进制 Wrapper JAR。可复制旧工程里已验证可用的同版本官方 `gradle/wrapper/gradle-wrapper.jar`，或运行 `setup-wrapper.bat` 下载并校验官方文件。不要关闭校验，也不要删用户数据解决同步问题。

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :core:test --console=plain --stacktrace
```

**默认命令不包含 `:auth-bridge:test`。** 只有需要构建自己部署的身份授权服务时才用：

```powershell
.\gradlew.bat -PincludeAuthBridge=true :auth-bridge:test :auth-bridge:installDist --console=plain --stacktrace
```

只编译手机应用，不必安装/运行服务端。默认不导入的 `auth-bridge/build.gradle.kts` 不属于当前 IDE 的 Gradle 模型；要编辑该服务的完整 Gradle 模型，再启用属性并重新同步。

## 二维码截图与保存

- 登录二维码窗口改为允许截图；Cookie 输入框仍保留 FLAG_SECURE。
- Android 10 / API 29 及以上，B站和其他真正返回二维码数据的路径新增“保存二维码”，确认后仅将黑白二维码 PNG 写入 `Pictures/镜流/登录二维码`，不保存账号资料、Cookie、token 或整张屏幕。
- 旧系统可以直接截图。平台返回的是官方扫码网页时，也使用截图，不把网页网址伪装为二维码。
- 只允许保存尚未到期、未完成、未取消的二维码；关闭、刷新、过期或确认会取消对应保存。媒体写入失败会尝试删除本次创建的未完成图片。
- 登录二维码是短期凭据。保存前显示隐私确认，提示相册云备份风险；请勿分享给他人，登录后手动删除二维码图片。
- 本修改不绕过工作资料、设备管理员或其他 App 的安全限制。

同机使用：申请二维码 → 保存或截图 → 打开 B站 → 在“扫一扫”中尝试从相册识别 → 确认 → 返回镜流等待身份与会话校验。相册扫码以当前官方 App 实际支持为准，不支持时用另一台设备扫描。如果 Android 杀死了后台镜流，需要重新申请二维码。

## 为什么打开 App 没有“给 Cookie 授权”弹窗

`NativeAppLauncher` 本来就是普通启动入口，不是 SDK 授权。新界面将它下移到“其他账号操作”，命名为“打开 XX App · 仅启动”，点开前明确提示用途，不再让它看起来像连接下载账号。

- **下载会话：** B站使用现有扫码登录路径，平台成功返回会话后校验并加密保存；各平台也保留用户主动导入自己的 Cookie。
- **公开身份授权：** 抖音、快手、TikTok 保留 0.7.3 的可选开放平台授权桥。没有开发者应用、HTTPS 服务与回调配置时显示未配置；即使完成，也不会返回浏览器 Cookie 或自动成为下载会话。
- **仅打开 App：** 不读取另一个 App 的私有文件，不伪造系统授权弹窗，不回写已登录状态。

除已有 B站扫码路径外，本包**没有新增其余六个平台自动获取下载 Cookie 的 SDK**。真实功能缺口没有通过自制“同意授权”对话框掩盖。普通下载不需要先部署身份授权服务。

## 升级与测试边界

包名仍为 `com.luma.downloader`，versionCode 为 11。保持原签名更新，不需要卸载。前版玻璃/动画修复、镜流图标、平台图标、MediaRuntime 的 length 修复原样保留。

实际执行：27 项账号/二维码导出策略、76 条核心断言、20 项扫码协议、26 项本机授权服务测试、181 条设置/动效断言、16 项菜单与11项覆盖层规则检查；77 个 Kotlin/KTS 文件语法检查，0 个语法错误节点。**不把这些混记成 Android 运行成功。** 核心与可选服务用 JDK 21/Kotlin 1.9.0、`-Xjdk-release=17` 在主机编译并测试，不是项目正式 Kotlin 2.1.20 的 Android 构建。

当前环境获取官方 Wrapper 仍发生 DNS 错误，未进入 Gradle 依赖解析和 Android 编译，没有 APK、Android 设备截图/相册写入实测或真实平台登录结果。仪器测试源码已附，但未执行。完整日志见 `tests/fix-005/`；其他测试目录属于历史版本记录。
