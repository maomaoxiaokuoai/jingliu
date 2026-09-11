# 本次验证范围：0.7.7

**没有完成Android Gradle全工程编译，也没有本机Android运行截图、APK、真实Python Android wheel导入或平台下载验收。** 下面是实际执行的主机检查，不能把mock/语法检查说成APK成功。

## 当前源码最终执行

`tools/verify-local077-host.sh`整脚本最终退出码0，日志在`tests/local077/final/host-full.log`与`host-full.exit`。

| 项目 | 本次实际结果 | 覆盖 / 不覆盖 |
|---|---|---|
| core当前生产代码编译 | JVM target17成功 | Kotlin1.9.0/JDK21，不含Android |
| 现有核心回归 | 76条断言通过 | 合成平台响应、真实localhost HTTP |
| 既有下载/备用协议 | 31项通过 | B站节点、选择、续传、历史服务协议；不是新Python平台联网 |
| 新本地引擎协议/映射 | 15项通过 | 私有IPC输入/输出、Cookie范围、缺失字段、图集/HLS、绑定引擎、旧格式提示 |
| Python Android适配层 | 19项通过 | **真正的本次适配器** + 显式上游classes/registry测试替身 + 真HTTPX MockTransport；无真实上游依赖/互联网 |
| 既有展示/动效策略 | 183条通过 | 数据与默认规则；不是UI帧率 |
| 菜单位置 | 16项通过 | 纯位置规则 |
| 覆盖层生命周期 | 11项通过 | 纯状态/代次规则 |
| 源码结构 | 91项通过 | XML、资源及工程结构，不解析Android符号 |
| 新接线约束 | 18项通过 | 确认框无引擎选择、无活动服务地址、私有进程、真实库调用等 |
| Kotlin PSI语法 | 112文件、0语法节点错误 | 包括测试/工具/显式类型替身；不是正式Kotlin2.1.20 Android编译 |

上述类别不合并宣传“全App通过多少测试”。历史`fix-*`与`package-*`目录仍是旧记录。

## 新Kotlin局部类型检查

`tests/local077/typecheck-with-stubs.log`和`.exit`：当前core、LocalParseVideoClient/Service、ExtractorRouter、MediaRuntime在**外部Android/Chaquopy类型替身**下编译成功。替身源码在`tests/local077/typecheck-stubs/`，不属于app源码集，不打包进APK，也不验证真实SDK/Gradle插件/ABI接口。

## Android构建尝试

`tests/local077/android-build-attempt.log`：执行`:app:assembleDebug`时官方Wrapper两个下载地址均DNS失败，Gradle尚未启动。当前环境没有Android SDK，因此未得到依赖解析、插件DSL、R资源链接、Manifest合并、完整Compose类型检查或APK证据。没有为了消除错误使用假jar、关闭TLS或伪造APK。

需要用户实际构建后的最低设备检查：`app/src/androidTest/.../LocalParserDeviceTest.kt`，真实连接私有进程并导入库。该测试源已写入，**尚未运行**。UI也新增同样的显式自检按钮，没有后台假登录或假下载。

## 有意保留的失败和限制

首次本地编译暴露了压缩Kotlin条件表达式缺空格的问题，已经修正，初始日志留在`initial/`。新结构检查最初对说明文字采用了错误的精确子串，以及Application路径写错；这两项属于测试脚本问题，已修正并重新执行。完整脚本最终退出码0。

Python依赖锁定到上游commit+直接版本，不代表传递依赖锁、供应链或native wheel安全审计已经完成。没有联网安装并执行完整上游，因此不能说25个站点全部成功或手机没有任何解析错误。返回缺失、多画质/合集不受支持、平台地区或账号验证会如实失败，不生产占位数据。

## 复现主机测试

具备JDK17+、Kotlin编译器和Python3.11（测试需要httpx0.28.1）的环境：

```bash
./tools/verify-local077-host.sh
```

这个命令不下载安装真实parse-video-py，而是运行明确的测试替身。输出默认为`.host-checks/local077/`，不进入ZIP。Kotlin PSI工具另见`tools/KotlinSyntaxCheck.kt`，需要Kotlin compiler jar。

实际Android构建与设备验证步骤见`BUILD-0.7.7.md`。设备联网测试至少覆盖有效/失效Cookie、取消/重试、多个任务排队、后台进程回收、国内直连和可访问海外网络、HLS清单合并播放、图集选择，以及旧任务重解析。
