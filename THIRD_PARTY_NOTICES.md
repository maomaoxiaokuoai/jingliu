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

## 液态玻璃参考实现（思路借鉴，非源码复制）

镜流 0.9.1 的玻璃材质为自有 Compose/AGSL 重新实现，未直接复制以下仓库的源码文件。
实现时对照其公开提交核对曲线与交互模型，并在注释中保留出处：

- Kyant0/AndroidLiquidGlass（Apache License 2.0）
  上游：https://github.com/Kyant0/AndroidLiquidGlass
  对照提交：`65ab177e90e5c1d8c62e70cf7755841982da65f6`（kmp 分支读取记录）
  借鉴点：分层底栏/镜片（LiquidBottomTabs）、按住扩大镜片、速度拉伸与弹簧回落
  （DampedDragAnimation）、交互高光、圆弧透镜与七色带色散思路。
  许可：Apache-2.0，版权归 Kyant 及贡献者所有。详见上游 LICENSE。

- QWEA0/Liquid-Glass-Android（MIT License）
  上游：https://github.com/QWEA0/Liquid-Glass-Android
  对照提交：`73e22530f4f6d1d525d7d76ca83efe07dd816eee`
  借鉴点：逆幂/平方边缘折射、逐角 SDF、smooth-min 双形状融合、局部触摸鼓起、
  双向镜面与环境高光、背景明暗自适应、介质着色与饱和度保护。
  许可：MIT，版权归 QWEA0 及贡献者所有。详见上游 LICENSE。

如后续直接复制上游文件，将同时保留其原始版权头与许可文本，打包进源码与 APK notices。

## 图片与平台标识

应用资源中的 Bilibili、抖音、快手、小红书、YouTube、TikTok、X 等名称和图标仅用于识别用户所选择的平台。相关商标、名称和图形归各权利人所有，不代表这些平台对镜流项目提供授权、赞助或背书。

应用不包含 Apple/SF 等专有字体文件，使用 Android 系统字体。
