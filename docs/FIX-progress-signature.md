# 镜流：网页进度属性 JVM 签名冲突修复

## 最快的修改

仅将 `EmbeddedLoginActivity.kt` 中本地网页进度属性 `progress` 及它的读取、赋值改名为 `pageLoadProgress`。
`LinearProgressIndicator(progress = ...)` 的库参数名仍是 `progress`，`onProgressChanged` 的回调名也不改。

```kotlin
private var pageLoadProgress by mutableIntStateOf(0)

if (pageLoadProgress in 1..99) {
    LinearProgressIndicator(
        progress = { pageLoadProgress / 100f },
        modifier = Modifier.fillMaxWidth()
    )
}
```

不要给该属性加 override，不改 Compose/Gradle 版本，不使用全局 Suppress 来遮盖错误。

## 原因与版本核对

Kotlin 的 var 属性有 setter。这个委托属性的 setter 生成 JVM 签名 `setProgress(I)V`，
与父类 Android Activity 的 `public final void setProgress(int)` 冲突。属性原来已经是 private，
所以再加 private 不能解决；框架方法是 final，也不应该通过 override 来解决。

截图第 64 行以及 consent / externalGoogle / lightFeed 这些字段，对应以前 0.7.10 原始文件的布局。
后续 0.8.0 原包的相同属性在第 55 行，也存在同样问题。补丁包含两种文件的最小修复，
根据旧文件校验值匹配，不把两个不同版本的整份浏览器实现互相覆盖。

0.7.10：仅 1 个生产文件，6 行、7 处属性引用改名。
0.8.0：仅 1 个生产文件，4 行、5 处属性引用改名。

两版各自原有的 Cookie 自动保存、期限/在线检查、WebView、缓存、登录流程不变。
玻璃、动画、图标、下载引擎、SDK/依赖配置、签名及 versionCode 都不变。

## 更新方式

### 当前工程推荐用自动匹配补丁

解压 `Jingliu-progress-signature-patch.zip`，在有 `ApplyPatch.ps1` 的目录打开 PowerShell：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\ApplyPatch.ps1 -ProjectPath "C:\Users\maomao\Downloads\Jingliu-Android"
```

脚本只检查/备份/替换上面的单个 Kotlin 文件。已是修复版会直接退出；
本地文件不是两种已知基线之一时会停止，不强行覆盖你自己的修改。
会识别常见 UTF-8 BOM / CRLF 与 LF 换行差异；除此以外不忽略代码变化。
备份写在项目根目录的 `.jingliu-progress-backup-*` 文件夹，不放进 app/src。

PowerShell 脚本本次没有 Windows 执行环境测试。也可以手动使用已标明版本的单个文件，
或在 Android Studio 对这个属性做 Rename 重构；不要对整个项目做无差别文本替换。

### 完整包

按你当前工程选择对应的完整 ZIP；不要把 0.8.0 的一个 Activity 单独放进 0.7.10。
解压新目录时打开包含 settings.gradle.kts 的根目录。缺少 Wrapper JAR 时沿用旧工程的
同版本官方 gradle/wrapper/gradle-wrapper.jar，或运行原 setup-wrapper 脚本。
只替换当前工程单文件时，不需要重新初始化 Wrapper 或删除缓存。

保存后直接点击 Android Studio Run，或在原工程根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug --console=plain --stacktrace
```

黄色建议不是本次唯一红色的签名冲突，不需要为了本错误升级全部依赖、重装 JDK 或卸载 App。

## 本次验证（不要与完整 Android 构建混淆）

1. 分别从 0.7.10、0.8.0 原 ZIP 校验/提取对应 Activity。
2. 之前的外部类型替身遗漏 Activity.setProgress(int)。本次新测试目录为替身补入同名 final 方法；
   历史测试目录原样保留，不伪造以前已测过这条继承签名。
3. 两个未修复的完整 Activity 都用实际 Kotlin 编译器复现 `accidental override ... setProgress(I)V`；
   修改后在同一套输入下两个完整 Activity 的局部类型编译退出码均为 0。
4. javap 检查修复类：有 `getPageLoadProgress()` / `setPageLoadProgress(int)`，不再声明 `setProgress(int)`。
5. 在外部类型替身环境中实例化实际编译的类并测试状态初始 0、写入/读取 1/25/99/100/0；
   同时检查原框架方法仍被继承。未启动 Activity 生命周期、WebView、账号或联网流程。
6. ZIP CRC、逐文件 SHA-256 校验；对照原包，生产源码只有该 Activity 改变；所有原图资源与依赖配置不变。

工具：Kotlin/JVM 1.9.0、OpenJDK 21、输出目标 Java 17。
局部类型检查使用明确的 Android / Compose / WebKit / AppGraph 类型替身，
不运行正式 Compose 编译插件，不等同项目正式 Kotlin 2.1.20 / Android SDK 编译。
**当前环境没有可用 Android SDK，本次未执行 Android assembleDebug、未生成 APK、未做手机登录验证。**
原包其他功能和未验证事项仍见原 README。这个补丁修复截图中确定的编译冲突，不承诺所有问题消失。

本轮可复现输入、编译前后日志和字节码签名在 `tests/progress-signature-fix/`；测试替身不属于 app/src。
执行 `tests/progress-signature-fix/run-typecheck.sh` 需要本机 Kotlin 安装（包含 coroutine JAR）。
不要把 tests 下的替身或原始错误样例复制进 Android 生产源码集。

## 官方依据

- Android Activity.setProgress(int)：https://developer.android.com/reference/android/app/Activity#setProgress(int)
- Kotlin 属性生成 getter/setter：https://kotlinlang.org/docs/java-to-kotlin-interop.html#properties
