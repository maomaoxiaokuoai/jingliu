# Release 发版流程（Tag 自动构建）

仓库：`https://github.com/maomaoxiaokuoai/jingliu`，默认分支 `main`。

## 1. 两种工作流分工

- `.github/workflows/build-apk.yml`：`push main` / PR / 手动 → 只做编译验证（Debug 分包能编过即可），不上传任何 APK，不创建 Release。
- `.github/workflows/release.yml`：`push tag v*` → 只编译 Release 分包 → 自动创建 GitHub Release 并挂附件：
  - `Jingliu-<tag>-<abi>.apk`（正式签名，有则用正式，无则 debug 兜底并在日志警告）
  - 例：Tag `v0.8.2` → `Jingliu-v0.8.2-arm64-v8a.apk`，Release 标题为 `Jingliu v0.8.2`
- Release 说明区刻意留空：工作流写入一个零宽空格占位文件。不要删掉它——正文为空时 GitHub 会自动把该 Tag 的提交信息显示出来，而 HTML 注释会被当成可见文本。

## 2. 用户该下哪个包（ABI 对照）

每个 Release 只有 5 个正式包，不再产出 debug 包、`SHA256SUMS.txt`、`build-info.txt`：

| 文件中的 `<abi>` | 给谁用 |
|---|---|
| `arm64-v8a` | 绝大多数真机，优先下这个，体积最小 |
| `armeabi-v7a` | 老 32 位手机 |
| `x86_64` | 电脑模拟器 |
| `x86` | 老 32 位模拟器 |
| `universal` | 不知道自己机型的直接装这个（体积最大，通吃） |

分包原理：每个包只含一个架构的 native 库（Chaquopy Python 运行库、ffmpeg、`lxml/aiohttp` 原生部分），`universal` 含全部 4 个架构。分包 versionCode = `abi码*1000+versionCode`（universal 保持原值），各包版本码唯一。

## 3. 一次性配置 Secrets

在 `Settings → Secrets and variables → Actions` 新建：

| Secret | 说明 |
|---|---|
| `DEBUG_KEYSTORE_BASE64` | 稳定 debug 签名，方法见 `docs/GITHUB-ACTIONS-BUILD.md` |
| `ANDROID_KEYSTORE_BASE64` | 正式 release keystore 的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | 正式库密码 |
| `ANDROID_KEY_ALIAS` | 正式别名 |
| `ANDROID_KEY_PASSWORD` | 正式 key 密码 |

本地生成正式库（只做一次，不要提交）：

```powershell
keytool -genkeypair -v `
  -keystore release.keystore `
  -alias jingliu `
  -keyalg RSA -keysize 2048 -validity 10950 `
  -storepass '你的库密码' -keypass '你的key密码' `
  -dname "CN=Jingliu,O=Jingliu,C=CN"
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) |
  Set-Content -NoNewline "release-keystore-base64.txt"
```

把 `release-keystore-base64.txt` 内容粘贴到 `ANDROID_KEYSTORE_BASE64`，其余 3 个照填。`release.keystore` 本体受 `.gitignore`（`*.jks/*.keystore`）保护，不会进仓库。

本地验证正式签名（可选）：

```powershell
$env:ANDROID_KEYSTORE_FILE="C:\path\to\release.keystore"
$env:ANDROID_KEYSTORE_PASSWORD="***"
$env:ANDROID_KEY_ALIAS="jingliu"
$env:ANDROID_KEY_PASSWORD="***"
.\gradlew.bat :app:assembleRelease --console=plain --stacktrace
```

## 4. 每次发版操作

1. 改 `app/build.gradle.kts`：`versionCode +1`，`versionName` 与 Tag 去掉 `v` 后一致。
   例：Tag `v0.8.2` → `versionCode = 20`，`versionName = "0.8.2"`。
2. 提交并推送到 `main`：
   ```powershell
   git add app/build.gradle.kts
   git commit -m "Release v0.8.2"
   git push origin main
   ```
3. 打 Tag 并推送（即触发构建）：
   ```powershell
   git tag v0.8.2
   git push origin v0.8.2
   ```
4. 去 `Actions → Release Jingliu APK` 看绿勾，再去 `Releases` 按上面的 ABI 表下载对应的包。
5. 手动触发（不打 Tag 只验证）：`Actions → Release Jingliu APK → Run workflow`，产物在 Artifact，不会创建 Release。

## 5. 注意事项

- Debug 与 Release 签名不同，不能直接互盖安装，切换时需卸载重装。
- 没配正式 Secrets 时 Release 仍能编出（debug 兜底，构建日志有警告），正式分发前务必配好。
- 不要提交 `*.apk / *.keystore / local.properties / qr-auth.properties / *_base64.txt`。
- 远端旧 `edgetunnel` 分支已备份到本地临时目录，默认分支为 `main` 后可删除远端 `edgetunnel`。
