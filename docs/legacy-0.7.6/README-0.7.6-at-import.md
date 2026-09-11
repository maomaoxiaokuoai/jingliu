# 镜流 GlassFlow · 0.7.6 完整源码

**这是完整 Android 工程，不是增量补丁；不含 APK。** 本次以已交付的 0.7.5 完整 ZIP 为底包，合并 0.7.6 单文件修改，并补齐模型、引擎路由、服务配置、任务存储、ViewModel 和页面之间缺失的接线。不是把几个独立文件压缩后称作完整工程。

版本 `0.7.6-download-integration`，versionCode 13，包名 `com.luma.downloader`。Gradle/Kotlin/Compose/Haze/yt-dlp Android 依赖版本不升级。已有玻璃、颗粒、弹簧参数、图标、账号和下载文件不重置。

> **验证边界：**核心和接口的主机编译/测试、Kotlin 语法与源码资源检查不等于 Android 全工程编译。当前 Android 构建尝试在获取官方 Wrapper 时因 DNS 失败停止，未进入 Gradle/Compose 类型检查；没有 APK、真实设备或真实平台联网验收。本版修复源码中发现的下载问题，不保证你的 B站失败原因已经唯一确认或任何链接都成功。

## 先打开工程

解压到独立目录，例如 `D:\Projects\Jingliu-Android-0.7.6`。Android Studio → Open → 选择包含 **settings.gradle.kts** 的 `Jingliu-Android` 根目录，不要只打开 `app`、`auth-bridge` 或 `parse-video-service`。

沿用你之前能够使用的 **JDK 17 / JBR 21、SDK 35、Build-Tools 35.0.0**。工程固定 Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.20。不要为这次打包删除 Android Studio 缓存或卸载旧应用。

**本包不附二进制 `gradle-wrapper.jar`。** 将旧工程中已正常使用的同版本官方文件复制到新目录 `gradle/wrapper/gradle-wrapper.jar`，或者运行根目录 **setup-wrapper.bat** 下载并校验官方文件。保持校验，勿使用不明来源的 JAR。

在工程根目录 PowerShell 执行：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain --stacktrace
```

只有实际编译成功，才会生成：

```text
app\build\outputs\apk\debug\app-debug.apk
```

之后点击 Android Studio **Run** 安装。保持旧签名更新，不用清空 Cookie、账号、设置或下载文件。Profile 构建也保留：

```powershell
.\gradlew.bat :app:assembleProfile --console=plain --stacktrace
```

Profile 输出 `app\build\outputs\apk\profile\app-profile.apk`，它是本地性能测试构建，不是此包已提供的 APK。

## 本版功能接线

### 合集下载前选画质

勾选合集 → 下载 → **合集下载 · 选择画质** → 读取已选第一项实际格式 → 选择最高可用或实际返回档位 → 确认。

点击下载不立即创建任务；取消不新增任务。固定档位按 B站 qn 或分辨率语义保存，而不是把第一集的临时 format ID 套给其他集。每集开始前独立解析；固定档位不可用会提示，不静默降级。“首项大小”不是整个合集大小。

旧任务兼容缺省 `parserEngine=auto`。全局切换引擎不改变已创建任务；要改旧任务的引擎或画质，请重新解析并创建任务。

### B站媒体节点与脱敏诊断

保留平台返回的同一视频/音频轨道备用地址，优先标准 HTTPS；只对 B站媒体域的指定端口作有限放行，不随意拼接节点。失败时在同一质量候选内切换，不借此绕过账号权益。

下载仍使用本机当前平台 Cookie 仓库，按域和路径发送，不把 SESSDATA 发给任意 CDN。失败任务显示具体阶段，并提供 **复制脱敏诊断**。诊断只含受控错误代码、阶段、代码位置和是否能向 B站 API 发送会话的布尔值，不包含 Cookie 值、账号或签名链接。

这修正了旧代码丢失备用地址、过严端口规则和失败提示不具体等问题；没有复现你那一次真实请求，因此不要把“账号已验证但失败”全部归咎于 Cookie。

### 四种可选解析引擎

| 引擎 | 执行方式 | 额外要求 |
|---|---|---|
| 自动 · 本机原生优先 | 原生适配器，必要时回退 yt-dlp | 不依赖自建服务 |
| 原生 Kotlin | 手机直接访问平台/API/CDN | 当前平台能返回可解析内容 |
| yt-dlp · 本机通用 | Android 封装运行组件 | 平台规则与引擎兼容 |
| parse-video-py · 自建服务 | 你部署的接口返回媒体，手机自行下载 | 配置自己的服务地址 |

**备用服务不是默认公共解析站，也不是已把完整 Python 项目装进手机。** 只有主动选中才发送分享链接及其自带参数；不转发本机 B站或其他平台 Cookie。服务 Basic 账号单独加密保存。

`parse-video-service/` 包含本项目新增包装层、启动脚本、测试与上游固定版本依赖声明；不包含上游项目所有源文件、wheel 或离线运行环境，首次安装需要联网。普通原生/yt-dlp 功能不依赖此服务。当前服务适配不冒充支持全部上游平台；B站 p>1 明确拒绝，避免上游首P行为下载错内容。B站合集优先使用原生或 yt-dlp。

- [备用服务安装与配置](parse-video-service/README.md)
- [源码整合与本次验证](docs/PACKAGE-0.7.6.md)
- [下载修改细节](docs/FIX-007-download-engine.md)

## 完整目录

```text
Jingliu-Android/
  app/                    原有手机/平板 UI + 新合集弹窗/引擎选择/任务诊断
  core/                   原有网络/账号协议 + 新画质/候选节点/备用接口
  auth-bridge/            既有可选身份授权服务，默认不参与 Android 构建
  parse-video-service/    新增可选 Python 接口包装层及安装声明
  branding/               镜流图标原资源和预览
  docs/                   历史说明与本次说明
  tests/                  历史记录 + 本次主机测试记录
  tools/                  校验、语法与主机复现脚本
  gradle/wrapper/          官方 Wrapper 配置；无二进制 JAR
  settings.gradle.kts
  build.gradle.kts
  source-manifest.json     逐文件大小与 SHA-256（不含清单自身）
```

## 本次测试范围

当前日志见 `tests/package-0.7.6/`。新增 31 项下载/画质/备用协议检查、9 项实际任务存储/路由接线检查，以及 Python 包装层 9 项本机 HTTP 测试。原有核心、扫码、账号、可选授权服务和外观/菜单/覆盖层的主机回归也已执行。

这些使用合成响应、本机 HTTP 和明确标注的外部类型测试替身；不代表 Android Keystore、MediaStore、后台任务、FFmpeg、UI、平台账号和真实媒体下载都通过。此前单文件附带的“40 项下载检查”日志放在 `tests/fix-007/historical-download-repair.log`，不当成本轮可复现测试结果；本轮新增测试源码和记录对应 31 项。

许可、上游来源与其他平台限制见 `LICENSE`、`THIRD_PARTY_NOTICES.md` 及各服务 README。没有新增预置模拟账号/视频，也没有为了打包删除原代码里的能力边界。
