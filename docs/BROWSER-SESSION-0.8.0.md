# 镜流 GlassFlow 0.8.0 · 内置浏览器自动保存与会话检查

**完整 Android 源码，不是补丁；不含 APK。** 基于最近的 0.7.8「文案精简与 Cookie 导入」分支，合并此前 0.7.9 的会话检查、真实 Cookie 属性及防旧响应覆盖逻辑，再增加本轮浏览器自动捕获、快手加载优化和抖音在线状态探测。

**本次没有完成 Android 全工程编译或真实平台登录验收。** 完整 Gradle 构建尝试仍在获取官方 Wrapper 时 DNS 失败。主机逻辑测试和显式 API 类型替身编译不能代替真实 SDK、WebView 或线上平台测试。

## 1. 怎么使用

设置 → 平台账号 → 选择平台 → **内置浏览器登录**。

- 默认使用电脑版；在浏览器右上角“显示”里可切换手机版，Cookie 仓库和当前会话不因显示方式切换而重新创建。
- 在平台真实页面自己登录。页面提供的密码、短信、二维码等方式由该平台决定，不由镜流伪造。
- “自动保存 Cookie”默认开启，仅在浏览器位于前台且页面已显示时检测新登录凭据。新值稳定后写入原来的本机加密仓库；正常状态提示“Cookie 已自动保存”，无需每次再按确认。
- 自动检测使用已知会话 Cookie 名称作线索，**不等于官方身份验证成功**。平台改用未知字段时可点“保存并返回”手动保存本页实际 Cookie；手动保存也不会伪报在线登录。自动检测不因为匿名统计 Cookie 变化就反复覆盖旧账号。
- 仍保留各平台的 Cookie 文件导入、粘贴及 B站独立扫码。没有恢复被你删除的“仅启动 App”“开放平台身份授权”等冗余账号分组，也没有改扫码弹窗文案。

### 平台支持范围

| 平台 | 本版内置浏览器入口 | 本版账号检查 |
|---|---|---|
| B站 | 电脑版/手机版，页面产生会话后自动保存；原独立扫码保留 | 原真实 nav 身份检查、本地登录 Cookie 到期与作用域 |
| 抖音 | 电脑版/手机版；检测到新会话后自动保存 | 本地有效期 + 当前用户接口在线探测；风险拦截/网络不通时显示无法确认 |
| 快手 | 电脑版默认“轻量加载”，可切手机版、可关闭轻量选项 | 本地有效期与登录 Cookie 线索；没有把它伪装成已通过官方在线验证 |
| 小红书、TikTok、X/Twitter | 电脑版/手机版；已知登录凭据变化后自动保存 | 本地有效期；缺少期限时显示未知；未统一实现在线撤销检测 |
| YouTube/Google | 提供入口说明，但**不打开被平台禁止的嵌入式登录** | 自己的 Cookie 文件/请求头导入仍保留；不读取系统浏览器私有会话 |

Google 是明确的例外，不会用修改 UA、借用第三方 client_id 或伪造系统授权页来绕过限制。镜流的桌面显示模式只用于允许的站点，不是 OAuth 绕过工具。

## 2. 快手电脑版加载修改

原版每次进入/退出会删除 WebView HTTP 缓存，重复下载网页脚本和资源；还需要等完整视频页面 onPageFinished 才算就绪。本版：

1. 不再调用 clearCache(true)，使用标准 LOAD_DEFAULT 缓存；优先按平台隔离 WebView Profile，旧 WebView 不支持时使用单会话退化。
2. Cookie 恢复使用异步回调，界面不执行同步 flush。账号文件读取、Cookie 属性读取和保存放在后台；不阻塞主界面导航。
3. onPageCommitVisible 即可开始观察已显示页面，独立显示真实加载进度，不等待所有推荐视频加载。
4. 快手“轻量加载”默认开启，仅阻止登录页子资源中能明确识别扩展名的视频流，保留脚本、样式、字体、图片和常见验证/二维码路径。不屏蔽主页面。若平台验证码因特殊资源形式受到影响，关闭轻量加载再刷新；不是所有站点资源的万能分类器。
5. 桌面 UA 保留当前设备 WebView 的 Chromium 版本，不固定伪造某个新引擎。20 秒没有显示页面时提供刷新/切手机模式提示。

**没有实测首次加载秒数或真实快手访问成功率。** 缓存复用主要有利于后续打开；首次仍有 WebView 初始化、网络和平台验证开销。临时 Cookie 及站点存储仍清理以保护账号隔离；保留的是 HTTP 静态资源缓存，不是把全部登录数据明文留在网页目录。

## 3. 抖音“到期”和“在线”分开

账户卡片显示：当前检查结果、已知到期时间（如有）、最近检查时间。

- 优先使用 AndroidX CookieManagerCompat.getCookieInfo 读取真实的 Expires/Max-Age、Domain、Path、Secure、HttpOnly。不支持此功能时回退到 Cookie 请求头，并保守保留已观察到的主机/路径。
- 粘贴请求头没有到期信息，expires=0 表示未知/会话 Cookie，不是“永久有效”。不解码 sid_guard 里的数字就假定一年有效。
- 本地到期判断只围绕识别到的登录凭据；过期的统计 Cookie 不会把仍有效的登录误判为过期，长寿命统计 Cookie 也不会掩盖登录凭据已经过期。
- 同一个登录值后续获取到真实期限，可自动升级已保存的未知期限元数据；不会因每秒 Max-Age 舍入或统计 Cookie 变化反复写文件。
- **抖音新增只读 current-user 接口探测**。只有自身份接口成功且返回实际用户标识时确认本次在线；只有明确的未登录响应才标为已撤销。200 空数据、403、验证码页面、超时、无法识别状态码都保持“暂时无法确认”，不会删 Cookie，也不会误称仍在线。
- 此网页接口不是官方开放平台 OAuth API，平台签名与风控可能使其不可用。本版未进行真实抖音账号在线探测验收，不能保证所有设备/会话都能得到确定结论。

检查触发：启动/回前台、进入账号或切换平台、自动保存/导入之后、解析使用会话前，以及手动“检查登录状态”。相同快照的重复请求合并；非手动检查 30 秒内不重复联网。UI 不等待网络检查后才响应。

## 4. 保存和隐私

保持原 `noBackupFilesDir/sessions.aes-gcm`、Android Keystore 别名 `shiliu.session.v1`、AES-GCM 及 schema=1 数据格式。

自动保存和手动导入只读取镜流自身平台 WebView，不读取密码输入框、不添加 JS bridge、不从其他 App/系统浏览器导出 Cookie。Cookie 内容不写日志，不发送到第三方解析服务。

浏览器会话保存有账号快照/代次保护：导入新 Cookie、删除账号（包括空→导入→删除）、另一次会话更新后，旧浏览器不能重新覆盖或复活它。后台检查仅修改身份元数据时，不会误伤当前浏览器的保存。平台未返回身份资料时显示未确认，不生成昵称或头像。

每次打开会将未撤销且未完全过期的已有 Cookie 恢复到镜流自己的平台 Profile。HTTP 缓存不清，临时 Cookie/站点 localStorage 在退出时清理；进程异常退出后，下次初始化先清理同 Profile 的临时会话，再恢复加密仓库。未改下载存储目录，不删除已下载内容。

## 5. 构建

版本 `0.8.0-browser-session`，versionCode **17**，applicationId 仍是 `com.luma.downloader`。保留原签名更新，无需卸载或清除数据。

Android Studio 打开有 settings.gradle.kts 的 **Jingliu-Android 根目录**。继续使用 JDK17/JBR21、SDK35、Build-Tools35.0.0 和编译电脑 Python3.11.x。手机不需要另装 Python 或启动解析服务器。

本轮新增 `androidx.webkit:webkit:1.12.1` 依赖（旧 0.7.9 分支已有，最近文案分支没有）。其他 Gradle/Kotlin/Compose/Haze/Media3/Chaquopy/解析库版本保持不变。先 Sync 获取依赖。

本包没有 gradle-wrapper.jar；复制旧工程中已正常使用的同版本官方文件到 gradle/wrapper/，或运行 setup-wrapper.bat 下载并校验。

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain --stacktrace
```

只有实际构建成功才会生成 `app\build\outputs\apk\debug\app-debug.apk`。Profile 变体仍可用 `:app:assembleProfile`。不要靠删除账号、清 Gradle 全局缓存或关闭安全校验来解决更新问题。

## 6. 当前检查与边界

新 **41 项**主机检查通过，覆盖期限、Cookie 元数据、自动捕获稳定窗口、匿名会话、真实 AES-GCM 文件写入与重读、旧响应保护、抖音分类与异步请求合并。网络探测使用明确的替身结果，不是线上平台登录。

复跑已有核心76断言（含 localhost HTTP）、Cookie导入35项、二维码安全规则27项。另有18项源码约束检查。每组只记一次，历史测试不再累计。

实际改动的 AccountScreen、EmbeddedLoginActivity、BrowserCookieStore、BrowserSessionSaver、SessionVault 和 SessionChecks 用真实 Kotlin 编译器进行局部类型检查，通过；**外部 Android/Compose/AppGraph/VM API 使用明确的类型测试替身**，这不验证真实依赖、插件、WebView Profile 行为或渲染。

完整 Android 构建尝试因官方 Wrapper 下载 DNS 失败停止，未进入 Android 编译。没有 APK、真实 Android WebView/Cookie属性读取、快手加载时延、抖音实际在线状态或七平台登录测试结果。不能宣称所有平台自动登录均已可用。

本次脚本和日志在 `tests/browser080/`。旧 `tests/*` 的历史数量不能当本轮整工程验收。

## 7. 设备复测建议

用原签名更新，保持旧账号。分别测试：

- 内置浏览器自己登录，等待“Cookie 已自动保存”再关闭；重新进入账号页确认持久保存，必要时点击“检查登录状态”。
- 自动保存关闭时不自动写入；手动“保存并返回”仍可保存本页实际 Cookie，未知期限显示未知。
- 快手先用电脑轻量模式；若官方验证资源异常，关闭轻量再刷新；对比第一次和第二次打开，而不是把缓存命中说成首次加速。
- 抖音已知到期、未知到期、明确未登录、断网/403几种结果，确保网络问题不删除凭据。
- 浏览器打开期间，从另一入口替换/删除账号，旧浏览器停止覆盖；相同账号后台检查元数据不阻碍保存。
- YouTube入口只解释可用导入路径，没有内嵌 Google 登录绕过。

## 参考（2026-09-10 查阅）

- AndroidX CookieManagerCompat： https://developer.android.com/reference/androidx/webkit/CookieManagerCompat
- WebView Profile / CookieManager / WebStorage： https://developer.android.com/reference/androidx/webkit/Profile
- WebViewCompat.setProfile 的时序： https://developer.android.com/reference/androidx/webkit/WebViewCompat
- Google 嵌入式 OAuth 限制： https://developers.google.com/identity/protocols/oauth2/policies
- 抖音网页 self 接口研究依据（第三方开源项目，不是平台稳定契约）： https://github.com/whiteguo233/OpenBiliClaw/issues/129

未复制第三方 Cookie、appkey、client_secret 或非商业项目代码。测试中的凭据仅是合成字符串，不是可登录账号。
