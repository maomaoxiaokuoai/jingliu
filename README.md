# 镜流 Jingliu · 0.9.0 液态玻璃候选

这是完整 Android 工程的恢复版本，已合入保存下来的 0.9.0 液态玻璃源码改动；**不是 0.9.1 全量扩展移植，也不是已通过真机验收的正式版**。改造范围见 `LIQUID_GLASS_MIGRATION.md`。

生产模块为 `app` 与 `core`，包含 Manifest、Kotlin/Compose、图标/资源、内嵌 Python 适配层和固定依赖声明。业务源码保持原基线不变，网络依赖在构建时由 Gradle/Chaquopy 获取，不是离线依赖全集。

## 构建

固定使用 JDK 17、Python 3.11、Android SDK 35 / Build Tools 35.0.0、Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.20。当前没有 Gradle Wrapper；GitHub Actions 会安装固定 Gradle，本地也可使用 Gradle 8.11.1。

```text
gradle --no-daemon --stacktrace --console=plain :app:testDebugUnitTest :app:assembleDebug
gradle --no-daemon --stacktrace --console=plain :app:assembleRelease
```

本地 SDK 路径写入 local.properties，Python 3.11 路径可写入 python-build.properties 的 buildPython；这些个人配置均被排除，不提交。代码包不含构建依赖缓存或签名密钥。

## 自动构建与发布

- `build-apk.yml`：main 推送、面向 main 的 PR 和手动触发；先运行 JVM 单元测试，再构建五种 Debug APK 并上传 Artifacts。
- `release.yml`：main 上 VERSION 改动、v* 标签和手动触发；验证版本/标签对应关系，运行测试，构建并检查 Release APK 签名及不可调试标记，最后发布 **prerelease**，附五种架构 APK、提交对应源码 ZIP、SHA256 与 build-info。

`VERSION` 当前为 `0.9.0`，CI 的 versionCode 为 `1000000 + Git 提交数量`。本地无 CI 环境变量时的低位回退值不能用来直接覆盖已安装的 CI 版本。保留已有 Git 历史，不要在 ZIP 上重建历史后强制推送。

如果某次发布构建失败，随后修复提交没有改 VERSION，可手动重跑 Release 工作流。某标签已指向另一个提交时必须使用新版本，不能移动旧标签。

签名沿用仓库配置的 ANDROID_KEYSTORE_BASE64、ANDROID_KEYSTORE_PASSWORD、ANDROID_KEY_ALIAS、ANDROID_KEY_PASSWORD；可选 DEBUG_KEYSTORE_BASE64 用于固定 debug 签名。未配置固定签名时可能构建成功但无法覆盖安装旧版，不要为安装测试包直接卸载有数据的旧应用。

本恢复环境仅完成源码树校验、纯 Kotlin 测试、语法及配置检查、core 宿主编译；**没有完成 Android 构建、AGSL/真机验收或本次 GitHub 提交**。以实际 Actions 成功记录和对应产物为准。

许可证见 `LICENSE` 和 `THIRD_PARTY_NOTICES.md`。仅保存你拥有或获准下载的内容，不绕过 DRM、付费或账号访问控制。
