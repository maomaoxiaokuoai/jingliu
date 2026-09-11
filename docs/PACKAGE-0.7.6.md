# 0.7.6 打包、合并与验证记录

## 构成与文件保留

由 0.7.5 完整源码底包合并 0.7.6 已交付单文件，并补写缺少的配套接线。根 README 已更新，旧 README 存档为 `README-0.7.5-package-archive.md`。旧测试日志保留且标注历史；新检查只看 `tests/package-0.7.6/`。

保留 `app/core`、可选 `auth-bridge`、原图标、样式资源、主导航与设置的性能实现。新增 Python 服务为可选目录，不加入 Gradle 模块、不在默认自动解析中使用。源码完整不等于已打入所有构建依赖或发布所需凭据。

## 本次实际执行

| 项目 | 结果 | 范围 |
|---|---|---|
| 当前全部 core + 可选授权服务的主机编译 | 通过 | Kotlin/JVM 1.9.0、JDK 21、`-Xjdk-release=17`，不是正式 Android 插件编译 |
| 既有 core 回归 | 76 条断言 | 样例 + localhost 实际字节传输 |
| 扫码协议 | 20 项 | 样例，不连接真实账号 |
| 账号 / QR 导出策略 | 27 项 | 主机规则，不验证手机相册写入 |
| 可选授权服务 | 26 项 | 样例与本机 HTTP |
| 新下载 / 画质 / 备用接口 | 31 项 | 新测试源码 `DownloadPackagingChecks.kt`；localhost CDN 候选、服务认证和重定向 |
| 新存储 / 路由接线 | 9 项 | 实际 TaskStore/ExtractorRouter + 标注的 Context/引擎替身；不验证 Android/yt-dlp |
| 设置 / 动效策略 | 183 条断言 | 新 parserEngine 选项与原策略；不是画面帧率 |
| 菜单定位 | 16 项 | 几何规则 |
| 覆盖层 | 11 项 | 生命周期规则 |
| Python 包装层 | 9 项 | 注入测试解析函数的真实 localhost HTTP，未安装上游库 |
| Kotlin/KTS 语法 | 97 个文件、0 个语法错误节点 | 不解析 Android/Compose 依赖类型 |
| 资源 / 源码结构 | 90 项 | 65 个生产 Kotlin 文件；XML、资源和设置引用 |
| Android assembleDebug | 未完成 | 官方 Wrapper 下载 DNS 失败，尚未执行 Gradle；无 SDK/ADB/APK |
| 真机 UI / 账号 / 视频下载 | 未执行 | 没有真实设备与已授权平台账号的端到端证据 |

数字包含不同范围，不应加在一起称为“全工程验收项目数”。确切输出在对应日志和 verification.json 中。

## 复现主机检查

安装本地 JDK 17+、Kotlin 编译器、Python 3，并准备 kotlinx-coroutines-core-jvm.jar：

```bash
export KOTLIN_HOME=/path/to/kotlin
./tools/verify-package076-host.sh
```

可设置 `KOTLIN_COROUTINES_JAR` 指向该 JAR。输出默认写入 `.host-checks/package-0.7.6`；临时测试 JAR 不参与 Android app 构建，也不放入源码发布 ZIP。

Android 环境完整时应另跑：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain --stacktrace
```

再用相同签名在设备上更新，验证实际 B站账号、合集选择、画质、不同阶段重试/删除与真实媒体可播放性。没有通过“重复登录”掩盖未知失败；失败时先复制脱敏诊断。

## 本轮最初错误记录

接线测试替身初稿的条件表达式缺少空格，局部编译失败记录为 `wiring-initial-error.log`；修正测试替身后重新编译并通过 9 项。该错误不在生产代码中。其他空的 `*-compile.log` 表示对应主机编译没有输出，不是 Android 成功日志。

此前单文件附带的 40 项测试日志没有对应完整测试源可用，本轮没有杜撰成复现结果。保留为 `tests/fix-007/historical-download-repair.log`，本次31项检查使用随包真实测试源码。

最终打包会重建 `source-manifest.json`，ZIP 逐项校验 SHA-256 和 CRC，检查图标字节与底包一致、未包含用户密钥、Cookie 文件、机器 local.properties 或字体。ZIP 通过这些检查不等于能够跳过 Android 编译。

## 交付前全脚本复跑

`tools/verify-package076-host.sh` 已完整执行，退出码 **0**，输出见 `script-run.log` / `script-exit.txt`。它重新编译了实际 core、可选授权服务、展示策略，以及带明确外部类型替身的 TaskStore/ExtractorRouter，并执行本机 HTTP 服务测试；不是仅重放已有日志。以上表格与全脚本是同一组检查，不重复计算。
