# 编译和运行排错

## 编译

先运行 setup-wrapper.bat。若失败在 DNS/HTTPS/下载超时，尚未进入 Kotlin 编译；检查电脑网络或使用 README 中的官方文件离线校验方法。不要删除 HTTPS/哈希校验。

Android Studio 请打开有 settings.gradle.kts 的根目录。SDK 路径由本机 local.properties 决定。若显示 SDK 35 缺失，在 SDK Manager 安装；若 JDK 错误，把 Gradle JDK 选成 17。不要一次升级全部 AGP/Kotlin/Compose 依赖来掩盖错误。

捕获完整构建日志：

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --console=plain *> build-log.txt
```

优先看最早的 `e:`、异常和 `Caused by`，不要只复制 `BUILD FAILED`。运行后产生的 private Cookie 文件不应附在报错中。

## 运行

- 解析失败：保留链接类型、是否登录、平台和网络条件；区分网络超时/403/412/429/不支持格式/账号失效。不要高频重试同一风控请求。
- libpython/libffmpeg 缺失：核对设备 ABI 与依赖下载是否完整，Manifest 的 extractNativeLibs 与 packaging.useLegacyPackaging 不要擅自关闭。引擎内部路径固定匹配 0.18.1，升级需一起审计。
- 无通知：Android 13+ 检查通知权限；下载仍受系统后台/电池策略影响。
- 保存失败：检查磁盘、MediaStore 权限与文件是否被其他程序移动；保留任务重试，不以移除列表代替删除文件。
- 会话导入：非 B站当前显示未验证身份是实情，不是假登录成功。Google 用外部浏览器和用户 Cookie 导入。
- B站账号过期：先重新扫码，不手工修改 Cookie 的有效期。自动维护依赖真实 refresh_token，并不总能成功。

## 本机账本或加密区损坏

启动失败不会静默覆盖旧文件。先停止应用/正在运行的下载，并备份公共 Download/拾流 中需要保留的媒体。使用 Android Studio 的 Device Explorer（Debug 构建）查看应用私有目录，仅备份到自己安全的位置；会话属于敏感数据，不上传。首次安装无重要数据时可以清除应用数据重新启动，但这会移除本机账号、设置和任务账本，不能撤销；Android 8–9 应用专属下载也需先外部复制。

当前没有用户可操作的旧账本自动迁移/损坏修复 UI，不应承诺自动恢复所有文件。源码中的错误提示保守地保留原记录。
