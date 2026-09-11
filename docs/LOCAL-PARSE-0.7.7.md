# parse-video-py 从服务调用迁移为手机本地执行

## 1. 不是把网页服务改一个名称

0.7.6：分享链接 → 用户配置的HTTPS包装服务 → 上游Python库 → 手机下载。

0.7.7：分享链接 → Kotlin ExtractorRouter → APK私有绑定组件 → 内嵌真实Python库 → 统一Media模型 → 原有Kotlin下载/FFmpeg。

`LocalParseVideoService` 是Android组件名称，不是网络服务器：同应用UID、exported=false、进程`:parse_video`、通过Binder传入有大小限制的消息，没有监听端口，没有HTTP到localhost，没有FastAPI/Uvicorn，也不在手机运行pip或下载可执行代码。

本版没有宣称把整个上游逐行转成Kotlin。纯Kotlin全面重写会重复维护25个解析器、HTML/XPath、签名和返回结构，容易造成平台覆盖回退。直接在Android内嵌上游库，保留Kotlin UI/任务与native B站专用路径，是这次的工程选择；不是三种路线的实测性能排行。

## 2. 调用的真实库和构建依赖

固定提交：`5fcf87256edb5ffcdebf0e4aac2a5a41745da76e`。Gradle的Chaquopy pip配置获取该上游archive；运行时适配器导入 `parse_video_py.parser.video_source_info_mapping`，根据真实hostname选择上游class，然后调用 `await parser.parse_share_url(source)`。没有用fixture替换APK里的解析器。

测试代码中的registry/classes替身只位于tests目录，测试HTTPX也使用MockTransport；它们不进入app主源码集。依赖源码通过构建过程进入APK，而非这个ZIP离线vendoring了全部源码与wheel；当前环境没有成功联网安装该完整依赖集合。

上游声明Python>=3.10、aiohttp、fake-useragent、lxml、parsel、httpx、jmespath和PyYAML；web/cli额外依赖独立。本版不安装web/cli/mcp extras。[R1]

| 依赖/机制 | 迁移处理 |
|---|---|
| Python | Chaquopy17 + CPython3.11，保留既有四种ABI；只在选用/自检时启动 |
| lxml、PyYAML | 固定官方Android cp311 wheel族版本；没有把Windows扩展复制到手机 |
| aiohttp及原生依赖 | 使用声明兼容的cp311版本集合；适配器网络主路径使用HTTPX，不启动aiohttp web服务 |
| fake-useragent | 数据随APK打包，明确提取该包的文件，不在首次运行下载UA数据库 |
| HTTPX | 使用带每请求Cookie域规则、TLS、超时、大小上限的实例；修正已import的客户端别名 |
| asyncio | 私有进程单worker串行拥有event loop，避免多个请求污染网络hook/取消上下文 |

固定native依赖采用可查到的Android3.11 wheel兼容组合，不表示版本“最新或已安全审计”；纯Python传递依赖仍由pip在约束内解析。生产发布之前应保存解析后的锁定/SBOM与做依赖安全检查。当前不提供全ABI构建成功证据。[R2][R3][R4]

## 3. 为什么不直接使用旧 yt-dlp 的Python目录

旧运行组件首先为yt-dlp自己的压缩包及FFmpeg准备，没有证明其环境能够导入这套lxml/HTML/native扩展。混用PYTHONHOME/PYTHONPATH或桌面wheel不是可靠移植。

新增Python运行时只在私有进程加载；旧yt-dlp下载进程保留原环境，并移除可能继承的PYTHONPATH。这样降低路径/模块/ABI相互污染风险，代价是APK更大、另一个缓存进程占内存、首次库导入有成本。没有真机PSS、启动时延或CPU对比数字，不能称零开销。

## 4. 平台覆盖与B站分支

该提交注册了包括抖音、快手、小红书、X、B站等25个上游解析器，没有YouTube/TikTok注册项。映射存在不等于这些平台今天每条链接都成功；原始mapping只支持列出的分享域，不能把同一平台所有私有接口都算进去。[R5]

上游B站使用首分P与有限播放请求，不能因此丢掉镜流原有会话、DASH、qn、多P及备用CDN。本地引擎对B站有明确的Kotlin兼容分支，并在选项说明及解析结果标注。**B站不执行上游BiliBili parser类**，也不伪称所有平台都纯Python运行。其他上游支持的链接直接执行对应的真实类。

本次没有修复所有网站的签名算法、替用户配置开放平台授权或绕过DRM/验证码。没有完成上游全部解析器的网络端点审计；“无需自建服务器”描述运行部署方式，不应被宣传为每一个上游请求都只访问平台主域。上游没有返回点赞/收藏/评论/粉丝/多画质就保持未知。Live Photo只保留静态图片；不承诺成对图片与视频格式。只有安全媒体链接会被交给现有下载队列。

如果上游返回HLS/MPD清单，作为真实清单URL交给本机已有yt-dlp generic下载器处理片段，再用FFmpeg流复制封装为MKV；不是把清单文本存成MP4，也不重新把原分享页交给另一个解析器伪装成功。没有DRM解密、授权绕过或虚假媒体大小。

## 5. 网络、Cookie及隐私边界

Kotlin只传当前平台的有效Cookie，不发送旧服务器Basic密码或OAuth secret。私有进程对每次请求/重定向按domain、hostOnly、path、secure、expires重组Cookie，日志不打印请求、库异常或签名URL。上游返回的请求UA仅在合法长度且无换行时用于后续媒体请求。

只允许HTTPS；历史HTTP地址尝试升级，不关闭证书验证。禁用Python客户端环境代理；系统VPN和运营商路由仍由设备控制，**没有据此实测证明国内所有平台都无需VPN成功**。DNS预检查阻止明显私网/本机地址，允许域名解析落入系统VPN常用198.18/15 Fake-IP路由（网址直接使用该IP仍拒绝）；TLS仍验证真实域名。[R6]

DNS预检查与实际连接存在TOCTOU窗口，HTTPX响应重建和第三方Cookie jar也没有完整攻击审计；这套本机组件不应直接被复制成公网上的多租户服务。

单请求96KiB输入/192KiB输出边界、网络主体8MiB限制、65秒解析deadline、4个排队位置、可取消通信和断连提示。队列等待与首次解包另有开销，客户端总等待上限6分钟。upstreamCPU死循环或原生扩展卡死不能仅靠asyncio立即终止，因此不声称所有故障都精确65秒退出；长时间native卡死可能需要系统回收进程或重启应用。不能因安全超时就自动重试上传凭据。

## 6. 下载确认只保留画质

解析页仍可选引擎；点击解析时固定requested engine ID。合集点击下载时保存选项及所选entries的快照，画质弹窗仅显示数量、首项实际格式、质量列表、确认/取消。移除引擎chips、切换处理函数和服务配置入口。

创建普通/合集任务都使用该次解析绑定的engine，而不是读取“最新全局设置”。画质只是质量语义：最高可用逐集独立选择、固定qn/分辨率不可用时明确失败、不静默降级。每个任务拿到新的时效地址仍需按其绑定引擎解析，这与弹窗是否再出现引擎选择无关。

## 7. 升级和源码保持

不改变包名、签名逻辑、账号Vault、下载目录、模糊/颗粒/弹簧/主页面保留策略。外观策略及关键渲染源码与0.7.6做逐字节对比，结果在tests/local077/verification.json。删除的是旧服务配置入口与运行依赖，不清用户账号和文件。

原parse_video_py ID继续有效，但指向本机。旧远程B站任务的pv-source ID明确提示重新解析，不把其他格式硬冒充旧格式。老Basic配置保持私有且不再读取，有意不自动删，便于用户在备份后的回滚；没有通过迁移流程上传它们。

## 参考资料（2026-09-09查阅）

- R1 固定提交的项目依赖：https://github.com/wujunwei928/parse-video-py/blob/5fcf87256edb5ffcdebf0e4aac2a5a41745da76e/pyproject.toml
- R2 Chaquopy Gradle/Python/ABI/包数据：https://chaquo.com/chaquopy/doc/current/android.html
- R3 lxml Android wheel：https://chaquo.com/pypi-13.1/lxml/
- R4 PyYAML及aiohttp Android wheel：https://chaquo.com/pypi-13.1/pyyaml/ 、https://chaquo.com/pypi-13.1/aiohttp/
- R5 固定提交完整注册表：https://raw.githubusercontent.com/wujunwei928/parse-video-py/5fcf87256edb5ffcdebf0e4aac2a5a41745da76e/src/parse_video_py/parser/__init__.py
- R6 Fake-IP路由说明：https://wiki.metacubex.one/en/config/dns/
- Chaquopy Java调用API：https://chaquo.com/chaquopy/doc/current/java.html
