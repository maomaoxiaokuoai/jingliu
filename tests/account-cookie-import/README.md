# 本轮验证

运行 `bash tests/account-cookie-import/run-checks.sh`，需要本机 JDK17+、Kotlin 编译器及其 coroutine JAR、Python3（仅运行静态检查）。脚本不下载依赖，默认结果输出 .host-checks/account-cookie-import。

core_cookie 检查运行真实生产核心类；API 替身只用于外部 Android/Compose 类型约束，不实现实际设备 UI 或存储。它们不在 app 源码集，不打入 APK。

本轮实际结果及边界见 verification.json 和日志。没有设备测试，没有 APK，没有全工程 Android 编译。旧 tests/account-cleanup 是历史要求，不能将其删除导入按钮断言当作本轮要求。
