# 0.7.7 编译与本地引擎检查

## 新增的是电脑构建依赖，不是让手机另装一个服务器

保留 Gradle8.11.1、AGP8.9.2、Kotlin2.1.20、SDK35、原 Compose/Haze 与 yt-dlp 版本。新增 Chaquopy17.0.0、APK CPython3.11。

官方说明：Chaquopy 的 pip 构建需要电脑存在匹配 Python 的主/次版本；补丁版本不要求相同。本包选3.11，因此 **Python3.10/3.12/3.13 不能直接替代此次 buildPython**。选择3.11是为了保留原工程 arm64-v8a、armeabi-v7a、x86_64、x86 四种ABI，不代表这些全部已经实机验收。

## Windows

先执行：

```powershell
py -3.11 --version
py -3.11 -c "import sys; print(sys.executable)"
```

应该显示3.11.x及对应解释器路径。不必在系统 Python 全局手动 pip 安装 parse-video-py。Gradle会按固定配置打包自己的依赖。

若 Gradle 自动识别失败：

```powershell
Copy-Item .\python-build.properties.example .\python-build.properties
```

编辑成你的实际路径，不带外层引号、使用正斜线，例如：

```properties
buildPython=C:/Users/maomao/AppData/Local/Programs/Python/Python311/python.exe
```

这里只是示例，不能保证你的 Python 安装在此处；以上 `sys.executable` 命令给出实际位置。不要把路径指向 Python 3.12、虚拟环境的缺失解释器、安装器或目录。

Android Studio 的 Gradle JDK 沿用JDK17/JBR21，与buildPython是两项不同配置。

## Wrapper与完整编译

解压到独立目录，Open工程根目录，不要只开app。可复制旧版已经校验的官方 Gradle8.11.1 Wrapper JAR到 `gradle/wrapper/gradle-wrapper.jar`，也可执行 `setup-wrapper.bat`。不要从陌生网盘取JAR或取消校验。

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain --stacktrace
```

成功输出：`app\build\outputs\apk\debug\app-debug.apk`。

```powershell
.\gradlew.bat :app:assembleProfile --console=plain --stacktrace
```

成功输出：`app\build\outputs\apk\profile\app-profile.apk`。Profile同本机debug签名，仅做性能比较。不要删除登录数据排查编译问题。

第一次会下载固定提交的上游源码、纯Python包和Android原生wheel；不能用电脑的Windows/Linux `.pyd`、`.so`、wheel替代Android版本。构建阶段的下载需要电脑网络，和手机运行是否需要服务器是不同问题。安装后没有远程解析地址或服务运行要求。

## 装好APK后的最低检查

解析页选择“parse-video-py · 手机本地”，点击“检查本地解析组件”。它真实经过Binder连接私有解析进程，导入上游注册表、lxml、parsel、PyYAML、fake-useragent数据并返回版本与解析器数。

这个检查不联网、不创建视频/任务、不伪造账号。成功只说明Android Python依赖实际装好，之后仍需用自己有权保存的链接测试各平台。第一次初始化有解包和导入开销，后续复用；没有测出首次耗时的固定数值。

Android仪器测试也附有同样的本机导入检查（当前未运行）：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.downloader.LocalParserDeviceTest --console=plain --stacktrace
```

测试需要连接设备/模拟器。本仓库中其他仪器测试按其各自说明执行，不要把设备测试源码存在当作已通过。

## 常见问题

- `No Python executable found` / Python版本不匹配：检查3.11与`python-build.properties`；不用重装Android Studio。
- `No matching distribution ... android...`：核对依赖固定版本、Python3.11、ABI、官方wheel源；不要强装桌面轮子。当前环境没有完成整套解析，因此仍可能有依赖解析兼容问题。
- `ModuleNotFoundError`、本地组件未正确安装：按引擎错误代码检查构建输出，并执行组件自检；不是“还需要一个服务器”。
- 国内连接失败：平台会话、风控、地区/链路与接口变化仍可能导致失败；不绕过验证码或关闭TLS验证。
- 返回资源只有一个源文件：上游没给多档格式，界面不会捏造。YouTube/TikTok请选yt-dlp。
- B站旧任务提示格式不存在：重新解析选画质，账号无需清空；本地B站兼容分支与旧远程`pv-source`不是同一个格式编号。

## CI

已有可选CI配置新增Python3.11准备步骤。它未在当前环境执行，也不要求连接GitHub账号才能本地构建。完整工程可以直接在Android Studio打开。

官方参考（2026-09-09查阅）：
https://chaquo.com/chaquopy/doc/current/android.html
https://chaquo.com/chaquopy/doc/current/java.html
https://developer.android.com/build/releases/past-releases/agp-8-9-0-release-notes
