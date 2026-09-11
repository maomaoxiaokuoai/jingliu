# 镜流 0.7.8 · 文案精简与 Cookie 导入

本版基于上一份「账号精简版」完整工程，只改 AccountScreen.kt、LoginQrDialog.kt 两个生产界面文件。
保留之前的 AndroidView / if-else 编译修复。没有修改 Gradle、Kotlin、Python、Compose、Haze、包名、签名、玻璃/动效、下载引擎、会话仓库与既有下载数据。

## 本次修改

- 删除扫码弹窗红框内两段说明，只保留标题、二维码、状态、倒计时及“保存二维码”。保存后短提示提醒删除临时二维码图片。取消、返回、有效期和保存 guard 保持原逻辑；没有恢复打开 App、浏览器试验或其他按钮。
- 删除账号资料卡里的“使用当前会话”及其说明，保留头像、昵称、会话状态、UID、会员字段、B站校验与删除本机会话。
- 删除登录入口下面“二维码是否提供及如何确认…”的说明。把扫码和电脑版登录的行内副标题各缩为一句。
- 七个平台（B站、抖音、快手、小红书、YouTube、TikTok、X/Twitter）统一恢复“导入 Cookie 文件”和“粘贴 Cookie”。这一组不放在是否支持内置浏览器的条件里，YouTube 也能导入；不解除原有 Google/YouTube 内置登录限制。
- 文件导入使用系统文档选择器；内容沿用现有 1 MB 限制及 CookieCodec 校验，支持 Netscape cookies.txt。开始选文件时固定目标平台，返回结果时不使用另一平台的新选择。取消文件选择不覆盖会话。
- 粘贴框提供“从剪贴板粘贴”，仅用户点击时读取文字，不读取剪贴板 URI，也不后台轮询剪贴板。可直接长按输入框使用系统粘贴。超长内容明确拒绝，不悄悄截断 Cookie。
- 粘贴界面继续使用 secure=true 和密码遮蔽；Cookie 内容只使用 remember，不写入 Activity saved state，取消和提交时清空编辑值。

## 保存和失效校验（范围保持真实）

新入口调用当前 LumaViewModel 的 importCookieFile/importCookies，继续通过原有 SessionVault 持久化到本机 AES-GCM 加密文件和 Android Keystore。
这不是只改“已登录”文字：Cookie 仍由原来的下载/请求路径按平台、域名、路径、secure 和 expires 条件使用。没有重置或搬移之前保存的资料。

B站导入继续先调用原有身份校验；失败不覆盖旧账号，保留手动校验与原有定期维护。其他平台沿用“Cookie 已保存 · 未验证身份”的真实状态，不把导入成功说成官方身份已验证。

只有包含有效期元数据的 Cookie 才能本地判断其到期；粘贴 Cookie 请求头本来通常没有 expires 信息，expires=0 不等于永久有效。服务器提前撤销会话须由平台响应发现。**本补丁没有新增七个平台逐次在线身份校验或自动续期，不能宣称所有账号每次启动都已在线验证。**

## 更新现有工程（建议用增量包）

解压增量包，在包含 ApplyPatch.ps1 的文件夹中打开 PowerShell：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\ApplyPatch.ps1 -ProjectPath "C:\Users\maomao\Downloads\Jingliu-Android"
```

把路径改成自己实际工程根目录。脚本先校验补丁与旧文件哈希，全部匹配才备份并替换；发现本地修改停止，不会强行覆盖。重复执行会识别已更新的文件。
备份在根目录 `.jingliu-cookie-backup-*`，不在 app/src 中。脚本没有在 Windows PowerShell 实际运行；也可先备份后按 patch-files/ 的相对目录手动替换两个文件。

更新后回到 Android Studio 点 Run，或在工程根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug --console=plain --stacktrace
```

不要卸载，不要清空 Cookie/文件，不需要修改依赖或清 Gradle 缓存。保留原签名和本机配置。

## 完整源码

完整 ZIP 包含 app、core、Python 本地解析适配器、可选 auth-bridge、图标资源、构建配置、历史记录与本轮测试。打开包含 settings.gradle.kts 的 Jingliu-Android 根目录。

沿用 JDK17/JBR21、SDK35 与编译电脑 Python3.11.x。源码不带 gradle-wrapper.jar；从已成功构建的旧工程复制同版本官方文件至 gradle/wrapper/，或运行原有 setup-wrapper.bat。
成功构建后的 APK 为 app/build/outputs/apk/debug/app-debug.apk。本次不附 APK。

## 本次实际检查

- 实际 core 类运行 35 项 Cookie 格式、七平台导入、域名隔离、路径/HTTPS、过期过滤与元数据序列化检查，全部通过；数据为测试样例，不是实际用户登录。
- 重跑原二维码导出安全策略 27 项，全部通过；不是 Android 相册写入测试。
- 两个修改后的完整 UI 文件及未改动的 EmbeddedLoginActivity，在真实 Kotlin 编译器 + 实际 core + 原 coroutine JAR + **显式外部 Android/Compose 类型测试替身**下编译退出码 0。测试替身不进入 APK，不验证 SDK 真实依赖或 Compose 渲染。
- 21 项静态入口/敏感数据约束检查通过；静态检查不是手机实际点击测试。
- 对照原 ZIP，生产代码差异严格限制为两个界面文件，其他源代码、资源、存储/下载组件逐字节未改。最终检查 ZIP CRC 与逐文件 SHA-256。

初次把全部检查放到一条容器命令中时触及 120 秒限制，停在 UI 编译；随后独立重跑 UI 编译和静态检查均通过。没有把超时记为通过。
本机 Kotlin1.9.0、JDK21，-Xjdk-release=17。当前没有 Android SDK，**未执行完整 Gradle/Android 编译、真实文件选择器/剪贴板/Keystore/平台登录验收，没有 APK**。
本轮证据在 tests/account-cookie-import/；旧 account-cleanup 的“禁止 Cookie 入口”等断言是上一版历史要求，不属于本轮验收。

## API 依据

- Android OpenDocument（String[] MIME 输入、Uri 结果）：https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument
- Android 复制与粘贴（只读取文本项）：https://developer.android.com/develop/ui/views/touch-and-input/copy-paste
