# 镜流 0.7.8 · 文案精简与 Cookie 导入

完整源码，不是补丁，不含 APK。本次删除截图红框中的长说明，给七个平台恢复 Cookie 文件导入和粘贴入口。扫码仍只保留保存二维码，电脑版登录继续保留；不恢复其他 App 启动/身份授权/手机版入口。

生产代码仅修改 `ui/AccountScreen.kt`、`ui/LoginQrDialog.kt`；包名、依赖、签名、图标、玻璃/动效、下载逻辑、SessionVault 原样保留，已有账号和下载数据不清除。

[本次修改与更新说明](docs/ACCOUNT-COOKIE-IMPORT-0.7.8.md) · [上一版说明（历史）](docs/README-before-cookie-import.md)

## 使用

账号页选择平台后，“导入 Cookie 文件”接受自己的 Netscape cookies.txt；“粘贴 Cookie”支持请求头或 Netscape 文本，并提供显式剪贴板粘贴。内容沿用原有本机加密保存。导入成功不等于平台身份已经验证；B站沿用真实校验，其他平台保留未验证状态。

## 构建

Android Studio 打开本根目录（含 settings.gradle.kts）。沿用 JDK17/JBR21、SDK35、Python3.11.x。未附 gradle-wrapper.jar，复制旧工程相同版本的官方文件或运行 setup-wrapper.bat。

```powershell
.\gradlew.bat :app:assembleDebug --console=plain --stacktrace
```

成功后 APK 为 `app/build/outputs/apk/debug/app-debug.apk`。保持原签名更新，不需要卸载或清数据。

## 验证范围

35 项真实 core Cookie 样例检查、27 项二维码规则与21项静态约束通过；修改 UI 的局部类型检查使用明确的外部 API 测试替身。它们不是完整 Android 编译、画面录屏、真实登录或设备存储测试。没有新 APK，未执行整工程 Gradle 构建。本轮日志与复现脚本见 `tests/account-cookie-import/`。
