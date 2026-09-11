# FIX-007 · 完整包中的下载与备用接口接线

## 本次整合依据

底包：当前会话实际挂载的 `Jingliu-Android-0.7.5-完整源码.zip`（248 项文件）。
合并此前单独交付的 `ParserEngineControl.kt`、`DownloadWorker.kt`、`DownloadOptions.kt`、`BiliMediaPolicy.kt`、`DownloadFailure.kt`，以及 Python 包装服务文件。

仅这些文件不能构成能调用的完整新版本。本次补齐了它们依赖的模型字段、错误代码、媒体节点规则、传输包装、备用服务 JSON/HTTP 客户端、Android 加密配置、路由、任务序列化、ViewModel 与解析/设置/下载页接线。没有声称这些补齐文件是已回收到的原始附件。

## 主要修改对应文件

- `core/.../Model.kt`：`Format` 保存视频/音频备用 URL 和 qn，`Media` 保存实际提取路径；平台域及传输端口规则调用。
- `MediaTransportPolicy.kt`：普通 HTTPS 和官方 B站媒体域 4483；抖音分享 ID 与有限公开接口路径。
- `BiliMediaPolicy.kt` / `Extractors.kt`：实际 DASH 格式、备用节点、播放请求头和拒绝试看/DRM；有限的原生抖音分享接口回退。
- `CandidateTransfer.kt`：沿用真实续传实现，最多四个同流 URL，变化的 CDN 身份不能混用旧片段；不重试取消/频控以伪造成功。
- `DownloadOptions.kt`：实际格式列表、每集 qn/分辨率的固定策略。
- `PrivateParser.kt`：受控服务 origin、Basic、禁跨目标重定向、有限 JSON 解析；不向自建服务传平台 Cookie。
- `engine/ParserServiceStore.kt`：独立的 AES-GCM 配置存储，尚未 Android 设备验证。
- `engine/ExtractorRouter.kt` / `AppGraph.kt`：四种引擎分流；auto 不偷偷使用私有服务。
- `data/TaskStore.kt`：新字段兼容旧 JSON，无需清数据。
- `data/LumaViewModel.kt`：合集读取第一项画质、取消、引擎/画质提交、任务持有引擎快照。
- `ui/ParserEngineControl.kt`：已有的真实画质弹窗及服务配置界面已与 ViewModel 连通。
- `ParseScreen.kt` / `SettingsScreen.kt` / `DownloadsScreen.kt`：显示引擎、合集确认、阶段和诊断。
- `DownloadWorker.kt`：执行所选解析方式与画质策略、同轨备用地址和脱敏失败信息。

## 兼容与限制

既有账号存储和登录协议未替换；媒体请求仍按原有 Cookie 域/路径规则执行。没有将 OAuth 公开身份资料强行当作浏览器 Cookie，也没有声称新的登录 SDK 已接通。原玻璃、颗粒、MotionPolicy 和平台品牌图片不降级。

备用接口仅按选择调用。Debug 模拟器可使用 `http://10.0.2.2:8000`，此明文例外只在 Debug 资源覆盖；Profile/Release 不允许它，实体机配置自己的 HTTPS 地址。没有全球明文网络开关。

备用服务可能返回一个源地址而非多个清晰度，且返回统计不完整。客户端不能凭空补全画质、粉丝量或收藏量。B站分P限制、直播/DRM、地址过期、服务 IP 与手机 IP 不同等情况仍可能失败。

## 来源

有限的原生抖音分享接口兼容参考了用户指定上游的固定提交：
https://github.com/wujunwei928/parse-video-py/blob/5fcf87256edb5ffcdebf0e4aac2a5a41745da76e/src/parse_video_py/parser/douyin.py

备用服务调用真实的上游库；依赖及 MIT 上游许可证由 pip 获取，不把本次主机测试中的测试解析器作为生产实现。
https://github.com/wujunwei928/parse-video-py

本轮没有联网安装或调用上游平台提取器；服务测试注入测试函数，并且只在 test_server.py 中使用。

## 测试

见 `PACKAGE-0.7.6.md` 与 `tests/package-0.7.6/verification.json`。Android 构建尝试未越过 Wrapper 下载步骤，不使用主机编译成功代替 Android 编译。
