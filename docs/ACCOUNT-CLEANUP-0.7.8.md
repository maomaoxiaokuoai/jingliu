# 镜流 0.7.8 · 账号入口精简版

本版基于 `Jingliu-Android-0.7.8-编译修复1-完整源码.zip`，只改三个生产文件。
完整包已保留上次 AndroidView 和 if/else 修复。没有修改 Gradle/Kotlin/Compose、包名、versionCode、图标、玻璃/动画、下载引擎、Cookie 仓库或任务存储。

## 账号页最终保留什么

- B站：独立的「B站扫码登录」，以及「内置浏览器登录 · 电脑版」。
- 其他原本支持内置网页登录的平台：只留「内置浏览器登录 · 电脑版」。
- 已保存账号的头像、昵称、状态、B站身份校验和「删除本机会话」保留。
- 删除整个「其他账号操作」分组，以及开放平台身份授权、仅打开 App、App 确认试验、浏览器接续试验、手机版和系统浏览器登录入口。
- 按“只保留”要求，账号页也不再展示手动 Cookie 导入/粘贴和旧说明入口。只移除界面入口，不删除此前保存的 Cookie。

YouTube 原本就禁止走此内置登录路径，本版没有擅自解除该限制，也不显示一个不可工作的电脑版扫码按钮。该平台显示说明，已有会话保留；本版不增加新的 Google/YouTube 登录能力。
网页是否提供二维码由平台决定，不将普通网址伪装成登录二维码。平台页面本身的密码/短信等选项不属于镜流的控件，本版不注入脚本修改它们。

## B站扫码弹窗

只有一个操作按钮：「保存二维码」（保存中/已保存状态会更新按钮文字）。
删除打开官方 App、App 确认、浏览器确认、重新申请和底部关闭按钮。
仍可使用系统返回键/手势或点击弹窗外关闭，关闭继续取消轮询和撤销未完成的二维码导出许可。

保存前直接展示相册与云备份风险说明。用户点击唯一保存按钮即确认保存，不再展开另一组“确认/取消”按钮。
只导出当前未过期的真实二维码像素，不导出账号、Cookie、网页 URL 或整张界面。已失效、已确认或正在校验时禁止导出；保存成功后当前二维码不可重复点保存。

API 29 及以上沿用现有相册保存；API 26–28 保留截图方式，保存按钮不可用。二维码窗口仍允许截图。过期后按返回关闭，再从账号页重新进入申请新码，不会自动刷新或无限申请。

## 内置浏览器

固定为电脑版 User-Agent 和宽网页布局，去掉切手机版与外部浏览器按钮。
网页请求离开当前受支持平台或唤起外部 App 时停止跳转并提示，不再弹“打开 App”。顶部保留刷新页面和关闭；底部保留本页截图保护控制及“我已登录，保存此页会话”。

原来的 `prepare`、`saveSession`、`onDestroy` 方法逐字节保留：平台域名过滤、已有 Cookie 注入、用户确认、B站身份校验、账号版本冲突检查、本机加密保存和清理临时 WebView 数据没有重写。
不要把入口精简理解为新的扫码、SDK 授权能力或修复了平台登录本身。

## 更新现有工程（推荐）

解压增量包，在含 `ApplyPatch.ps1` 的文件夹中打开 PowerShell：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\ApplyPatch.ps1 -ProjectPath "C:\Users\maomao\Downloads\Jingliu-Android"
```

路径换成当前实际工程目录。脚本先检查三个文件及补丁 SHA-256，全部匹配才备份并替换；本地改动不匹配会停止，不会强行覆盖。已是新版会跳过。
备份位于工程根目录 `.jingliu-account-backup-*`，不在 app/src 内。
脚本不改 `.idea`、`local.properties`、Python 路径、Wrapper 或 Gradle 缓存，不删除任何已有账号或下载数据。
PowerShell 脚本未在 Windows 运行；也可按补丁的 `patch-files/` 目录结构手动覆盖这三个文件。

## 完整源码

需要新目录时解压完整 ZIP，Android Studio 打开含 `settings.gradle.kts` 的 `Jingliu-Android` 根目录。
沿用 JDK 17/JBR 21、SDK 35、Build-Tools 35.0.0 和编译电脑的 Python 3.11.x。
源码仍不带二进制 `gradle-wrapper.jar`，可复制旧工程中同版本、已正常使用的官方文件，或运行原 `setup-wrapper.bat` 下载并校验。

```powershell
.\gradlew.bat :app:assembleDebug --console=plain --stacktrace
```

命令成功后点击 Android Studio 的 Run 安装和启动。APK 应位于 `app\build\outputs\apk\debug\app-debug.apk`。沿用原签名，不需要卸载或清空数据。

## 本次实际验证

1. 修改的三个完整 Kotlin 文件，在真实 Kotlin/JVM 编译器 + 原 core 代码 + 原 coroutine JAR + **明确的外部 Android/Compose 接口测试替身**下进行局部类型编译，退出码 0。不是 Compose/Android 全工程编译。
2. 执行原二维码安全策略的 27 项主机检查：过期、确认、取消、刷新后的旧 guard 等仍拒绝导出。不是 Android MediaStore 实测。
3. 19 项本次源码约束检查通过：入口删除、电脑版固定、单个保存按钮、返回关闭、命名 AndroidView 参数与身份保存校验等。静态约束不是界面录屏。
4. 对全包 180 个 Kotlin/KTS 文件做 PSI 语法检查，0 个语法节点错误；其中包含历史测试/测试替身，不能等同 180 个安卓模块编译成功。
5. 生产文件差异严格限定为这三个文件。其余业务、配置及图片资源与基线逐字节一致，完整 ZIP 校验 CRC 及逐文件 SHA-256。

最初源码约束脚本把账号页 SettingsLine 静态调用数误写为 5，实际为 4，已纠正并重跑；没有为迁就断言改生产逻辑。
本机环境为 Kotlin/JVM 1.9.0 / JDK 21，输出兼容 Java 17。本次没有 Android SDK，没有执行完整 Gradle 构建，没有 APK、真机界面/扫码/相册验证。
最新日志与可复现脚本在 `tests/account-cleanup/`。其他测试目录为历史记录，不累计成本轮验证。
