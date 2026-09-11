# FIX-001：MediaRuntime.kt 的 Kotlin length 编译错误

## 修复范围

本修复基于上一份 `Shiliu-Android-完整源码.zip`，原包 SHA-256：

`d36aff78634015336d1e38810f82adf52db816c18c0cccde2465c65eddab36a7`

生产代码只修改 `app/src/main/java/com/luma/downloader/engine/MediaRuntime.kt` 的第 93、98 行。
不修改 UI、动画、下载策略、账号逻辑、Gradle 版本或资源。

- 第 93 行：`stderr.length()<32000` → `stderr.length < 32000`
- 第 98 行：`output.length()+line.length<24*1024*1024` → `output.length + line.length < 24 * 1024 * 1024`

两处对象都是 StringBuilder；Kotlin 的 length 是属性，不应当使用函数调用括号。
两个 compareTo/operator 报错是上述表达式类型错误带来的连带诊断，不需要添加 operator。

第 49、50、75 行的对象是 java.io.File，其 length() 是合法方法，已原样保留。
不要在整个工程中把所有 `.length()` 全局替换为 `.length`。

修复后 MediaRuntime.kt SHA-256：

`f4d79033c242322202fbaf777da70f676115d03f2129b3ff730946d67116c0e5`

## 推荐安装方法：仅替换一个文件

1. 备份本机工程的 MediaRuntime.kt；备份移到源码目录以外，避免被 Kotlin 编译器重复编译。
2. 将随补丁提供的 MediaRuntime.kt 覆盖到：
   `app/src/main/java/com/luma/downloader/engine/MediaRuntime.kt`
3. 文件名保持 MediaRuntime.kt，不要保存为 MediaRuntime.kt.txt 或同时保留两份同名类。
4. 保存后点击 Android Studio 顶部 Run，或在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain
```

后一个命令只构建 APK；要安装和启动，使用 Android Studio Run。
不需要因这两处源码错误重装 Android Studio、重建模拟器、改 Gradle/Kotlin 版本或再次运行 setup-wrapper。
黄色的多余分号、绿色的单词拼写检查不是截图中的四个编译阻塞错误。

## 本次实际验证（不是 Android 整工程验收）

- 扫描原包所有 Kotlin/KTS 中的 length()/size() 调用，区分 StringBuilder 属性与 Java 文件/缓冲区方法。
- 以原 MediaRuntime.kt、实际 core 生产代码和最小外部类型测试替身进行编译，复现 length 无法调用及其连带诊断。
- 使用同一套局部编译输入，仅替换修复后的 MediaRuntime.kt，编译退出码为 0。
- 对修复后的真实 JVM subprocess 方法执行 10 项检查：标准输出缓冲、标准错误分离、进度回调、空输出、末行、非零退出分类、预取消和长度 API 等，全部通过。
- 重跑实际 core 测试：76 条断言通过。数据来自合成样本与 localhost HTTP，不是平台线上下载。

本地编译器：kotlinc-jvm 1.9.0，启用 `-language-version 2.0` 的实验性 K2 模式，JVM target 17；运行 JDK 21。
这并非项目配置的 Kotlin 2.1.20 正式 Android 构建。
局部编译用测试替身提供 Context、SessionVault、YoutubeDL、FFmpeg 的类型，不验证其真实 Android 依赖接口或运行时。
没有连接平台账号、执行 yt-dlp/FFmpeg 下载、生成 APK 或运行 Android 模拟器。
当前环境未获得可用的完整 Android SDK/依赖；本次 Maven 网络探测失败（DNS）。

因此，本次结论是“截图中两处源码错误已修复并完成针对性复核”，不是“整个应用零 Bug”。
构建是否全部通过，以本机 Android Studio/Gradle 新输出为准。

新日志见 `tests/fix-001/`。旧 `tests/logs/` 是原包的历史记录，未伪造或覆盖。

## 官方 API 参考

- Kotlin StringBuilder.length： https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/-string-builder/length.html
- Java File.length()： https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/File.html#length()
