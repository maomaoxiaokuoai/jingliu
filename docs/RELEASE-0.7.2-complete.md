# 镜流 0.7.2 · 完整源码整合说明

## 本包包含什么

本包以 `Jingliu-Android-menu-icon-fix.zip` 完整工程为基础，将上一轮提供的全部 23 个生产代码/配置文件及 2 份历史日志合并，补齐未随单文件交付重复提供的 core、下载引擎、账号存储、任务管理、图标、资源、Gradle 配置、许可证和构建脚本。

版本配置：`0.7.2-glass-fix`、versionCode 9；applicationId 仍为 `com.luma.downloader`。
此次打包不再更改上一轮交付的生产代码：玻璃、按钮、开关、菜单、颗粒、输入框/搜索框、恢复默认和 NativeAppLauncher 文件均按原始字节合并。不是只压缩上一轮不完整的 glass3-work 目录。

已删除旧 `auth/WebLoginActivity.kt`，新 Manifest 不再声明该 Activity。官方 App 启动入口、包可见性声明和平台目标规则已一并包含。B站二维码和 Cookie 导入代码保留。

**打开官方 App 不等于完成 SDK/OAuth 授权。** 七个平台的开发者凭证、官方授权回调与完整认证接入仍未配置；不会因打开 App 就伪造登录成功。实网解析、最高画质、账号及平台能力的原有限制仍然存在。

## 包内目录

```text
Jingliu-Android/
  app/                          界面、账号、下载、原生资源
  core/                         解析模型、网络与传输核心
  branding/                     镜流水波图标与资源预览
  gradle/wrapper/                固定版本与官方校验配置
  docs/                         编译、范围及历史修复说明
  tests/                        历史与本次检查记录
  tools/                        结构/语法检查工具
  settings.gradle.kts
  build.gradle.kts
  gradlew / gradlew.bat
  setup-wrapper.bat / .ps1 / .sh
  build-debug.bat / .sh
  source-manifest.json           本包逐文件 SHA-256
```

## Android Studio 打开与编译

1. 解压到独立目录，如 `D:\Projects\Jingliu-Android`。不要只打开 app 子目录，也不要把旧项目的全部源码再次覆盖到新目录中，否则会恢复已移除的类。
2. 本包沿用 JDK 17、Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.20、compile/target SDK 35。使用你此前成功运行工程的本机工具配置即可。
3. 本包不包含预编译依赖，也未内置官方 `gradle-wrapper.jar`。新目录先运行 `setup-wrapper.bat`，它会下载官方 JAR 并校验哈希。也可把已成功使用的同版本官方 JAR 从旧工程复制到新工程的 `gradle/wrapper/gradle-wrapper.jar`，脚本仍会校验。
4. Android Studio → Open → 选择包含 settings.gradle.kts 的 `Jingliu-Android` 根目录，完成同步，然后运行 app。

PowerShell 中执行：

```powershell
.\gradlew.bat :app:assembleDebug --console=plain --stacktrace
```

该命令只构建；成功后用 Android Studio Run 安装启动。完整本机测试可另行执行：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain --stacktrace
```

APK 仅在实际构建成功后出现在 `app/build/outputs/apk/debug/app-debug.apk`。交付包内没有 APK。

保留 applicationId、沿用同一签名即可按通常更新流程安装；这次不需要为了改界面卸载应用。不要在排错时随意清除账号和下载数据。没有本机签名或真实用户数据迁移验收记录。

## 本次重新执行的检查

- 76 条核心 JVM 断言通过：合成平台数据、本机 HTTP 字节传输；不是平台联网验收。
- 181 条设置/动效策略断言通过：纯 Kotlin 策略，不是 Compose 渲染。
- 16 项菜单规则与定位检查通过：更新了旧版“始终不透明菜单”的过期预期，按新版“可采样时玻璃、无采样/关闭时实色”检查；几何检查保留。
- 89 项结构检查通过：目录、XML、资源引用、清单本地类、设置键与脚本语法；不是 Android 类型检查。
- 55 个 Kotlin/KTS 文件的语法树检查无错误；不是 Gradle 编译。
- 压缩包 CRC、逐文件 SHA-256、上一轮 25 个文件原始字节一致性、镜流与七个平台图标保留情况均检查。

本次日志位于 `tests/package-0.7.2/`。`tests/glass-fix/`、`tests/menu-fix/`、`tests/fix-001/` 等为原交付的历史记录，未拿来冒充本次执行。没有重复累加检查次数。

旧版 MenuRenderingTest 已明确改为无采样来源时的实色回退/交互测试，补充了空 HazeState；这是测试源码调整，尚未在设备执行，也不覆盖真实跨窗口模糊。

## 明确未验证的部分

当前环境没有可用 Android SDK 或完整 Gradle 依赖。本次没有执行 Android 全工程编译、输出 APK、运行模拟器、真机验收 Haze 模糊、验证所有控件或执行七个平台登录下载。

**完整源码整合不等于无报错成品。** 真实玻璃采样、开关/滑块效果、官方 App 跳转、Android 布局与下载运行结果，仍需你本机编译和设备检查；本包没有用网页截图代替安卓运行证据。

## 相对上一完整包的额外整理

除上一轮生产代码覆盖及移除 WebLoginActivity 外，本轮只同步过期的菜单测试、更新文档、记录检查结果并重建源文件校验清单。生产引擎、账号存储、下载 Worker、旧 length 编译修复与品牌图片未额外改动。
