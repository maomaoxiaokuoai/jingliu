# 镜流 GlassFlow 0.7.7 · parse-video-py 手机本地引擎

**完整 Android 开发源码，不是增量补丁，不含 APK。** 已将 0.7.6 的远程 `parse-video-py` 入口替换为 APK 内嵌 Python 库的本机执行路径；删除应用里的服务地址、Basic 账号/密码配置入口。点击合集下载只选择画质，不再次询问解析引擎。

> **重要新增构建条件：编译电脑需要 Python 3.11.x。手机不需要另装 Python，不需要部署解析服务器。** 这是 Kotlin 宿主 + Chaquopy 内嵌真实上游库，不是把 Python 全项目逐行改成 Kotlin，不再通过镜流的旧HTTP包装服务执行解析。上游各解析器仍会访问其实现使用的联网端点；本次未完成逐站网络去向审计。
>
> **验证状态：本次主机核心、映射、网络适配与结构检查通过；Android 整工程、真实 Python Android wheel 导入与平台联网尚未验收。** 获取官方 Wrapper 时仍遇到 DNS 失败，没有生成 APK。不得把主机测试说成设备成功。

[编译教程](docs/BUILD-0.7.7.md) · [迁移分析与实际边界](docs/LOCAL-PARSE-0.7.7.md) · [测试记录](docs/TESTING-0.7.7.md)

## 直接使用

完成编译安装后，在解析页选择 **parse-video-py · 手机本地**。无需填写 IP、域名或服务器账号。可以先点“检查本地解析组件”；它在手机真实加载上游及依赖，成功只表示组件能导入，**不表示每个平台已经联网验证**。

解析返回后，使用原有图集/合集多选。合集点击下载只显示真实画质、首项大小与确认/取消；沿用发起本次解析时的引擎，不受随后修改的全局设置影响。每集重新取得时效地址时仍使用已绑定的引擎；“不再选引擎”不等于永远复用已过期的媒体 URL。

四个入口均为本机执行：

| 入口 | 当前实现 |
|---|---|
| 自动 | 保持原生 Kotlin 优先、必要时 yt-dlp；不会静默切入 Python 本地新引擎 |
| 原生 Kotlin | 现有国内解析、会话与下载能力 |
| yt-dlp | 原有 Android 封装引擎，仍负责 YouTube/TikTok 等支持的链接 |
| parse-video-py · 手机本地 | 私有 Android 解析组件内执行真实固定版本库；B站是明确标注的原生兼容分支 |

## 为什么 B站有兼容分支

为避免退回上游第一分P、有限画质的行为，B站在此引擎下继续调用现有 Kotlin/WBI/DASH 路径，保留本机登录会话、分P、合集、实际画质和同轨备用节点。界面说明及解析结果明确写“原生兼容分支”，**不是把旧原生代码偷偷命名成上游 Python 的全平台移植**。

其他受该固定提交覆盖的链接调用真实上游解析类。YouTube/TikTok 不在该上游映射中，不虚构支持；请使用 yt-dlp。点赞、收藏、粉丝量、大小、多档画质等只有返回了才显示；只有源文件时，不制造 4K/1080p 选项。Live Photo 仍仅处理其静态图片，不声称完整配对导出。

## 源码目录

```text
Jingliu-Android/
  app/                                 Kotlin UI/任务、Android 资源
    src/main/python/jingliu_local/      真正调用上游库的本机适配器
    src/main/java/.../engine/local/     私有绑定组件与 Binder 客户端
  core/                                统一结果模型、下载与引擎/画质规则
  auth-bridge/                         原有可选身份授权服务，默认不导入
  python-runtime-requirements.txt       构建时固定的上游提交与 Python 依赖
  python-build.properties.example      编译电脑 Python 路径示例
  branding/                            原有镜流和平台图标
  docs/                                新旧说明和明确的能力边界
    legacy-0.7.6/                       旧远程解析服务仅作历史存档
  tests/local077/                      本次测试与证据（不进入 APK）
  tools/verify-local077-host.sh         主机检查重跑入口
  settings.gradle.kts
  setup-wrapper.bat
```

旧 `parse-video-service/` 已移入历史文档区，不属于应用运行路径，不需要启动。原来的 `auth-bridge` 是可选开放平台身份授权用途，不是解析服务器；普通下载无需部署它。

## 打开工程

1. 安装/确认编译电脑的 Python 3.11.x：`py -3.11 --version`。已有 3.11.x 可继续使用。
2. Android Studio 打开包含 `settings.gradle.kts` 的根目录。JDK 17/JBR21、SDK35、Build-Tools35.0.0 继续沿用。
3. 自动找不到 Python 时，复制 `python-build.properties.example` 为 `python-build.properties`，填自己的 **Python3.11 python.exe** 路径，建议用 `/`。
4. 新目录不含官方 `gradle-wrapper.jar`：从旧工程复制已可用的同版本文件至 `gradle/wrapper/`，或运行 `setup-wrapper.bat` 校验下载。
5. Sync 后点击 Run，或执行：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain --stacktrace
```

只有实际构建成功才会生成 `app\build\outputs\apk\debug\app-debug.apk`。Profile 变体保留：`:app:assembleProfile`。

**第一次构建电脑需要获取 Gradle/Maven、上游固定提交和 Android Python wheel。ZIP 是完整项目源码/集成与依赖声明，不是离线依赖镜像；没有把整个上游 archive 和所有 wheel 离线打进去。** APK 一旦成功构建安装，解析直接在手机执行，不在运行时 pip 安装或下载执行代码；访问平台/CDN 当然仍然需要网络。

## 更新与隐私

版本 `0.7.7-local-parse-video`、versionCode14；applicationId 仍为 `com.luma.downloader`。保留同一签名更新，不需要卸载或清空 Cookie、文件和外观配置。玻璃、颗粒、弹簧和页面复用实现保持原字节，未自动打开“省资源”。

旧引擎 ID `parse_video_py` 保留但语义切到本地。旧远程服务配置不再读取/发送，不自动清除私有凭据，以保留回滚余地。旧 B站备用任务若使用 `pv-source` 这种旧远程格式 ID，会提示重新解析并选择当前画质，不把它映射成不确定的新档位。其他旧任务仍需按真实返回地址和格式兼容性验证。

本地 Cookie 只传入同一 APK 私有进程，按平台、域、path、secure、expiry 使用；不会上传到解析服务器。不会因打开 App、组件导入或缺失字段而伪造登录、下载或统计结果。

许可证及分发要求见 `LICENSE` 和 `THIRD_PARTY_NOTICES.md`。本版没有完成平台实网、Android 编译、性能或安全发布验收。
