# 依赖与素材

本工程源代码以 GPL-3.0-or-later 发布；根目录 LICENSE 附 GPLv3 完整文本。并不改变以下依赖各自的许可。运行时依赖由 Gradle 获取，源码 ZIP 不打包其 AAR / 本地二进制。

| 依赖 | 固定版本 | 上游 / 许可 |
|---|---|---|
| Kotlin / Compose compiler plugin | 2.1.20 | https://github.com/JetBrains/kotlin · Apache-2.0 |
| AndroidX Compose / Lifecycle / Activity / WorkManager | 见 app/build.gradle.kts | https://android.googlesource.com/platform/frameworks/support/ · Apache-2.0 |
| kotlinx.coroutines | 1.10.2 | https://github.com/Kotlin/kotlinx.coroutines · Apache-2.0 |
| Haze | 1.6.10 | https://github.com/chrisbanes/haze · Apache-2.0 |
| Coil | 2.7.0 | https://github.com/coil-kt/coil · Apache-2.0 |
| ZXing core | 3.5.3 | https://github.com/zxing/zxing · Apache-2.0 |
| youtubedl-android library / ffmpeg | 0.18.1 | https://github.com/yausername/youtubedl-android/tree/0.18.1 · GPL-3.0 |
| yt-dlp 及 Python/FFmpeg/QuickJS 组件 | 由上项发行包提供，引擎可用户手动更新 | 分别参照 https://github.com/yt-dlp/yt-dlp 、https://www.python.org/psf/license/ 、https://ffmpeg.org/legal.html 、https://bellard.org/quickjs/ |
| Gradle Wrapper / Gradle | 8.11.1 | https://github.com/gradle/gradle · Apache-2.0；脚本只下载官方文件并核对哈希 |
| JUnit | 4.13.2 | https://junit.org/junit4/ · EPL-1.0 |

发布二进制时应把使用到的组件许可、源码提供方式与对应版本一起保留；本文件不是已经完成商店发布合规审查的证明。Python/FFmpeg 等原生组件的构建选项与许可请以固定上游版本为准。

## 源码来源

玻璃/排版/弹簧控件和类型化设置来自本对话此前的 LUMA v6 工程，已经移除其演示业务。真实解析/传输/账号部分延续本对话的 Shiliu Kotlin 代码并补齐结构、测试与安全检查。没有直接打包 Bili23-Downloader 或 parse-video-py 的完整 Python 应用，也没有将这些项目部署为云端服务。

## 图标与字体

平台 B站、抖音、YouTube、TikTok、小红书、快手 PNG 来自用户上传图标包，X PNG 来自用户上传原图，原图哈希记录在 tests/resource-checks.json。素材不代表平台的授权背书。本次启动图标沿用早期 LUMA 的矢量资源。只使用 Android 系统字体，不提供或打包 Apple/SF 等字体文件。

## 0.7.3 可选授权服务

`auth-bridge` 为此工程新增的 Kotlin/JVM 源码，沿用根目录 LICENSE。没有复制官方客户端密钥或把平台品牌当作授权背书。协议依据官方公开文档，参见 docs/FIX-004-render-qr.md。实际使用仍需注册/获批对应应用和权限，OAuth 身份资料与媒体下载权限相互独立。

## 0.7.6 packaging addendum

The optional `parse-video-service` wrapper depends on wujunwei928/parse-video-py at commit `5fcf87256edb5ffcdebf0e4aac2a5a41745da76e`. The upstream project retains its MIT license. This source ZIP includes only our wrapper and an installation declaration, not upstream wheels or the whole upstream repository. The limited Kotlin Douyin share endpoint compatibility was informed by the same commit's `parser/douyin.py`; protocol fields/URLs are used without bundling a borrowed account, client key, or platform signature secret.

https://github.com/wujunwei928/parse-video-py/tree/5fcf87256edb5ffcdebf0e4aac2a5a41745da76e

No real upstream parser or live platform account was exercised by the Python wrapper unit tests; the test substitute exists only in test_server.py.


## 0.7.7 本地Python引擎新增依赖

- Chaquopy 17.0.0：Android内嵌Python运行与Gradle插件。https://github.com/chaquo/chaquopy 。按官方仓库及所解析制品携带的许可分发，不是苹果私有框架。
- CPython 3.11与其运行时附属库：随Chaquopy依赖进入APK，保留对应许可证与二进制来源。
- parse-video-py：上游声明MIT；固定源提交 `5fcf87256edb5ffcdebf0e4aac2a5a41745da76e`，https://github.com/wujunwei928/parse-video-py 。完整库在构建时作为依赖安装，本ZIP未离线复刻其全部源码和轮子。
- httpx、httpcore、fake-useragent、parsel、lxml、jmespath、PyYAML、aiohttp、yarl、multidict、frozenlist及pip解析的传递依赖：版本输入见根python-runtime-requirements.txt。各自版权/开源许可继续有效。Android轮子来源为Chaquopy官方Python包索引；不要将桌面轮子当作Android二进制。
- app/src/main/python/jingliu_local/__init__.py 是本项目的Android适配层；并非把测试替身打入产品，也不是运行一个网络服务器。

旧版本“没有额外Python运行时/无需电脑Python”的描述不适用于0.7.7；以当前README和BUILD-0.7.7为准。发布前应从真实Gradle/pip解析结果生成完整SBOM、保留库许可证及原生组件对应源码要求。本记录不是法律或安全审计。


## 0.7.8 新增依赖声明

媒体预览使用 AndroidX Media3 1.7.1（exoplayer/hls/dash/datasource-okhttp/ui）；网络显式使用 OkHttp 4.12.0，图像仍为 Coil 2.7.0。AndroidX 与 OkHttp 按其上游 Apache-2.0 等相应许可证使用。此包没有捆绑这几个依赖的二进制副本；实际 APK 分发须保留相应许可证、核对全部传递和原生依赖。

https://github.com/androidx/media
https://github.com/square/okhttp


## 0.8.1 optics integration note

The current app-owned optics implementation is provided under the project license. Kyant0/AndroidLiquidGlass and QWEA0/Liquid-Glass-Android are design/architecture references from the previous investigation, not new bundled runtime dependencies. No Apple font or proprietary shader is included. The active Haze renderer dependency has been removed; references and test records for older versions above are historical. Android Compose, Android RenderEffect and RuntimeShader provide the actual drawing APIs. This is not a verified release SBOM or native shader performance certification.
