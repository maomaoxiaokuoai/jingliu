# 账号入口精简检查

api-stubs/ 是明确的 Android/Compose 等外部接口测试替身，生产 core 与 coroutines 用真实源码/库。
三个修改后的完整 Kotlin 文件在这些接口下编译，捕获参数、重载及基础类型错误。
没有运行 Android/Compose 编译插件，也没有真实 UI/GPU/WebView/平台联网验收。
原始 tests/compile-fix-078 等目录只是历史记录，不作为此次执行结果。
