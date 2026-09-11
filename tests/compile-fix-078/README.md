局部类型检查：真实 Activity、core、coroutines，外部 Android/Compose/AppGraph 类型替身。
不是真实 Android 构建或运行验证。默认前端和实验 K2 前后对照见日志。
执行：bash tests/compile-fix-078/run-typecheck.sh
需要 Kotlin 编译器分发目录中含 kotlinx-coroutines-core-jvm.jar、JDK 17+ 和 UTF-8 环境。
原源码为 .txt，不参与生产编译；api-stubs 不能移入 app。
