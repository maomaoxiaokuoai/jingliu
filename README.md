# 镜流 GlassFlow 0.8.1 · 液态玻璃控件完整整合源码

**完整 Android 开发源码，不是补丁，不含 APK。** 以 `0.8.0-进度命名修复` 完整工程为底包，合并上一轮提供的 8 个液态玻璃文件，并补齐依赖的光学策略、设置参数、控件入口、背景采样关系及设备测试的源类型。不是把旧 ZIP 改名。

> **当前没有 Android 整工程编译、AGSL 真机运行或 iQOO Z8 帧率验收。** 已完成的主机策略、资源、语法、调用参数名和 ZIP 校验不能代替这些验收。实际构建在获取官方 Wrapper 时 DNS 失败，Gradle 没有进入 Android 编译。日志见 `tests/package-0.8.1/`。

## 1. 打开项目

解压后在 Android Studio 打开 **Jingliu-Android 根目录**，即包含 `settings.gradle.kts` 的那层，不是单独的 app。

沿用之前可用的 JDK 17 / JBR 21、SDK 35、Build-Tools 35.0.0 和电脑上的 **Python 3.11.x**。应用使用原本的 Chaquopy 本地解析方案；Python 是编译电脑的条件，手机无需单独安装 Python 或运行解析服务器。

第一次在新目录打开时：

1. 本包不附 `gradle-wrapper.jar`。从你已成功构建的旧工程复制同版本官方 JAR 到 `gradle/wrapper/`，或者运行 `setup-wrapper.bat` 获取并校验官方文件。
2. 让 Android Studio 为本机生成 `local.properties`；不要复制别人电脑的 SDK 路径。
3. Python 自动识别失败时，复制 `python-build.properties.example` 为 `python-build.properties`，填写自己 Python 3.11 的绝对路径。
4. Sync 后运行 app。不要为更新删除全局 Gradle 缓存、卸载镜流或清空 Cookie。

在工程根目录执行：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain --stacktrace
```

成功后的 APK：`app\build\outputs\apk\debug\app-debug.apk`。

性能对照版本仍保留：

```powershell
.\gradlew.bat :app:assembleProfile --console=plain --stacktrace
```

成功后的 APK：`app\build\outputs\apk\profile\app-profile.apk`。Profile 使用原有本机 debug 签名用于测试，不是正式发布签名。

版本为 `0.8.1-liquid-optics`，versionCode=18，applicationId 仍是 `com.luma.downloader`。应保留原签名更新；本次代码不迁移或删除账号、下载文件、用户的玻璃与动画偏好。

## 2. 这次打包包含什么

- app、core、原 Python 本地解析适配、原可选 auth-bridge、资源、品牌图标、构建脚本和测试源码。
- 上一轮的 GlassSource、GlassOpticalLayer、LiquidShader、GlassSurface、GlassWidgets、LiquidControls、ShiliuApp 与 SettingsScreen。
- 补齐 LensPolicy / LensPlan / OpticalTier、镜片边缘宽度与色散设置、光学能力提示，以及设置恢复兼容。
- 所有应用自有按钮、图标按钮、单选/图库勾选、开关/滑块、进度指示、菜单/确认框、输入/搜索、底栏、平板导航、浏览器/预览操作栏统一使用新玻璃组件。
- 原有浏览器自动保存 Cookie、过期/在线检查、相册二维码保存、下载/续传与实际引擎选择保留。pageLoadProgress 命名修复也保留。

**网页里面的控件、系统键盘、系统文件选择器、二维码像素及视频画面不被镜片变形。** 播放器内部原生 PlayerView 控制器仍属于播放器组件；应用自己的外围导航和操作按钮使用新材质。

## 3. 新材质与平台回退

本包不是直接复制 Kyant0 或 QWEA0 的完整库，也不声称调用了苹果系统玻璃。它是镜流自有 Compose 渲染层：共享 GraphicsLayer 背景记录，按控件局部坐标重放，RenderEffect 模糊，Android 13+ 使用 AGSL 圆角镜片、边缘色散和饱和度，最后绘制清晰文字/图标。

- Android 13+ 且硬件绘制可用：清透/标准材质可启用镜片。
- Android 12：模糊与颜色处理回退。
- 更旧设备、软件画布、减少透明度或用户关闭玻璃：可读实色回退。
- 着色器初始化失败：会记录不含账号数据的错误类型，并尝试模糊/实色，不将初始化成功与否伪装为设备兼容认证。
- API/材质不适用的光学参数有明确说明，不再做无效果的可调数值。

没有增加长驻定时器强制刷新玻璃；持续转动的进度环只在相应忙碌状态中使用。更多玻璃层仍会产生真实 GPU 和离屏缓冲成本，**没有稳定 120fps 或苹果原生视觉完全一致的证据**。

## 4. 采样结构

背景 → 分组材质（不含子控件）→ 按钮/输入等小控件 → 页面输出 → 导航和同窗口覆盖层。

分组只把材质输出给内部控件，不把内部文字/按钮重新采样进自身背景。页面内的导航和控件不读取正在记录的页面纹理；外置菜单有独立的安全来源引用。切页、返回和菜单形变时传递绘制阶段的运动读数，以刷新采样坐标，而不是每帧重建业务列表。

这套关系已做源码约束检查，**尚未用 GPU 帧捕获证明所有设备都不会闪烁或产生反馈循环**。

## 5. 版本和依赖

Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.20、Compose BOM 2025.06.01、WebKit 1.12.1、Chaquopy 17.0.0、Python 3.11、Media3 1.7.1 与旧版一致。本次移除了 app 对 Haze 的活动依赖；`LocalContentHaze` 等名称仅为旧调用点兼容，实际类型改为 GlassSource。

不打包机器路径、签名私钥、真实 Cookie、外部字体或已编译测试 JAR。第三方依赖仍按 Gradle/pip 配置在编译时取得，本包不是完整的离线依赖仓库。

[本次验证范围](docs/TESTING-0.8.1.md) · [控件覆盖清单](docs/GLASS-COVERAGE-0.8.1.md) · [来源与许可](THIRD_PARTY_NOTICES.md)

## 6. GitHub Actions 在线编译 APK

本完整源码已包含 `.github/workflows/build-apk.yml`。工作流固定使用 JDK 17、Python 3.11、Android SDK 35、Build Tools 35.0.0 和 Gradle 8.11.1，日常推送只做编译验证，不再上传 debug 包。

打 Tag 自动发 Release（5 个正式分包：`arm64-v8a` / `armeabi-v7a` / `x86_64` / `x86` / `universal`，如 `Jingliu-v0.8.2-arm64-v8a.apk`）见 [发版流程](docs/RELEASE-FLOW.md)：`git tag v0.8.2; git push origin v0.8.2`。真机优先装 `arm64-v8a`，不知道机型装 `universal`。

**GitHub 不会自动解压你提交到仓库里的 ZIP 文件。** 请先解压，再把 `Jingliu-Android` 目录内部的 `.github`、`app`、`core`、Gradle 文件等提交到仓库根目录。具体上传、手动运行、下载 APK 和可选稳定 Debug 签名方法见 [GitHub Actions 编译说明](docs/GITHUB-ACTIONS-BUILD.md)。
