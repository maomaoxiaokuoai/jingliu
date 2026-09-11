> **0.7.8 编译修复 1：** 已修正 EmbeddedLoginActivity 的 AndroidView 参数及 if/else 间距。
> 本次只修改 4 个生产文件，其中 3 个仅补空格；不调整依赖或数据。
> [本次修复、安装与真实验证范围](docs/FIX-078-COMPILE-1.md)。仍没有 APK 或 Android 全工程构建结果。

# 镜流 GlassFlow 0.7.8 · 媒体请求、预览、浏览器会话与恢复下载

**完整源码工程，不是增量补丁，不含 APK。** 基于 0.7.7 完整包，合并上一轮导出的 0.7.8 修复文件，并补齐当时没有随单文件交付的模型、依赖、请求客户端、页面入口和状态存储接线。

> **交付状态：开发源码整合包。当前没有 Android 整工程编译、真机预览/WebView/120Hz 或平台联网验收。**
> 本次本地实际通过的是核心/JVM/HTTP/文件/协议和 Python 适配测试；不是用真机或真实平台账号测试出来的“全部功能正常”。Android 构建在官方 Wrapper 下载 DNS 失败时中止，没有运行 Gradle 依赖解析与 Compose/Media3 类型检查。

## 打开与编译

1. 解压到独立目录，例如 `D:\Projects\Jingliu-Android`；Android Studio 打开包含 `settings.gradle.kts` 的根目录，而不是单独的 app、auth-bridge。
2. 保留 JDK 17 / JBR 21、SDK 35、Build-Tools 35.0.0。Gradle、AGP、Kotlin、Compose、Haze、Chaquopy 与 0.7.7 相同。此次为预览新增统一版本的 Media3 1.7.1 模块，并显式声明 OkHttp 4.12.0。
3. **构建电脑仍需要 Python 3.11.x**。手机不需要另装 Python，也不需要解析服务器。运行 `py -3.11 --version` 确认；识别失败时复制 `python-build.properties.example` 为 `python-build.properties` 并填写该 Python 的实际可执行文件路径。
4. **源码不含 `gradle-wrapper.jar`。** 可以从你已经成功构建的旧工程复制同版本官方文件到 `gradle/wrapper/`；或者运行 `setup-wrapper.bat` 下载并校验。不要关闭 SHA-256 或 TLS 校验。
5. 完成 Sync 后选择 app，运行到手机或模拟器。

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain --stacktrace
```

只有构建成功才会产生 `app\build\outputs\apk\debug\app-debug.apk`。性能对照构建仍使用：

```powershell
.\gradlew.bat :app:assembleProfile --console=plain --stacktrace
```

包名保持 `com.luma.downloader`，versionCode=15。使用同一签名更新，不需要为了这次修改卸载、清 Cookie 或删除下载文件。首次构建仍需要联网获取依赖；ZIP 不是所有 Maven/Python wheel 的离线镜像。

## 本次代码范围

### 小红书图片及封面

- 手机 Python 适配层记录当前解析请求中的有效官方页面，优先使用页面实际返回的图片地址、签名和处理参数；不把有效地址重写成猜测的 ci/notes_pre_post 地址。
- 同图实际备用地址穿过 Python → Kotlin 模型 → 图片预览/下载链路，不只保留首地址。
- 图片加载、视频预览和下载共享媒体请求规则：稳定的平台 Referer、User-Agent、按最终请求域和路径匹配的 Cookie；不把平台 Cookie 发给任意 CDN，也不在媒体元数据内保存 Cookie。
- 小红书等图片 CDN 返回 403 仍可能与资源过期、权限或风控有关，本次没有真实链接实测；不能保证先前具体 403 已消失。

### 预览

解析页增加图片“预览”和所选视频/音频预览入口，与多选操作分开。图片可缩放、拖动、恢复及重试；视频使用 Media3，支持实际源流和分离音视频组合。离开页面释放播放器，迟到的错误回调不能后台重新开始播放。完整播放器、Codec/网络兼容需在设备验证。

### 快手和本地 Python

同一次解析的临时、域隔离 Cookie 在新建 HTTPX 客户端时不再丢失。兼容逻辑从捕获的实际页面状态读取匹配作品，不取错误推荐视频。Python 仍在 APK 私有进程执行，无 localhost 服务，也没有把错误转换成假解析成功。

### 显式引擎选择

选择 native、yt-dlp、parse-video-py 后只走所选实现；B站在 parse-video-py 下不再隐式转入原生。上游不支持的非首分P会提示不支持，用户需要自己选其他引擎。自动模式保留原生→yt-dlp 的既有回退；不静默调用第三方服务。合集确认框仍只有画质，不重复选择引擎。

### 续传、进度和重试

不同官方候选节点使用独立续传槽位。有效 validator 时继续 Range；缺失 validator、身份变化或服务器返回整份文件时，旧片段保存在任务私有目录，再安全重新读取，**不是把不同内容强行拼接**。网络未结束但流提前终止会报告可重试错误。

遇到 401/403/410，当前 Worker 最多重新解析一次实际地址，不无限循环认证。普通网络错误按重试上限和 WorkManager 退避处理。默认重试次数提升至本版上限 10，升级迁移一次；之后自己修改的次数继续保存。

字节进度先更新内存，约一秒合并持久化；阶段转换、暂停、完成、删除等关键状态仍即时写盘。片段实际字节与 validator 决定恢复位置，不依赖可能落后一秒的 UI 数字。无 validator 的旧片段需要额外磁盘空间；任务完成/确认删除后随任务目录清理。

### 浏览器登录

账号页增加内置手机版/电脑版浏览器。用户在官方页面完成密码、短信或扫码等平台提供的流程后，主动点击“保存此页会话”，才保存该 WebView 自己的 Cookie。新浏览器先清理前一次临时站点状态；只对当前平台域名采集；B站保存前验证身份。其他平台仍标记“Cookie 已保存 · 未验证身份”。

**YouTube/Google 不开放内嵌 OAuth 路径**，保持系统浏览器和主动 Cookie 导入。外部浏览器已有账号或唤起官方 App，不代表镜流拿到了它们的 Cookie；未配置的平台 SDK 不会被假的授权弹窗代替。网页不提供相应登录方式或跨站跳转受限时，会如实提示。登录页默认截图保护，用户可仅在本次页面确认允许二维码截图。

### 滚动更新开销

保留原有 UI 风格、玻璃/颗粒和弹簧参数，不自动启用低画质或低模糊策略。增加共享图片连接池/缓存、列表派生值和玻璃计划缓存，减少频繁磁盘写入。主窗口请求同分辨率且不高于 120Hz 的可用刷新模式；实际刷新率仍由系统决定。Profile 帧统计使用实际帧预算，**没有 iQOO Z8 的帧耗时或内存实测，不作 120fps 保证**。

## 目录

- app/：完整 Kotlin/Compose 应用、媒体预览和 WebView 登录、本地 Python 适配、资源。
- core/：统一模型、请求/续传/错误/浏览器会话策略、测试。
- auth-bridge/：此前可选的开放平台身份服务，默认不参与 app 构建；不是解析服务器。
- branding/：原镜流启动图标和七个平台素材，未更换。
- docs/、tests/、tools/：新旧说明与测试证据，原历史日志保留，不冒充本轮结果。

[本次打包与验证范围](docs/PACKAGE-0.7.8.md) · [本次测试结果](tests/package-0.7.8/verification.json)
