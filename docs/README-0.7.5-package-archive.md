# 镜流 GlassFlow 0.7.5 · 完整源码整合包

**完整工程目录，不是补丁；不含 APK。** 本次以 `Jingliu-Android-0.7.4-完整源码.zip` 为底包，合并上一轮提供的全部 0.7.5 性能与登录跳转修改；没有在打包过程中重新设计界面或改写下载引擎。

版本：`0.7.5-motion-performance`，`versionCode=12`，包名仍为 `com.luma.downloader`。

## 打开与编译

1. 解压到独立目录，在 Android Studio 打开包含 `settings.gradle.kts` 的 **Jingliu-Android 根目录**，不要单独打开 `app` 或 `auth-bridge`。
2. 沿用你此前可用的 JDK 17 / JBR 21、SDK 35、Build-Tools 35.0.0。Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.20、Haze 1.6.10 与此前一致。
3. **源码不附二进制 `gradle-wrapper.jar`。** 新目录先运行 `setup-wrapper.bat`，或从旧工程复制同版本、已验证的官方 JAR 到 `gradle/wrapper/`。初始化脚本保持 SHA-256 校验；不要用来路不明的 JAR，不要关闭校验。
4. Android Studio 同步完成，选择 `app` 和设备后点击 Run。首次获取依赖仍需要网络。

在根目录打开 PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug --console=plain --stacktrace
```

编译成功后的 APK：`app\build\outputs\apk\debug\app-debug.apk`。

性能对比使用新增的不可调试 `profile` 变体，不降低材质和动效参数：

```powershell
.\gradlew.bat :app:assembleProfile --console=plain --stacktrace
```

编译成功后的 APK：`app\build\outputs\apk\profile\app-profile.apk`。也可以运行新增 `build-profile.bat`。它使用本机 debug 签名，只用于本地测试，不是正式发布签名。用相同签名更新通常不需要卸载；先保留旧签名和数据，不要为了切版本清空 Cookie、下载文件或设置。

## 包内内容

- `app/`：完整手机/平板界面、玻璃/弹簧组件、任务管理、账号、原生资源，包含 0.7.5 新增的页面复用和帧耗时统计代码。
- `core/`：原有解析模型、Cookie 与网络、续传、账号和扫码协议代码及测试。
- `auth-bridge/`：已有的可选 Kotlin/JVM 身份授权服务；默认不导入 Android 构建，普通 app 编译无需部署它。
- `branding/`：镜流图标源素材与预览，七个平台图标保留。
- `docs/`、`tests/`、`tools/`：说明、实际可用的测试源码、历史记录与本次打包检查。
- `source-manifest.json`：除清单自身外的逐文件 SHA-256。

## 本版整合的修改

四个主页面复用、设置首页和详情返回处理、绘制/布局阶段动效更新、图片请求与过滤计算缓存、下载进度订阅范围收敛，以及 B站当前扫码请求的 App / 外部浏览器接续跳转代码。已有玻璃、颗粒、图标和默认动画参数没有在打包时降低或关闭。

**B站这两条跳转仍是标注为“试验”的兼容性路径，不等于官方 SDK 的一键 Cookie 授权。** 只有扫码轮询拿到真实会话并校验身份才保存；打开页面或回到应用本身不算登录成功。其他平台现有 Cookie 导入及已配置时的开放平台扫码代码保留。

**本包没有额外补写上一条需求中尚未完成的 `parse-video-py` 多引擎切换、合集下载前新增画质选择，或 B站“已登录但下载失败”的针对性修复。** 下载相关代码仍是当前已有实现，不能因为打了完整 ZIP 就视为这些需求已解决。七个平台正式 SDK 的全面接通也没有新增。

## 验证状态

本次核对了底包、全部 0.7.5 覆盖文件和资源字节，重新运行可用的核心/账号/扫码、菜单和设置主机测试，以及 Kotlin 语法、XML 和资源引用检查；具体输出见 `tests/package-0.7.5/`。

**没有在本次执行完整 Android Gradle 编译、真机帧率对比或平台实际登录/下载，不含 APK，也不保证新版本零报错或所有平台可用。** 主机检查不是 Android 类型检查或画面流畅度测试。上一轮单文件未提供的个别测试源码没有伪造补入，原日志按历史证据保留。

[本次打包范围与验收说明](docs/PACKAGE-0.7.5.md) · [0.7.5 开发说明存档](docs/FIX-006-performance-login.md) · [0.7.4 使用说明](docs/README-0.7.4.md) · [可选授权服务配置](auth-bridge/README.md)
