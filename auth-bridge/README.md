# 可选的 Kotlin/JVM 官方扫码授权服务

**B站现有扫码、媒体解析和下载不需要这个服务。** 本模块只为抖音、快手、TikTok 的开放平台授权保护 client_secret、处理 HTTPS 回调及真实身份校验。它不是在线解析服务器，不接收视频链接、下载任务、浏览器 Cookie 或平台密码。

默认工程没有开发者凭证和部署地址，三个对应扫码入口会显示“未配置”。只把这个文件夹复制进工程，不会自动得到平台权限或一个可用服务。

## 必要配置

先在相应平台注册/获批支持此用途的网站/开放平台应用，申请 `user_info`（抖音、快手）或 `user.info.basic`（TikTok）。遵守平台审核和产品接入范围；本源码不能代替审核。

为自己的服务准备受信任证书的 HTTPS 域名，例如 `https://auth.example.com`。注册对应回调：

```text
https://auth.example.com/callback/DOUYIN
https://auth.example.com/callback/KUAISHOU
https://auth.example.com/callback/TIKTOK
```

服务端环境变量（只填在自己的服务器，不填进 APK）：

```text
PUBLIC_BASE_URL=https://auth.example.com
PORT=8787
DOUYIN_CLIENT_KEY=
DOUYIN_CLIENT_SECRET=
KUAISHOU_CLIENT_KEY=
KUAISHOU_CLIENT_SECRET=
TIKTOK_CLIENT_KEY=
TIKTOK_CLIENT_SECRET=
```

快手 `CLIENT_KEY` 对应 app_id，`CLIENT_SECRET` 对应 app_secret。只配置你已获批的平台；某个平台为空会返回未配置，不影响其他平台。`.env.example` 仅为模板，程序读取进程环境，**不会自动读取 .env**。

在 Android 工程根目录将 `qr-auth.properties.example` 复制为 `qr-auth.properties`：

```properties
bridgeUrl=https://auth.example.com
```

这里只写公开域名地址，不写密钥、用户名、token 或回调查询串。修改后重新构建 APK。该配置默认不随源码分发。

## 构建与部署

默认 Android 构建不加载此服务模块。在已有 JDK 17 或 JDK/JBR 21 和 Gradle 依赖的开发环境，显式启用后运行（输出目标保持 Java 17）：

```sh
./gradlew -PincludeAuthBridge=true :auth-bridge:test :auth-bridge:installDist
```

将 `auth-bridge/build/install/auth-bridge/` 的分发目录部署到自己的 Java 17+ 服务器，设置上述环境变量，运行 `bin/auth-bridge`（Windows 为 .bat）。启动时只监听 **127.0.0.1:8787**。把 HTTPS 反向代理指向该端口，必须配置请求大小、超时和频率限制；参考 `nginx.example.conf`，证书路径及域名需替换。

服务仅用标准 JVM HTTP/HTTPS 和当前 `core` 模块，无 FastAPI/Python 或第三方在线授权站。Android Studio 构建 app 不会启动该服务。源码在当前环境用 Kotlin/JVM 编译及本机测试，但没有部署公网服务或实际平台验收。

## 协议与防护

`POST /api/qr/start`：platform + 客户端随机 nonce，返回私有 session/proof/nonce、有效期以及官方 QR 地址或官方扫码页面。

`POST /api/qr/poll`、`POST /api/qr/cancel`：需同时出示 session、proof、nonce。不要记录这些请求正文。只返回状态，只有经平台 token/身份接口校验成功后才返回最小公开资料。

抖音/快手返回的地址是**真正由平台生成二维码的页面**，不是把普通授权网址转换成静态二维码。应用只显示允许的 HTTPS 官方域名页面，不提取 Cookie、不注入登录脚本、不绕过 TLS。

TikTok 使用官方 v2 get_qrcode/check_qrcode，插入随机 client_ticket，确认时同时检查 ticket/state，然后交换 token 并核对 open_id。`/callback/TIKTOK` 仅是静态返回提示页，**不接受页面跳转作为身份证明**；只有二维码轮询验证能完成该流程。v2 文档示例有时省略 state，但本实现请求了 state，因此缺失时仍拒绝，不降级安全检查。

服务会话保存于内存，3 分钟过期，最多 256 项；重启即失效。还需要反向代理限制请求速率。本版本不是多实例共享存储服务，不适合在没有 sticky session/共享存储的情况下多副本部署。没有长效平台 token 保管、自动续期、撤销接口或安全审计。

## “已授权”不等于“能直接下载平台全部视频”

公开资料接口只能确认用户允许的身份信息。它不会返回浏览器会话 Cookie，也不授予会员、DRM 或其他内容下载权限。因此 Android 将资料写入独立的私有 `authorized-profiles-v1.json`，不覆盖原有 SessionVault。账号页标记的是**一次开放平台身份确认**，不是永久登录或完整下载 Cookie。

原来的 Cookie 导入及B站实际会话保留。后续若要让受授权的业务 API 长期使用 token，需要另行设计安全保存、续期、撤销和各平台范围，不能用这个“仅确认身份”实现冒充已经完成。

## 隐私与上线检查

不要记录 authorization code、client_secret、token、proof、state 或请求全文；服务器/代理日志应只保留无查询串的路径和状态码。这里不持久化平台 token，也不把 token 返回手机。仍应审核你的运行环境、反向代理、应用权限和数据政策。

没有提供真实应用密钥、受信任部署地址或批准范围，因此本次只验证了协议样例与 localhost 测试，没有完成真实平台扫码。首次部署至少验证：同意、拒绝、过期、重复回调、错误 state/client_ticket、关闭后回调到达，以及服务重启后的客户端提示。
