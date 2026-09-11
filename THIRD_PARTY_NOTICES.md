# 第三方依赖与素材声明

镜流项目自有源码按根目录 `LICENSE` 中的 GPL-3.0-or-later 条款发布。下列第三方组件继续适用各自的版权与许可证，本文件不改变其授权条件。

## Android 与 Kotlin 依赖

| 组件 | 当前构建版本或来源 | 许可证/上游 |
|---|---|---|
| Kotlin 与 Compose Compiler | 2.1.20 | JetBrains Kotlin，Apache-2.0 |
| Android Gradle Plugin | 8.9.2 | Android Open Source Project，Apache-2.0 |
| AndroidX Compose、Core、Activity、Lifecycle、WorkManager、WebKit、Media3 | 版本见 `app/build.gradle.kts` | Android Open Source Project，Apache-2.0 |
| kotlinx.coroutines | 1.10.2 | JetBrains，Apache-2.0 |
| Coil | 2.7.0 | Coil Contributors，Apache-2.0 |
| OkHttp | 4.12.0 | Square，Apache-2.0 |
| ZXing Core | 3.5.3 | ZXing Authors，Apache-2.0 |
| youtubedl-android library/ffmpeg | 0.18.1 | yausername/youtubedl-android 及其捆绑组件的相应许可证 |
| Chaquopy | 17.0.0 | Chaquo Ltd. 及其发行组件的相应许可证 |

## 内嵌 Python 依赖

`python-runtime-requirements.txt` 固定了 APK 构建使用的 Python 依赖，包括：

- `parse-video-py` 固定提交 `5fcf87256edb5ffcdebf0e4aac2a5a41745da76e`，上游声明 MIT。
- `httpx`、`fake-useragent`、`parsel`、`jmespath`、`lxml`、`PyYAML`、`aiohttp`、`yarl`、`multidict`、`frozenlist` 及其传递依赖。
- CPython 3.11 和 Android Python 运行时由 Chaquopy 在构建阶段解析并嵌入 APK。

实际发布 APK 时，应以 Gradle、Maven、pip 和原生库最终解析结果为准，保留各组件要求的许可证、版权声明和源码提供方式。建议为每个正式版本生成完整 SBOM。

## 图片与平台标识

应用资源中的 Bilibili、抖音、快手、小红书、YouTube、TikTok、X 等名称和图标仅用于识别用户所选择的平台。相关商标、名称和图形归各权利人所有，不代表这些平台对镜流项目提供授权、赞助或背书。

应用不包含 Apple/SF 等专有字体文件，使用 Android 系统字体。
