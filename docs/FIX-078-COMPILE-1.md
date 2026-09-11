# 镜流 0.7.8 · 编译修复 1

## 这次为什么编译失败

原 `auth/EmbeddedLoginActivity.kt` 第 69 行：

```kotlin
AndroidView(Modifier.padding(padding).fillMaxSize(), factory = { ... })
```

`AndroidView` 的第一个参数是 `factory: (Context) -> T`，不是 `Modifier`。
原调用先把 Modifier 作为 factory 传入，又使用 factory 命名参数重复赋值，
于是触发 Modifier/函数类型不匹配、重复参数及无法推断 T 等连带诊断。

第 60、61、65、68 行的 `"文本"else"文本"` 也已补齐空格。
旧版 PSI 语法树检查不做此处的真实 API 类型绑定，不能证明这些调用可编译；
本次增加了针对该 Activity 的编译前后对照，不再仅以语法树作为修复证据。

## 修复内容与范围

- `EmbeddedLoginActivity.kt`：改为 `AndroidView<WebView>(factory = ..., modifier = ...)`，
  将 WebView 建立和一次性 prepare 留在 factory。将文件按正常 Kotlin 多行排版整理。
- `MediaPreviewActivity.kt`、`LoginQrDialog.kt`、`LumaViewModel.kt`：各有一处相同类型的
  if/else 紧贴字符串写法，仅添加空格，不改变条件、文字或行为。
- 不改变 Gradle、Kotlin、Compose、Haze、Chaquopy 或 Media3 的依赖版本。
- 不改变应用包名、versionCode、图标、玻璃/动画、解析/下载/续传实现或账号数据格式。
- 内置浏览器原有域名校验、用户确认、Cookie 范围与截图保护仍保留。
- 不新增平台 SDK 授权能力，也不将本次编译修复解释为平台登录实测成功。

## 最快更新：替换一个文件

截图中阻止 `:app:compileDebugKotlin` 的错误集中于 EmbeddedLoginActivity.kt。
将单独提供的同名文件覆盖到原工程：

```text
C:\Users\maomao\Downloads\Jingliu-Android\app\src\main\java\com\luma\downloader\auth\EmbeddedLoginActivity.kt
```

保持文件名为 EmbeddedLoginActivity.kt，不要改成 .kt.txt。
备份放到源码目录以外，不要同时保留两份 .kt 类文件。

完整 ZIP 另外包含三处空格修正。已有本地改动时不要用整包无条件覆盖你的工程；
可以使用补丁 ZIP 中保持相对路径的四个源码文件，或按 patch.diff 查看具体差异。
原本已经正常下载的 Wrapper、SDK、Python 和 Gradle 缓存都无需删除或重装。

更新后在 Android Studio 保存，再点击 Run；或者在原工程根目录运行：

```powershell
.\gradlew.bat :app:assembleDebug --console=plain --stacktrace
```

这个命令构建 APK，不会自己启动应用。构建成功后点击 Run 安装和启动。
新目录仍不附 gradle-wrapper.jar，可从原已可用工程复制同版本官方文件或运行原 setup-wrapper。
保持原来的签名，不要卸载、清空账号、删除下载文件来修复此处编译错误。

## 实际验证，不等于 Android 整工程构建

1. 核对原 ZIP 的 SHA-256 与随包校验文件一致；原包 415 个文件，CRC 正常。
2. 使用真实 Kotlin 编译器、当前完整 core 生产源码、真实 coroutine JAR，
   对实际 EmbeddedLoginActivity.kt 作局部类型编译。
   Android / Compose / AppGraph 等外部接口用明确列出的**类型测试替身**提供。
3. 原文件在 Kotlin 1.9 默认前端复现四处 literal prefixes/suffixes 及 AndroidView
   参数类型/重复参数错误；实验 K2 前端复现 AndroidView 参数类型/重复参数错误。
4. 修复后的整个 Activity 在同一套输入下，默认前端和实验 K2 均编译退出码 0。
   K2 的实验语言版本提示保留在日志中，没有全局关闭诊断。
5. 扫描应用生产源码的全部 3 处 AndroidView 调用，另两处本来使用命名参数。
6. 最终 ZIP 再校验 CRC、逐文件 SHA-256，并对照原包确认只有上述四个生产文件变化；
   其余生产源码、构建配置与图标资源逐字节保持不变。

环境：Kotlin/JVM 1.9.0、JDK 21、Java 17 输出目标。不是项目正式的 Kotlin 2.1.20
Android 编译；没有真实 Android SDK 或 Compose 编译插件，不验证 WebView 的运行时、
生命周期、真实 Cookie/登录行为。测试替身在 tests/compile-fix-078/api-stubs，
不属于 Android app 的源码集，不能复制到 app/src/main。

**当前环境没有可用 Android SDK，官方依赖源 DNS 探测失败。本次没有执行完整
Gradle assembleDebug、生成 APK 或真机验证。** 此包修复截图明确显示的源码问题，
不宣称所有平台功能或整个工程零错误。

局部编译日志和可复现脚本见 `tests/compile-fix-078/`。其他 tests 目录保持为历史证据，
没有把历史测试数重新累计成本轮结果。无需为了这次修改运行下载平台测试。

## 官方接口依据

- AndroidView（factory/modifier 参数）：https://developer.android.com/reference/kotlin/androidx/compose/ui/viewinterop/package-summary
- Kotlin 条件表达式：https://kotlinlang.org/docs/control-flow.html
- Kotlin 格式规范：https://kotlinlang.org/docs/coding-conventions.html
