# parse-video-py 备用服务（可选）

这是镜流新加的私有 HTTP 包装层，真实调用 wujunwei928/parse-video-py 的 Python 包，不是示例解析或模拟返回。普通 Android 原生/yt-dlp 下载不依赖它。

## 接口与上游

Android 适配该项目已有路径 `/video/share/url/parse?url=...`，支持带 `code/data` 的响应和直接媒体对象，以及可选 HTTP Basic 认证。也可配置你已经部署好的官方 Web 服务。

本目录的 server.py 使用 Python 标准库 HTTP 服务 + 上游基础包，不引入 FastAPI/MCP 前端依赖。requirements.txt 固定上游 Git commit：

`5fcf87256edb5ffcdebf0e4aac2a5a41745da76e`

该提交有抖音分享页适配修改。requirements 是安装声明，不是已缓存的 wheel；其传递依赖由 pip 解析，未提供完整可重复构建 lock 文件。没有在当前环境联网安装或实测上游提取器，不保证所有链接成功。

## Windows / Android Studio 模拟器测试

使用 Python 3.10+（你原有 Python 3.11 可以用于此服务）。在本目录打开 PowerShell：

```powershell
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe server.py
```

也可运行 `start.ps1`。默认只监听 `127.0.0.1:8000`。服务必须保持运行。

在模拟器中的 **Debug APK** 选择 `parse-video-py · 自建服务`，服务地址填写：

```text
http://10.0.2.2:8000
```

这不是手机实体机访问电脑的地址。HTTP 例外仅限 Debug 构建的 localhost/127.0.0.1/10.0.2.2，主 Manifest 不放开全局明文网络。Profile/Release 和实际手机应使用你自己的可信 HTTPS 域名/服务。

## 真实手机与 HTTPS

用可信证书的 HTTPS 反向代理转发到本机 `127.0.0.1:8000`，不在公网直接裸露 Python HTTP 端口。设置认证环境变量：

```powershell
$env:PARSE_VIDEO_USERNAME="你的服务用户名"
$env:PARSE_VIDEO_PASSWORD="你自己设置的强密码"
.\.venv\Scripts\python.exe server.py
```

Android 填 `https://parser.example.com` 一类的真实服务根地址（example 只是示意），以及相同服务用户名/密码；不要填写 `/video/share/url/parse` 路径。不能把平台 Cookie 当作服务密码。服务地址、Basic 认证在单独的 AES-GCM 文件保存，未写进源码。

若已有官方服务，须提供兼容接口及最终地址；客户端拒绝 3xx 跳转，不把认证携带到跳转目标。

## 数据与网络

只发送待解析分享链接（可能包括 xsec_token 等分享参数）和你自己的服务认证。不发送 SESSDATA、bili_jct、授权令牌或其他平台 Cookie。请求返回后，媒体由 Android 下载，并非服务端替手机保存文件。

服务端 IP 与手机 IP 不同，某些签名直链可能受 IP/时效/Referer 限制而无法在手机使用。遇到此情况用原生或 yt-dlp，不篡改 URL 或把所有站点强行重定向到别的 CDN。

本包装层只允许当前产品的 B站、抖音、快手、小红书、X 分享域名；不冒充覆盖上游全部平台。YouTube 和 TikTok 应使用本机 yt-dlp。B站 p>1 明确拒绝，防止上游首P逻辑导致下载错误视频；普通合集的独立 BV 链接可逐项尝试，但不承诺会员画质。

本版不组合 LivePhoto，不把 `music_url` 当成视频正确音轨强行合并，不制造上游缺失的统计。上游仅返回源地址时，画质显示“源文件”，大小未知就明确显示未知。

## 安全 / 验证范围

Python HTTP 包装层默认限并发 2、平台调用 55 秒超时、最大结果 8 MiB，关闭包含分享参数的请求日志，拒绝未经认证、跨站 Cookie 导入及错误参数；TLS 由反向代理负责。这里只是供自用的辅助服务，**不是通过安全审计、能直接对公众开放的托管平台**。

DNS 初始检查与上游自身跟随的跳转不是全面 SSRF 沙箱；公开部署前还需出站防火墙、速率限制、超时、禁止访问私网、日志/隐私审查。不要把自己的服务做成无认证公共解析站。

`python -m unittest -v test_server` 用明确注入的测试解析器和 localhost 验证 HTTP 契约；不连接平台，不证明真实 Python 包可解析当前平台。

## 来源

- https://github.com/wujunwei928/parse-video-py
- https://github.com/wujunwei928/parse-video-py/commit/5fcf87256edb5ffcdebf0e4aac2a5a41745da76e
- https://raw.githubusercontent.com/wujunwei928/parse-video-py/main/src/parse_video_py/web.py
- https://raw.githubusercontent.com/wujunwei928/parse-video-py/main/src/parse_video_py/parser/bilibili.py
- https://developer.android.com/studio/run/emulator-networking

上游 MIT 许可证属于上游项目；本工程新增包装层遵守镜流根目录的 GPL-3.0-or-later。这里只提供源码和安装声明，不分发平台凭据或第三方预配置账号。
