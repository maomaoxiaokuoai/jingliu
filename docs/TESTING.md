> 最新 0.7.4 测试见 [FIX-005](FIX-005-build-qr-access.md) 及 `tests/fix-005/`。下文为基线/历史说明，不是本次 Android 构建成功证明。

> **本文件为历史基线。** 0.7.2 整合时重新执行的结果和限制见 [本次说明](RELEASE-0.7.2-complete.md) 与 `tests/package-0.7.2/`。历史设备/网络未验收状态没有改变。

> 本次菜单/品牌补丁验证见 [FIX-002-menu-brand.md](FIX-002-menu-brand.md) 和 tests/menu-fix/；以下是原包基线记录。

# 本次验证记录

这份记录针对当前源码包，不能沿用早期网页原型的截图/浏览器测试来证明原生软件工作正常。

## 已执行

| 检查 | 结果 | 边界 |
|---|---|---|
| 纯 Kotlin core 编译 | 成功，1 条 Java 平台 nullability 警告 | JDK 21 + kotlinc 1.9.0，本地 CLI；不是 Android Gradle 构建 |
| core 回归 | 76 条断言通过 | JSON、Cookie/URL、格式/图集/合集合成响应、最高可用画质、实际 localhost HTTP 下载/206续传/200回退/416校验/取消/HTML拒绝 |
| 设置/材质/动效策略 | 175 条断言通过 | 纯 JVM，不涉及 Compose 绘制或触摸事件 |
| Kotlin/KTS 语法 | 46 个文件，0 语法错误 | Kotlin PSI 解析，不能识别缺失 Android 依赖类型或 API 不匹配 |
| 目录/资源/配置检查 | 85 项结构检查通过 | 37 个生产 Kotlin 文件、XML、Manifest 类、资源、设置键、shell 语法 |
| 平台 PNG | 7 张原图哈希一致 | 不含字体、不生成替代品牌图标 |

原始日志位于 `tests/logs/`；结构报告是 `tests/source-checks.json`，图标报告是 `tests/resource-checks.json`。断言数量不是功能数量，更不是七个平台逐项联网成功次数。

## 尝试但未完成

执行 `./gradlew :core:test :app:assembleDebug --console=plain` 后，获取官方 Wrapper 的两个 URL 均因 DNS 解析失败而退出，未进入 Gradle/Kotlin Android 编译。当前环境也没有 Android SDK、模拟器或真机。**没有 APK、没有 Android Studio 构建成功截图、没有 UI 自动化/真机/平台联网验收。**

## 自行复现

完整环境安装后：

```sh
sh gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
```

只运行当前本地可用的纯 Kotlin 检查：

```sh
kotlinc core/src/main/kotlin/com/luma/core/*.kt core/src/test/kotlin/com/luma/core/CoreChecks.kt -include-runtime -d core-checks.jar
java --add-modules jdk.httpserver -jar core-checks.jar
kotlinc app/src/main/java/com/luma/downloader/data/UiSettings.kt app/src/main/java/com/luma/downloader/data/MotionPolicy.kt app/src/main/java/com/luma/downloader/data/MaterialPolicy.kt app/src/main/java/com/luma/downloader/data/Appearance.kt app/src/test/java/com/luma/downloader/PolicyChecks.kt -include-runtime -d policy-checks.jar
java -jar policy-checks.jar
python3 tools/check_source.py
```

Windows PowerShell 的通配符展开不同；优先执行 Gradle 对应测试任务。CLI 手动生成的 test JAR 不应放入 app 或发布包。

`tools/KotlinSyntaxCheck.kt` 使用 Kotlin 编译器的 PSI API，需要本机 `kotlin-compiler.jar` 在 classpath 中；它只做语法解析，不替代上述 Android Gradle 测试。

## 下一个真正的验收清单

编译通过后，分别在 Android 8/10/13/15 手机、横屏平板测试：冷启动、切页/返回/旋转/分屏、登录取消与过期、下载暂停恢复、音视频分轨合并、网络变更、满磁盘、删除确认和取消、进程被杀后的账本恢复、通知权限。再用自有或获授权内容的真实链接逐平台检查，记录账号状态和直连网络条件。

本版本未承诺所有链接都能解析、所有会员画质都可取或所有统计字段必定存在。未提供的数据保留为空。
