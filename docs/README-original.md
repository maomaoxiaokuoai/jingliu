> 历史基础版本文档，最新改动见 FIX-002-menu-brand.md。

# 拾流 · Shiliu Android 源码工程

**版本：0.7.0-source。交付的是完整源码目录，不是经过端到端验收的成品或已编译 APK。**

已补齐此前缺失的解析页、下载/文件页、设置页、账号页、二维码弹窗、ViewModel、任务账本、后台调度、Manifest、资源和构建配置。没有预置演示任务、模拟下载计时器、示例作品或虚构账号。本包只包含 Android 手机/平板工程，不包含旧 HTML、桌面或 SwiftUI 参照项目。

## 1. 在 Android Studio 中编译

建议解压到短路径，例如 `D:\Projects\Shiliu-Android`。不要把本包覆盖到旧的 LUMA 工程里；单独打开新的工程目录。

1. **先运行根目录 `setup-wrapper.bat`**（Windows；macOS/Linux 执行 `sh setup-wrapper.sh`）。本环境无法下载 Gradle Wrapper 二进制，因此源码包不内置 `gradle-wrapper.jar`；脚本从 Gradle 官方 GitHub 仓库获取并强制校验 SHA-256。已经存在且校验通过时不重新下载。没有隐藏的第三方镜像、关闭 TLS 验证或跳过哈希校验的逻辑。
2. Android Studio → **Open** → 选择本目录，即能看到 `settings.gradle.kts`、`app`、`core` 的目录。**不要只打开 `app` 目录。**
3. 使用 **JDK 17** 作为 Gradle JDK；在 SDK Manager 安装 **Android SDK Platform 35、Build-Tools 35.0.0、Platform-Tools**。`local.properties` 由 Android Studio 生成，不使用别人的 SDK 路径。
4. 等待 Gradle Sync 完成，选择 `app` 配置和 Android 手机/平板或模拟器，点击 Run。

固定工具链：AGP **8.9.2**；Gradle **8.11.1**；Kotlin/Compose Compiler **2.1.20**；Compose BOM **2025.06.01**；minSdk **26**；compile/targetSdk **35**。核心和应用的 JVM 字节码目标均为 17。

AGP 官方兼容表：https://developer.android.com/build/releases/agp-8-9-0-release-notes

### Windows PowerShell 编译命令

```powershell
cd D:\Projects\Shiliu-Android
.\setup-wrapper.bat
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug --stacktrace --console=plain
```

成功后 APK 的**预期生成位置**：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以执行根目录 `build-debug.bat`；它会运行核心测试和 Debug 构建。完整静态检查额外执行：

```powershell
.\gradlew.bat :app:lintDebug --stacktrace
```

### macOS / Linux

```sh
sh setup-wrapper.sh
sh gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

首次编译需要联网获取工具链和 Maven 依赖，不需要手动安装 Python、FFmpeg 或运行第三方解析服务器。下载器运行时所需的原生组件来自声明的 Android 库依赖，**它们不是全部内置在源码 ZIP 中**。

### Wrapper 下载失败时

官方文件：https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar

官方 SHA-256：

```text
2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046
```

可以在能够访问该地址的网络下载官方文件，再运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-wrapper.ps1 -Source C:\Downloads\gradle-wrapper.jar
```

Unix 等效命令：`sh setup-wrapper.sh /path/to/gradle-wrapper.jar`。脚本仍会校验，错误的 JAR 不会被执行。不要用网页 HTML 改名成 `.jar`，也不要安装来历不明的构建程序。

校验值来源：https://gradle.org/release-checksums/ 。Gradle 分发 ZIP 的校验值已写入 `gradle/wrapper/gradle-wrapper.properties`。

## 2. 实际源码包含什么

- `core/`：纯 Kotlin 数据模型、JSON、安全边界、Cookie 域名隔离、国内平台公开页面/接口结构提取、格式选择、图集/合集模型、HTTP 续传。
- `app/.../ui/`：Compose 解析、下载、文件、设置、账号界面；Haze 操作层模糊、弹簧页面和控件；手机单列和横向宽屏平板布局。
- `app/.../data/`：设置、真实任务账本、ViewModel；不会创建演示任务。任务状态写入原子文件，失败时保留记录。
- `app/.../auth/`：Keystore 加密会话、B站真实扫码/身份校验/有条件续期，独立进程官方网页登录，用户主动导入 Cookie。其他平台的 Cookie 保存**不冒充已验证登录**。
- `app/.../engine/`：`youtubedl-android 0.18.1` 封装的 yt-dlp/Python/FFmpeg；逐轨下载并无损合并，不依赖外部在线解析站。
- `app/.../download/`：WorkManager、实际字节进度/速度、并发、Wi-Fi 约束、暂停与续传、失败重试、MediaStore 导出、停止写入后删除任务及文件。
- `app/src/main/res/`：原有六个平台图标，以及用户提供的 X 图标；应用名“拾流”，启动图标沿用之前工程的矢量资源，并非本次重新设计的标识。
- `core/src/test/`、`app/src/test/`：合成数据测试，仅测试目录存在测试作品/测试 Cookie，生产程序不会加载它们。
- `docs/`：验收状态、架构、隐私、编译排错与测试记录。`.github/workflows/android.yml` 是可选构建脚本，**本次没有执行这个 CI**。

## 3. 平台能力边界

源码是真实请求和文件操作，不等于各平台已经实测全部可用。详细矩阵见 `docs/PLATFORM_STATUS.md`。

国内平台代码不配置应用级代理，不把链接/Cookie 发往第三方解析站。手机系统 VPN、运营商线路和平台风控不由该设置控制，**本次没有中国大陆手机网络直连验收结果**。海外平台需要手机网络能访问相应站点。

“最高画质”是当前会话实际返回且有权访问的最高格式；不是绕过会员/地区/版权/DRM。大小已知时显示数值，否则基于码率和时长估算或显示未知。互动数据和粉丝量缺失时为“未提供”，不填 0。正在直播的内容不作为点播下载。

图集和合集可单选/多选；全选按钮从未选或部分选中切换到全选，再点击清空。合集按 100 项分段加载，全选只作用于已经加载的条目。默认不代替用户勾选未见过的项目。

官方网页登录有平台域名限制，不向页面注入脚本或截取密码；不把 Google OAuth 登录嵌入 WebView。其他平台可能因网页验证、第三方身份跳转或 WebView 限制而需要外部浏览器登录后导入自己的 Cookie。

## 4. 验证状态（不要误读）

本次真正执行：核心 JVM 编译及 **76 条断言**（合成平台响应 + 本机 HTTP 服务器的真实传输）；设置/动效策略 **175 条断言**；Kotlin/KTS 语法检查；资源/XML/本地引用/ZIP 完整性检查。

**没有完成** Android Gradle 全工程类型检查、APK 构建、Android Studio 运行、真实平台七站下载、B站账号续期、真机 MediaStore/通知/后台任务验收。构建尝试在获取官方 Wrapper 时因 DNS 失败停止，未到 Kotlin Android 编译阶段。详见 `docs/TESTING.md` 和原始日志。

包内没有 APK，也不使用“无报错成品”“全平台一定成功”的描述。请先在本机编译，再用少量自有/获授权的链接验证；不要一开始批量提交数百项。

## 5. 安装 / 更新提醒

包名保留 `com.luma.downloader`。旧演示应用可能签名不同，此时安装会提示签名冲突；确认旧应用没有需要保留的文件后再卸载，或使用你自己的签名持续更新。卸载 Android 8–9 的应用会连同应用专属下载目录一起移除，先分享/复制需要保留的文件。

Android 10+ 文件保存至 `Download/拾流`；Android 8–9 使用应用专属目录，通过文件页打开或分享。删除按钮先弹出确认，确认后会停止下载并删除片段与已导出文件；取消不会发起删除。手动改动目录、清除数据或卸载后，应用可能不再持有旧文件的所有权/账本。

许可证：本项目以 GPL-3.0-or-later 提供，依赖与素材信息见 `THIRD_PARTY_NOTICES.md`。平台图标是用户提供的品牌素材，不代表平台合作、授权背书或官方客户端。
