# 0.7.8 整合清单与验证边界

## 来源及补齐工作

底包：`Jingliu-Android-0.7.7-完整源码.zip`，SHA-256 `04bb74685af76610cddad639411dca3faaa67cb87177cb0a8704d666305e96fd`。

上一轮实际可取到的生产源码只有 6 份：media_compat.py、MediaRequestPolicy.kt、CandidateTransfer.kt、TransferRecoveryPolicy.kt、MediaPreviewActivity.kt、EmbeddedLoginActivity.kt，以及3份历史日志。**因此本次不是把那一个稀疏文件夹改名压缩**：先解压完整底包，再合并，并补写缺失的 MediaHttp、BrowserSessionPolicy、预览/登录入口、模型字段、依赖、Manifest、HTTPX 捕获/跨请求 Cookie、下载 Worker、重试迁移和进度落盘接线。

两份 Activity 还修正了播放器回调生命周期保护、播放器音轨独立请求头、WebView 不重新导入已过期 Cookie，以及字符串分支的可读性。上一轮未提供源码的测试只保留其原日志在 `tests/repair078/`，不能声称这些历史日志由本次完整工程产生。

## 本次实际执行

| 检查 | 结果 | 证据/范围 |
|---|---|---|
| 当前 core + 测试 Kotlin 编译 | 成功 | Kotlin/JVM 1.9.0，JDK21，Java17目标；不含 Android |
| 既有核心 | 76断言 | 合成数据与实际 localhost HTTP |
| 下载、备用协议回归 | 31项 | JVM协议和本机HTTP，非线上媒体 |
| 本地引擎协议映射 | 15项 | IPC/schema规则，非 Android Binder |
| 账号/二维码导出规则 | 27项 | 纯规则，不是账号实际登录 |
| 扫码协议 | 20项 | 样例协议 |
| 展示/动效策略 | 183断言 | 纯策略，不是画面或帧率 |
| 菜单与覆盖层 | 16项、11项 | 状态/定位规则 |
| 新媒体/浏览器/续传规则 | 15项 | Package078Checks.kt，真实localhost字节续传与节点失败 |
| Python原适配回归 | 19项 | 显式上游测试替身+HTTPX MockTransport |
| Python新图片/快手兼容 | 12项 | 真实本次适配代码+合成页面，非真实上游在线账号 |
| 设置/任务保存 | 7项 | 当前生产 TaskStore/SettingsStore，实际临时文件，Android Context由测试替身提供 |
| 源码/资源结构 | 93项 | XML、资源、引用检查，不解析 Compose 类型 |
| Kotlin PSI | 123文件，0语法树错误 | 语法而非 Android 全工程类型检查 |
| Android :app:assembleDebug | **未完成** | Wrapper 官方两个下载域名 DNS 失败；退出1，Gradle没有启动 |
| APK / 设备 / 平台 | **未执行** | 无APK，无iQOO/模拟器画面和真实小红书/快手测试 |

日志均位于 `tests/package-0.7.8/`。不要把不同测试类别合起来宣传“全软件数百测试通过”，也不要把表中的无语法错误说成可以零错误编译 Android。

本轮主机脚本见 `tools/verify-package078-host.sh`，需 JDK17+、kotlinc、Python3.11和httpx0.28.1。可单独执行 `tests/package-0.7.8/test_media_compat.py`。测试替身文件在 `tests/package-0.7.8/host-fixtures`，不在 app 生产源码集，不会打进 APK。

## 尚未完成的发布验收

Android Gradle/Chaquopy/Media3依赖解析、完整类型检查、原生wheel导入、真实预览解码、WebView的各平台登录、Cookie/存储行为、iQOO Z8滚动帧率/120Hz、电量温度策略、进程强杀与媒体库恢复、完整平台网络验证。保留了原有未配置官方SDK的边界。

本包包含直接依赖声明，不离线打入上游所有 Python wheel、Maven artifact、Gradle或Android SDK。上游动态站点行为不因打包完成就得到保证。完整性校验只能确认文件齐全与未损坏。

## 接入参考

Media3播放器初始化、线程与释放：https://developer.android.com/media/media3/exoplayer/hello-world
Media3发布记录（本工程固定1.7.1，不跟随网页最新值升级）：https://developer.android.com/jetpack/androidx/releases/media3
现有上游固定提交：见根 python-runtime-requirements.txt。
