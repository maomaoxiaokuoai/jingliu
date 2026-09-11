# FIX-002：镜流品牌替换与选择菜单白框修复

## 基础版本

基于上一份 `Shiliu-Android-完整源码-编译修复1.zip` 修改，保留 FIX-001 的 `MediaRuntime.kt` 长度编译修复。
显示名称改为 **镜流 / GlassFlow**，版本为 `0.7.1-menu-fix`，versionCode 为 8。
Android applicationId、namespace、Keystore 别名、数据库格式和内部类名没有因品牌更换而修改。

## 菜单问题与改法

旧版 `SpringDropdownMenu` 用可变 alpha 的 Surface 显示 Popup，却没有对后面的设置页面进行模糊。
因此底层卡片、分隔线、文字和开关都会透出来，形成截图中看似逐行白框的现象。

本版所有**设置选择菜单和平台选择菜单**统一使用：

- 一个不透明底面（baseAlpha 始终为 1）、一套圆角、一个外描边和一个外阴影。
- 选项行只画文字、图标与勾选，不建立各自的白色 Surface/Card。
- 行分隔线为统一的细线；点击/键盘焦点反馈为短暂、整行的低强度灰色，不是方形白底。
- 淡入淡出在完整菜单合成之后应用；使用 Offscreen 合成并为外阴影保留空隙，避免阴影被硬切成矩形。
- 按按钮位置选择向上或向下展开，限制到窗口内；间距按 dp 转换，不再混用固定像素。
- 关闭动画保留，但关闭期间不接受第二次选项提交；快速重开会取消旧的关闭动画。
- 深色、浅色、增强对比、减少透明度及减少动效设置有相应处理。

**有意选择的兼容策略：**弹出菜单采用“高遮蔽的磨砂外观”，不是实时跨窗口高斯模糊。
“菜单磨砂质感”开关控制统一表面的细腻高光，不降低底面对正文的遮挡。
普通导航/按钮的 `GlassSurface` 与 Haze 采样代码保持不变，没有为了修菜单关闭整个应用的玻璃效果。
没有声称调用了苹果原生玻璃引擎。

## 启动图标与名称

使用你选中的最后一张“镜流”原始彩色图，只裁出图标部分；没有把名字、英文副标题或宣传语缩成桌面图标。
提供：

- Android 自适应图标背景/前景；圆形、圆角方形遮罩由启动器决定。
- Android 13+ 的单色轮廓资源，供系统主题图标使用。开启系统主题图标时，不会显示原彩色玻璃渲染。
- 常规密度的 PNG 兼容资源，以及此前 `@drawable/ic_launcher` 的兼容引用。
- `branding/launcher-mask-preview.png` 是资源遮罩预览，不是 Android 模拟器/真机截图。

图像来源、裁切坐标和 SHA-256 记在 `branding/asset-manifest.json`；七个平台图标保持原始文件不变。

界面中的关于页、解析页、平板侧栏、分享提示和失效通知同步使用“镜流”。
内部包名仍为 `com.luma.downloader`，不要为了改名字全局替换包名。

## 保存路径兼容

新导出的文件使用 `Download/镜流`（旧 Android 使用本应用专属目录）。
旧 `Download/拾流` 文件不搬动、不删除；FileProvider 保留旧映射并新增镜流映射，旧任务记录的 URI 不重写。
同一 applicationId、相同签名且正常覆盖安装时可保留本机数据；更换签名或主动卸载的情况不能这样保证。
此补丁不会操作你设备上的现有文件。

## 安装：两种方式

### A. 完整源码

将完整 ZIP 解压到新目录，以 Android Studio 打开包含 `settings.gradle.kts` 的 `Jingliu-Android` 根目录。
依赖版本沿用上一份工程，只更新版本号，没有为了菜单新增第三方库。
二进制 Gradle Wrapper 的获取方式沿用原项目；本包没有因为这次改 UI 就附带一个来源不明的 JAR。

### B. 已经在编译的旧工程：推荐增量补丁

解压 `Jingliu-menu-icon-patch.zip`，在其目录打开 PowerShell：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\ApplyPatch.ps1 -ProjectPath "C:\Users\maomao\Desktop\Shiliu-Android"
```

脚本会先校验待覆盖文件和补丁自身的 SHA-256，再建立项目根目录下的 `.jingliu-backup-*`，最后复制改动。
发现与你的原源码不同的文件时，**整批停止，不强行覆盖，也不动任务数据**。
可先加 `-WhatIf` 检查；脚本没有执行编译、安装或卸载。

有自行修改过的文件导致冲突时，对照 `changes.patch` 合并；不要为了省事覆盖 `local.properties`、签名文件或你修改过的下载业务代码。
`payload/` 只含改动的生产文件，不含整个旧项目。不要把 payload 文件夹本身放进 app/src。

覆盖完成后执行：

```powershell
.\gradlew.bat :app:assembleDebug --console=plain --stacktrace
```

或点击 Android Studio 顶部 Run。依赖未改变，不需要重新安装 Android Studio。
更换图标后，启动器可能暂时保留旧快捷方式；可移除桌面快捷方式后重新从应用列表添加，**不必为刷新图标卸载应用**。

## 本次验证与边界

实际执行：

- 16 项菜单纯逻辑/定位检查，其中一项在 10,000 组窗口/方向输入上检查边界（不是 10,000 次设备测试）。
- 175 条原有设置/动效策略断言。
- 76 条原有核心断言，使用合成响应和 localhost HTTP，不访问实际视频平台。
- 50 个 Kotlin/KTS 文件语法解析：0 个语法错误节点。语法解析不是 Android 类型检查。
- XML、资源引用、品牌资源和 ZIP 完整性检查，详见 `tests/menu-fix/`。

另提供 `app/src/androidTest/.../MenuRenderingTest.kt` 的 5 个设备测试：浅/深色菜单背景不串色、选择关闭、长菜单滚动、关闭中快速重开。
**这些设备测试在本环境没有执行，不记为通过。**

当前环境没有可用 Android SDK，官方依赖仓库 DNS 探测失败，未完成 `assembleDebug` 或模拟器/真机视觉验收，没有生成 APK。
本轮没有把手工绘制界面图当作运行截图，也没有承诺整个应用或平台下载功能零 Bug。
Windows 补丁脚本做了路径/摘要设计和静态检查，但本环境无 PowerShell/Windows，未实际在 Windows 执行。

## 参考

- Android Compose 图形层与合成：https://developer.android.com/develop/ui/compose/graphics/draw/modifiers
- Android 自适应与单色图标：https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
