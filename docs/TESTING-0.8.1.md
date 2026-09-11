# 0.8.1 本次打包与验证

## 实际执行

| 检查 | 当前结果 | 限制 |
|---|---|---|
| 新 LensPolicy | 23 项通过 | 真实 Kotlin/JVM，无 Android/AGSL/GPU |
| 展示/动效策略 | 191 条通过 | 参数/默认值与弹簧策略，不是操作录像 |
| 菜单位置 | 16 项通过 | 纯位置与几何规则 |
| 覆盖层生命周期 | 11 项通过 | 纯状态与代次检查 |
| 原核心 | 76 条通过 | 合成平台数据及实际 localhost HTTP |
| 下载/候选节点 | 31 项通过 | localhost 字节/策略，不是平台实网 |
| 本地引擎协议映射 | 15 项通过 | 不导入 Android 上游 Python 运行时 |
| Cookie/二维码访问策略 | 27 项通过 | 不操作 Android 相册或真实账号 |
| 扫码协议 | 20 项通过 | 协议样例，没有真实扫码 |
| 现有恢复规则 | 15 项通过 | 本机 HTTP，无平台验收 |
| 控件调用名/位置 | 113 处无该类冲突 | Kotlin PSI，只检查参数名字/位置，不是类型解析 |
| XML/资源/设置引用 | 98 项通过 | 源码结构，不做 Android 资源链接 |
| Kotlin PSI 语法 | 315 个文件，0 语法节点错误 | 含历史测试/明确替身，**不是 Android 编译** |
| 官方 Gradle 构建 | 退出码 1，Wrapper DNS 失败 | 未进入依赖解析或 Android 编译，没有 APK |

具体日志在 `tests/package-0.8.1/`。原 `tests/optics090/android-build-attempt.log` 来自上一轮，只作为历史材料保留。其他历史 tests 目录不计成本轮验证。

新的调用检查器第一次把 Kotlin 尾随 lambda 当作普通位置参数，误报 10 处冲突；已按 KtLambdaArgument 映射到最后一个参数修正检查器，并重跑为 0。初始输出保留在 glass-calls-checker-initial.log，没有为通过断言随意删除生产参数。

本机：Kotlin/JVM 1.9.0，JDK 21，Java 17 输出目标；Android 工程声明的 Kotlin 2.1.20 未在本环境完成完整构建。没有制造假 SDK、假 APK 或把类型替身的运行说成真实 Android 界面运行。

## 打包核对

- 基线为 0.8.0 进度命名修复完整 ZIP，校验其 CRC。
- 对照基线确认全部平台图标及其它图片原字节保留，core、下载/解析、SessionVault 等业务源未在打包时重写。
- 业务入口只改界面组件调用及独立窗口的材质容器，账号信息不加入 ZIP。
- 最终 ZIP 自检 CRC，逐文件 SHA-256 记入 source-manifest.json；清单不包含自身哈希。
- 排除构建缓存、测试输出 JAR、字体、真实凭据与机器路径。

## 仍需 Android Studio / 设备验收

1. :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleProfile。
2. API 26/31/33+ 的实际构建、链接和渲染，特别是 AGSL 初始化、GraphicsLayer 录制与缩放。
3. 四 Tab 连续切换、一级/二级返回、菜单快速重开、滚动时边缘坐标稳定、暗色和减少透明度。
4. 原生输入/搜索、所有按钮的禁用态和焦点、TalkBack、软键盘遮挡、取消拖拽。
5. iQOO Z8 对比同设置 Profile 的帧时间、内存、耗电，不只看平均 FPS。
6. 原有实际平台登录/下载/播放器验收；这轮没有新增或保证平台能力。

此源码包是完整整合工程，不是已经达到上述验收标准的发布版。
