> 上一轮开发说明存档。当前可用的 0.7.5 附件含 22 个生产源码/配置文件和 2 份历史日志，已全部合并。原说明提及的 `PerformancePolicyChecks.kt`、`PerformancePolicyTest.kt` 和 `RetainedSceneDeviceTest.kt` 没有随当前附件提供；本包不伪造这几份原始测试源码。`tests/fix-006/` 的两个日志是历史记录，不是本次运行结果。本次打包实际检查见 `tests/package-0.7.5/`。

# 镜流 0.7.5 · 页面复用、绘制阶段动效与登录跳转排查

**本包是源码，不含 APK。** 基于你能构建的 0.7.4 修改，不升级 Gradle/Kotlin/Compose/Haze/下载引擎。重点优化页面和控件的更新路径，没有调低模糊、关闭玻璃/颗粒、降低弹性或清空你的设置。没有从静态截图推断真机帧率，也没有把主机测试说成流畅度实测。

## 1. 截图里的警告与卡顿不是同一个问题

截图显示 core/app Kotlin 编译阶段的 warning，不是 compile failed。`Condition is always true` 是 Java 响应头映射边界的冗余空检查；OpenInNew 是图标弃用提醒；Haze 是实验 API 提醒。它们不是“运行时每帧弹出一次警告”。本版保留响应头过滤的语义，明确可空键类型；改用自动镜像图标并声明对应实验 API opt-in。**没有使用全工程关闭警告的方法来掩盖代码问题。**

## 2. 源码排查及改动

### 一级页面：不再销毁、重建和等旧动画结束

0.7.4 使用 AnimatedContent + SaveableStateHolder。保存可恢复状态，不等于保留完整页面、列表及材质节点；切换完成后旧内容仍会退出 composition。根部同时订阅整份下载队列，字节进度也可以触发高层更新。

新 `RetainedSceneHost` 保留四个主页面的稳定身份。页面运动仍使用原 MotionPolicy 的弹簧刚度、阻尼和距离；每次点击直接改变目标，由 Compose Transition 从当前动画状态重新定向，没有新增导航请求队列，也没有等动画完成才接受下一次点击。

隐藏页面保留状态，但在布局的放置阶段不放置、不绘制，避免四套玻璃一直在 GPU 上重画。目标页在命中测试上位于最上面；按固定权重重新计算透明度，避免快速反向切换时旧页挡住点击，或多层普通交叉淡化露出底色。淡化权重只是绘制控制，不写回业务状态。

**权衡：**保留页面会使用更多常驻 UI 内存和一次性的初始 composition。手机还保留设置首页和最近一个详情页；平板详情按实际访问保留，数量受已知分类数量限制。还没有设备 PSS/启动耗时对比，不能说零内存成本或首次进入一定没有开销。

### 设置二级页：复用原列表，返回末尾不重新挂载

设置首页保持在下面；最近一次详情退出后不马上销毁。再次进入同一页可复用原滚动状态和控件。返回手势仍使用原弹簧和推进方向；隐藏/退出面板只在自己实际显示范围拦截输入，首页不必为不可见的弹簧尾段继续整屏锁住。

仅当前逻辑页面处理返回和滚动导航状态。留在缓存里的设置页不会截获“下载/文件”的系统返回。平板详情使用相同复用机制，左栏不重建。

### 动效更新放到 draw/layout，而不是每帧重建整块组件

- 底栏选中块的位移在 lambda offset 中读取；收拢高度在布局阶段读取。
- 开关滑块位移和轨道颜色在绘制/图层阶段读取；不在每帧重组整个玻璃开关。
- 按钮触碰提亮在绘制阶段读取，不再每帧生成新的 fallbackColor、HazeStyle、边缘/噪声缓存。
- 同窗口菜单取消显式等待两个空帧；根采样源仍保持常驻。对话框 scrim 的 alpha 在绘制阶段读取，不让整个覆盖层每帧重组。
- 图片请求按 context/URL/Referer 缓存；下载列表按输入缓存过滤。
- 下载数量单独派生、去重；文件列表只观察完成的记录；隐藏下载页不持续收集所有字节进度。
- 计数/文件筛选放到 Default dispatcher。设置重复提交同一个已吸附数值直接返回，不反复取消/新建存储任务。

`UiSettings.kt`、`MotionPolicy.kt`、`MaterialPolicy.kt`、`GlassPolicy.kt`、`GrainPixels.kt` 和图标素材与 0.7.4 相同。默认仍是全分辨率 Haze 输入；已有“省资源”选项仍然需要用户自己选择，不会自动启用。

官方依据：Compose 官方建议记忆昂贵计算、延后读取状态、用 lambda modifier 避免纯位移/颜色动画触发 composition；动画 API 支持从当前位置和速度重新定向。这不是来自某个“iOS 神奇参数”。

- https://developer.android.com/develop/ui/compose/performance/bestpractices
- https://developer.android.com/develop/ui/compose/animation/value-based
- https://raw.githubusercontent.com/chrisbanes/haze/1.6.10/docs/usage.md

## 3. 登录：两条代码路径已经增加，但不冒充官方 SDK Cookie 授权

B站账号页增加 **“在 B站确认当前登录 · 试验”** 和 **“浏览器接续登录 · 试验”**。两者都先申请平台真实扫码请求，保留对应 key、超时及原有轮询/身份校验；不是打开 B站首页就回写登录状态。

| 路径 | 实际代码行为 | 不能保证的部分 |
|---|---|---|
| App 确认 | 将当前平台返回的合法 HTTPS 扫码确认 URL 编码进 B站 browser deep link，并指定官方包名 | 这是兼容性试验，不是官方 OAuthManager SDK；当前 B站版本是否接受该页面/显示确认尚未实机验证。不能处理时保留原始扫码入口 |
| 浏览器确认 | 用系统已安装的浏览器打开同一个官方确认 URL，而不是站点首页；没有默认浏览器时提供浏览器选择 | 网页是否继续唤起 App 由平台/浏览器决定。应用不会注入 JavaScript 强迫跳转，也不能从外部浏览器读取 Cookie |

只有原有 `BiliAuth.poll` 得到平台真实登录响应并完成身份校验后，才保存新的下载会话。回到镜流、Activity 启动成功、网页跳转均**不**构成登录成功。已过期、已经完成、取消或 key 不匹配的请求不允许继续跳转。该入口不新增 exported 回调，不接收外部任意 URL 或 Cookie。

直接方案使用 `bilibili://browser?url=...` 只是将官方确认页交给 App 的兼容性尝试。**没有找到足以证明这个包装在当前所有 B站版本中都等效于扫码的官方或已验证开源证据，因而明确标记为试验；不要对用户宣传一键免扫码已经可靠实现。**

### 查阅官方与 GitHub 得到的结论

B站官方接入文档确实提供 `OAuthManager.startOauth()`，需要自己的移动应用 client_id、签名登记和官方 SDK；回调给 code，再兑换 access_token。这与下载 Cookie 不是同一种凭证。本包没有你的开发者配置，也没有把别人的示例 client_id、appkey 或密钥借来冒充镜流。

TikTok Login Kit 明确支持 `AuthMethod.TikTokApp` / `AuthMethod.ChromeTab` 两种路径，并要求 clientKey、已登记的 HTTPS redirectUri、PKCE 与服务端换票。它返回开放权限的身份/令牌，不自动变成浏览器 Cookie。

检查到的 B站开源扫码项目采用 generate→展示真实码→poll→取得会话；PiliPlus/CLI 的 Cookie 和二维码路径并不能证明一个普通启动 Intent 就是正式授权。2026 年 8 月的公开 CLI 修复还涉及成功响应变成 crossDomain URL 后需读取 Set-Cookie；现有 BiliAuth 已有“缺少 SESSDATA 时跟随允许的确认 URL”逻辑，本轮没有删除它。

- B站官方 SDK：https://bilibili.apifox.cn/doc-885729
- TikTok 双路径官方示例：https://developers.tiktok.com/docs/en/login-kit-android-quickstart-v2
- 抖音正式 SDK：https://developer.open-douyin.com/docs/resource/zh-CN/dop/develop/sdk/mobile-app/permission/android/permission-develop-guide/
- B站开源扫码示例：https://github.com/cueavyqwp/bilibili_qrcode_login_example
- 开源客户端源码：https://github.com/bggRGjQaUbCoE/PiliPlus/blob/main/lib/pages/login/controller.dart
- 扫码响应修复实例：https://github.com/public-clis/bilibili-cli/pull/27

**本轮没有接通七个平台的正式原生 SDK，也没有增加通用浏览器 Cookie 提取。**其余平台保留 0.7.4 的 Cookie 导入、仅启动 App 与已配置时的开放平台身份扫码功能；OAuth 身份不会被写入下载 Cookie 仓库。平台弹授权页这一效果本身不是无法实现，但必须使用自己的正式接入配置，不能把打开页面当成授权已完成。

## 4. 如何比较流畅度

截图来自 Android Studio 模拟器/调试构建。Debug 有额外开销，模拟器还受宿主 GPU、缩放、录屏与运行负载影响；不能仅凭它推断真实设备表现，也不能把所有卡顿归咎于模拟器。本轮有具体源码优化，不是只让你换设备。

新增 `profile` 构建：与本机 debug 同签名、不可调试、开启系统 profileable，保留相同 UI、模糊和下载代码。**未打开 R8**，是为了先对同一实现做可比测试，避免引入混淆兼容问题；这不是最终发布优化的替代，也没有附伪造 Baseline Profile。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleProfile --console=plain --stacktrace
```

输出为 `app/build/outputs/apk/profile/app-profile.apk`。可以在 Android Studio 的 Build Variants 中选 app→profile 后运行，或者安装生成的 APK。首次构建沿用你原来成功的 JDK 17/JBR21、SDK35、同版本官方 Wrapper。不要卸载/清除数据。

测试时保留当前外观设置。先在真机连续往返四个 Tab，再做设置首页→外观→返回，以及返回中途再点。滑块、开关和菜单保留相同效果；以相同输入序列对比，而不是将新版本切到实色后比较。

Profile 构建在应用进入后台时用 `JingliuFrames` 标签输出 FrameMetrics 摘要：有效帧数、超出显示预算的帧数、近期 p50/p95 及回调丢失数。不记录链接、账户、Cookie 或输入文字；监听运行在专用线程，不逐帧写日志。显示预算在此测量段开始时取当前刷新率；动态切换刷新率需重新开始一段。总帧耗时不等于完整端到端输入延迟。

```text
adb logcat -s JingliuFrames:I
```

官方性能配置依据：https://developer.android.com/develop/ui/compose/performance
系统指标：https://developer.android.com/reference/android/view/FrameMetrics

## 5. 实际验证与限制

本轮主机执行：
- 25 项性能/跳转策略用例（包含权重/z 顺序随机检查、最新目标、隐藏绘制、帧统计、URL/key/过期/状态限制）。这些不是帧率测试。
- 76 条核心断言、20 项扫码协议、27 项账号/导出策略、26 项本机授权服务、181 条外观/动效断言、16 项菜单定位、11 项覆盖层生命周期；均为原有主机回归，数据为样例或 localhost，不是线上平台登录。
- Kotlin/KTS 语法检查和 XML/资源/源码结构检查，数字见 tests/fix-006/verification.json。
- 新增设备测试 `RetainedSceneDeviceTest` 验证快速重定向时实例复用、目标接收输入和隐藏页面语义；**只提供源码，没有在 Android 上执行。**

本环境 Android 构建仍在下载官方 Wrapper 时因 DNS 失败停止，没有 Gradle/Compose 全工程类型检查、APK、真实设备帧时间/内存对比，也没有真实 B站同机跳转或其他平台 SDK 验收。你的截图证明上一版在你电脑已走到构建/运行阶段，不代表新版本已在这里构建成功。

不要把“本地策略测试通过”解读为“已零卡顿/120fps/原生 iOS 无差别”。缺少真实设备数据时，我只说明修改的代码路径和验证边界。旧 tests/fix-001…005 日志为历史记录，本轮看 fix-006。
