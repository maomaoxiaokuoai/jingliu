# GitHub Actions 在线编译 APK

项目已包含：

```text
.github/workflows/build-apk.yml
```

这个工作流会在 `main`／`master` 推送、Pull Request，以及手动点击 **Run workflow** 时运行。它会安装 JDK 17、Python 3.11、Android SDK 35 和 Gradle 8.11.1，生成 Debug APK，并上传到工作流页面的 **Artifacts**。

## 重要：不要只把 ZIP 文件本身提交到仓库

GitHub Actions 不会自动展开仓库里的 ZIP。正确做法是：

1. 下载并解压完整源码 ZIP。
2. 进入解压后的 `Jingliu-Android` 目录。
3. 确认能看到 `.github`、`app`、`core`、`settings.gradle.kts`。
4. 将这些内容提交到 GitHub 仓库根目录。

使用 Git 命令上传：

```powershell
cd "你的项目目录\Jingliu-Android"
git init
git add .
git commit -m "Initial Jingliu Android source"
git branch -M main
git remote add origin <你的 GitHub 仓库地址>
git push -u origin main
```

仓库已经关联远程地址时，不要重复执行 `git remote add origin`，直接 `git add`、`git commit`、`git push`。

## 从网页手动编译

1. 打开仓库的 **Actions** 页面。
2. 选择 **Build Jingliu APK**。
3. 点击 **Run workflow**。
4. 等待 `Build debug APK` 变绿。
5. 打开该次运行，在页面底部下载：

```text
Jingliu-debug-apk-运行编号
```

解压 GitHub 下载的 Artifact 后可看到 APK、SHA-256 文件和 `build-info.txt`。

## 为什么工作流不依赖 gradle-wrapper.jar

源码包为了避免分发未经当前环境再次核验的二进制 Wrapper JAR，只保留 `gradle-wrapper.properties`。GitHub 工作流使用官方 `gradle/actions/setup-gradle` 下载并缓存固定的 **Gradle 8.11.1**，然后直接执行 `gradle` 命令。

AGP 8.9.x 对应 Gradle 8.11.1、Build Tools 35.0.0 和 JDK 17；工作流按项目现有版本固定这些环境，不自动升级整个工程。

本地 Android Studio 编译仍可使用项目的 `setup-wrapper.bat`，或从你已验证过的旧工程复制同版本官方 `gradle-wrapper.jar`。

## 保持 APK 更新签名一致

如果不设置密钥，每个全新的 GitHub Runner 可能生成不同的 Debug 签名。后一版 APK 可能无法直接覆盖安装前一版，需要卸载旧版。

可在电脑上一次性生成标准 Debug Keystore：

```powershell
keytool -genkeypair -v `
  -keystore debug.keystore `
  -alias androiddebugkey `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000 `
  -storepass android `
  -keypass android `
  -dname "CN=Android Debug,O=Android,C=US"
```

将它转换为 Base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("debug.keystore")) |
  Set-Content -NoNewline "debug-keystore-base64.txt"
```

在 GitHub 仓库中进入：

```text
Settings → Secrets and variables → Actions → New repository secret
```

名称填写：

```text
DEBUG_KEYSTORE_BASE64
```

值粘贴 `debug-keystore-base64.txt` 的完整内容。工作流不会把密钥输出到日志，也不要把 `debug.keystore` 或 Base64 文本提交进源码仓库。

这个方案仍属于测试签名。准备正式发布时，应单独建立 Release 签名流程，并将正式密钥只保存在 GitHub Secrets 或自己的安全设备里。

## 构建失败时查看什么

工作流无论成功或失败，都会尝试上传：

```text
Jingliu-build-reports-运行编号
```

重点打开：

```text
build-logs/assemble-debug.log
build-logs/unit-tests.log
app/build/reports/
core/build/reports/
```

常见失败：

- Python 依赖或 GitHub 源下载失败：重新运行一次，或查看具体下载地址。
- Chaquopy 找不到构建 Python：工作流会自动生成 `python-build.properties` 并指向 setup-python 安装的 Python 3.11。
- Android SDK 缺失：工作流会安装 API 35、Build Tools 35.0.0 和 platform-tools。
- Kotlin／Compose 源码错误：在 `assemble-debug.log` 中搜索 `e:`、`Compilation error` 或具体 `.kt` 文件路径。
- APK 已编译但单元测试失败：APK Artifact 仍会保留，测试步骤被设置为诊断性、不阻断已成功生成的 APK。

## 安全边界

工作流不包含账号 Cookie、二维码、签名私钥、`local.properties` 或个人 Python 路径。不要将真实 Cookie、正式 Keystore、邮箱密码等信息提交到 GitHub；源码仓库为公开仓库时尤其要注意。
