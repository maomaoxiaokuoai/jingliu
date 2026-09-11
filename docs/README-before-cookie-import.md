# 镜流 0.7.8 · 账号入口精简版

**完整源码，不含 APK。** B站保留扫码入口（弹窗只有保存二维码）；其余支持的平台只保留内置电脑版网页登录。其他账号操作、试验跳转、手机/系统浏览器及手动 Cookie 导入入口已从账号页删除。已保存会话与下载数据保留。

[本次修改、更新方法和验证范围](docs/ACCOUNT-CLEANUP-0.7.8.md) · [此前完整编译说明](docs/README-before-account-cleanup.md)

生产代码仅修改 AccountScreen.kt、LoginQrDialog.kt、EmbeddedLoginActivity.kt。没有更改依赖、包名、签名配置、玻璃/动画或下载逻辑。
YouTube 原有内置登录限制继续保留；官方页面是否提供二维码以平台为准，不新增模拟扫码。

本次执行局部 Kotlin 类型检查（外部 API 测试替身）、二维码策略和源码约束检查，**不是 Android 整工程或真机验收**。

## 本地编译

打开此根目录（不是 app 子目录），沿用原来的 JDK 17/JBR 21、SDK 35 和编译电脑的 Python 3.11.x。
Wrapper JAR 沿用旧工程同版本官方文件，或运行原 setup-wrapper.bat。账号数据不需要清除。

```powershell
.\gradlew.bat :app:assembleDebug --console=plain --stacktrace
```

只有构建成功后才会生成 app/build/outputs/apk/debug/app-debug.apk，再点击 Run 安装运行。
