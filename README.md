# 镜流 Jingliu

镜流是一个 Android 原生视频与图片解析、下载应用。本仓库只保留**能够生成 APK 的生产源码**、依赖声明、许可证，以及 **GitHub Actions 自动构建与自动发布 APK** 所需文件。

仓库中不再保留本地 Android Studio 教程、旧版本说明、测试包、制图中间文件、一次性检查脚本、独立服务端或本机构建脚本。

## 保留的工程结构

```text
.github/workflows/
  build-apk.yml          推送 main、Pull Request 或手动运行时构建并上传 Debug APK
  release.yml            推送 v* Tag 时构建并上传 GitHub Release
app/
  build.gradle.kts
  proguard-rules.pro
  src/main/              Android Manifest、Kotlin/Compose、资源和内嵌 Python 适配层
core/
  build.gradle.kts
  src/main/              解析、网络、账号会话和下载策略等生产 Kotlin 源码
build.gradle.kts
gradle.properties
settings.gradle.kts
python-runtime-requirements.txt
LICENSE
THIRD_PARTY_NOTICES.md
```

## 自动构建 APK

向 `main` 推送代码、提交面向 `main` 的 Pull Request，或手动运行 **Build Jingliu APK**，工作流会：

1. 在 GitHub Runner 安装固定版本的 JDK 17、Python 3.11、Android SDK 35、Build Tools 35.0.0 和 Gradle 8.11.1。
2. 构建 `arm64-v8a`、`armeabi-v7a`、`x86_64`、`x86`、`universal` 共 5 个 Debug APK。
3. 将 APK 上传到该次 Actions 运行的 **Artifacts**，保留 14 天。

Debug APK 的内部版本名自动采用“最近 Tag 去掉 `v` + `-dev.运行序号`”，`versionCode` 自动使用“1,000,000 + 当前 Git 提交总数”。高基准用于兼容旧版本曾采用的分架构版本号，避免安装时被判定为降级。

## 自动发布正式版本

创建并推送以 `v` 开头的 Tag，例如：

```bash
git tag v0.8.3
git push origin v0.8.3
```

**Release Jingliu APK** 会把 Tag 去掉开头的 `v` 后写入 APK 的内部 `versionName`，自动生成递增 `versionCode`，随后把 5 个架构 APK 上传到同名 GitHub Release。

手动运行 Release 工作流时，只会把 5 个 Release APK 上传到 Actions Artifacts，不会创建正式 GitHub Release。

架构选择：

- `arm64-v8a`：绝大多数安卓手机，优先下载。
- `armeabi-v7a`：老旧 32 位安卓手机。
- `x86_64`：64 位安卓模拟器。
- `x86`：老旧 32 位安卓模拟器。
- `universal`：不清楚架构时使用，文件最大。

## 发布签名

为了让后续版本能够直接覆盖安装，建议在仓库 Actions Secrets 中配置：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Debug 构建可选配置 `DEBUG_KEYSTORE_BASE64`，用于保持 Debug APK 签名稳定。

应用需要连接外部授权桥接服务时，可设置仓库 Actions Variable `QR_AUTH_BRIDGE_URL`；只允许填写纯 HTTPS 域名源地址，不能包含账号、密钥、路径或查询参数。

未配置正式签名时，Release 仍可构建，但会回退到 Debug 签名；不同运行之间若签名变化，已安装版本可能无法直接覆盖升级。

## 许可证

项目许可证和第三方依赖声明属于发布源码及 APK 的必要组成部分，因此保留 `LICENSE` 与 `THIRD_PARTY_NOTICES.md`。
