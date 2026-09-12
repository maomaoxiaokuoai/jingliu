# Liquid Glass 0.9.0–0.9.1：完整工程与交互光学升级

## 状态

本完整工程以 `maomaoxiaokuoai/jingliu` 提交 `6ca9812553ecfedff1253b6fb38fee559704f2f1` 为基线。2026-09-12 恢复时，基线整棵源码树哈希与远端完全一致，随后合入实际保存下来的 0.9.0 候选改动。0.9.1 在此基础上完成液态套件扩展、编译修复与发布。

## 参考来源及实际适配方式

参考了 Kyant0/AndroidLiquidGlass 的 Compose Catalog：

- `app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt`，读取时文件 blob 为 `21067726b258c7fb1a3b29463c38f1df1c3439ee`。
- `app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidToggle.kt`，文件 blob 为 `dcc6531ad27d601f9a1d0ed2cfd3748d30acc6fb`。
- `app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt`，文件 blob 为 `2d7e4455a0eed3166057b157d0b75a8931c8705f`。
- 上游地址： https://github.com/Kyant0/AndroidLiquidGlass 。这些文件读取自 `kmp` 分支，分支可能继续变化，以上 blob 标识读取时内容。

参考了 QWEA0/Liquid-Glass-Android README 对 SDF 边缘折射、按压鼓起、色散和平台适配的说明： https://github.com/QWEA0/Liquid-Glass-Android 。没有把它的 View SDK 包装进 AndroidView，也没有复制其旧系统渲染后端。

本补丁是在镜流已有 GlassSource / GlassSurface / MotionTokens 架构上的重新实现与适配，不是给 Gradle 添加依赖后就宣称迁移全部组件。Kyant 的关键参考点是“轨道/标签与镜片分层”、按住时扩大镜片、速度相关拉伸及弹簧回落；QWEA0 的参考点是按压驱动光学参数，而不只是改变白色高光。新代码没有逐文件搬运上游 SDK 或复制 Apple 的渲染实现。

原仓库 LICENSE 与 THIRD_PARTY_NOTICES.md 保留不变。本补丁的镜流侧改动沿用原仓库 GPL-3.0 许可；外部项目的名称、作者及其各自许可不因此改变。如果后续直接复制上游实现文件，应同时保留其原始许可/版权通知，不能仅保留这一段参考说明。

## 0.9.1 新增（来源→实现→调用点→测试）

- 底部导航长按/按住滑动/跨页连续拖动、选中镜片跟手、页面与镜片联动：来源 Kyant LiquidBottomTabs + DampedDragAnimation → `LiquidNavigationDock` + `LiquidTabMotion` + `RetainedSceneHost` 连续位置 → 真机：长按拖过四页再拖回，取消恢复起始页；测试 `LiquidGestureRegression` 17 组。
- 按压放大/拉伸/阻尼/回弹：来源 Kyant 阻尼与速度拉伸 → `LiquidGestureMath.stretchX/Y` + `MotionPolicy` 弹簧 + `controlLift` → 导航镜片/开关拇指/滑块拇指/按钮 `elasticPress`；测试拉伸边界。
- 按钮/图标/筛选/菜单统一液态反馈：自有 `GlassButtonBody` + `GlassFilterChip` 按压量化 → 所有主按钮、文本/轮廓/图标按钮、筛选 chips、菜单项；测试：快速点击不卡顿，禁用态无高光。
- 设置每模块真实背景折射：自有 `GroupCard` 每项独立 `GlassSource` + `GlassBackdrop` 共享丝带 → `SettingsScreen` 每个 `spec` 独立卡片；深浅色、竖屏/横屏平板均复用同一管线。
- 开关/滑块长按保持液态：来源 Kyant LiquidToggle + QWEA 按压光学 → `LiquidSwitch`/`LiquidSlider` 保持 `pressure=1` + 触摸鼓起 → 长按不移动仍折射，取消不提交；测试 `switchTarget`。
- 输入/搜索焦点玻璃变化：自有焦点管线 → `GlassTextField` 焦点动画 `pressure*0.28` + 触摸鼓起 → 解析页/文件搜索框；文字绘制在镜片之上，不扭曲字形。
- 菜单/弹窗展开液态变化：自有 `GlassOverlayHost` → `SpringDropdownMenu`/`GlassAlertDialog` 用 `LocalGlassMotion` 驱动 `pressProgress` → 所有设置选项菜单、确认弹窗。
- 双玻璃 smooth-min 黏连：来源 QWEA 融合 → `LiquidFusionMath.smin/fusionFactor` + AGSL `sminPoly` + `fuseStrength` → 导航镜片跨页时 `0.7*pressure*(1-between)`；测试融合边界。
- QWEA 逆幂边缘折射：`inversePowerTail` + AGSL `1/pow(1+inside/(band*0.25),2)` → 全镜片 `depth*tail`；测试边缘衰减。
- Kyant 圆弧透镜：`arcBevel` + AGSL `1-pow(t,1.6)` → 同一 `tail*(0.72+0.28*arc)`；保留锐利边缘同时中段可见。
- RGB/七色边缘色散：QWEA/Kyant 色散 → AGSL 三次 `scene.eval`（R/G/B）近似七带 → `dispersion` 设置；省资源模式仍限制模糊，色散不清零文字。
- 边缘柔化：自有 `edgeSoftPx` + AGSL `soft` → `edgeSoft` 0–100% → 设置滑块实时预览。
- 局部触摸鼓起：来源 QWEA 按压 → `touchFalloff` 高斯 + AGSL `exp(-0.5*(d/sigma)^2)` → 导航/开关/滑块/按钮/输入框；`touchBulge` 开关控制。
- 高光随手指/姿态、重力感应、双向镜面：Kyant 交互高光 + QWEA 环境高光 → `GravityLightProvider`（加速度计、低通、横竖屏重映射、平放回退、后台/省电/减少动态时停） + AGSL 双高光 `specKey+specFill` → 全 `GlassSurface`；测试 `gravityToLight`。
- 自适应明暗：自有 `LumaTheme` + `palette.dark` → 边缘/丝带/阴影在深浅色分别取值；对比度增强时 `tint>=0.82`。
- Clear/Regular/Frost/有色介质/饱和度：自有 `MaterialPolicy` → `material` 四档 + `tint` 有色 + `saturation` 100–180% + AGSL 饱和度保护 → 设置实时预览 `GlassMaterialPreview`。
- 滚动顶部/底部渐进模糊：自有轻量渐隐 → `scrollEdgeFade` + `rememberScrollEdgeAlphas` → 设置页与解析/下载/文件列表；`scrollFade` 开关控制，无额外离屏。
- 实时材质预览：自有 → `GlassMaterialPreview` 用生产 `GlassSurface` → 设置玻璃区首项。
- 降级：减少动态/透明/高对比/省电：`GlassPolicy` + `LensPolicy(powerSave)` + `MotionMode.OFF` → API 33+ 镜片 / 31–32 模糊 / 26–30 实色，着色器失败回退，省电强制实色。
- 性能：共享 `GlassSource`/`GlassSurface`/单例 `LiquidShader.SOURCE`，`drawWithCache` + 量化 uniform（pressure 200 档、位置 100/60 档），`GraphicsLayer` 无 Bitmap/PixelCopy/无限重绘；`LocalGlassMotion` 在放置阶段读取，避免每标签重组。

## 未迁移（诚实边界）

没有移植两个演示应用的全部页面，没有 View/OpenGL 旧系统后端（仍用模糊/实色回退），没有 Android 16 专有路径与字形 SDF 实验（成本/收益不足），没有系统级玻璃与自动截图回归。内置浏览器 WebView、视频 SurfaceView、二维码像素、输入字形只做上层绘制，不进镜片采样。

## 已写入的改动

### 底部导航与页面联动

`LiquidNavigationDock` 在完整底栏上观察按下、超过触摸阈值后的横向拖动及释放/取消。未拖动时保留 selectable 点击、键盘和 TalkBack 语义。拖动开始后根据手指位置得到连续页索引，跨过相邻页中点时同步选中状态。取消时恢复起始页。

`LiquidTabMotion` 将同一位置状态提供给镜片和 RetainedSceneHost。页面位移在布局放置阶段读取，不让每个标签读取每一帧动画值。连续模式只放置相邻可见页面，保留原页面和滚动状态；宽屏、关闭标签形变或选择淡入淡出时保留原路径。

底栏采样源包含背景、底栏和标签，但不包含选中镜片。选中镜片是独立兄弟层，因此可以折射标签而不把自己递归采样进去。

### 设置模块

调低分组的遮蔽性染色，背景改为静态有细节的柔和色带。长设置页从“全部参数包进一个巨大 GroupCard”改为每项独立 GroupCard，并使用稳定 key。这样每个模块都有独立、有限尺寸的材质层。

分组仍只采样共享背景，不采样分组自身的文字、二维码或可编辑内容。分区域玻璃开关、减少透明度和增加对比度仍优先。

### 开关与滑块

开关保留 toggleable 语义和单一 Boolean 数据源；取消拖动不会提交开关值。采样源扩展为完整控件高度，包含轨道周围背景，避免放大后只看到白底。按住时白色滑块逐渐变成透明镜片，保持按压则保持折射，拖动释放后用原动效参数回落。

滑块保留 Material3 Slider 的轨道点击、拖动、键盘和 TalkBack 实现，只替换轨道/拇指外观。参数变化仍通过原回调处理，不用视觉动画反向修改业务值。异常参数范围会禁用控件并采用安全显示范围，而不是传递 NaN 给渲染器。

### 光学层及其他现有控件

`pressure` 与 `lightPoint` 进入 AGSL，驱动边缘折射、中心鼓起和高光。每个表面拥有独立的 shader 实例；按压更新只重建需要更新的 RenderEffect，不共享可变 uniform。

GlassSurface 增加不消费手势事件的按压观察。通过它绘制的既有按钮、选择控件、字段、菜单和分组可使用统一按压光学效果；不替换它们原本的点击、登录或下载动作。弹性幅度沿用 controlLift 和动效设置，而不是硬编码成永远最大。

## 未完成或未迁移

没有移植两个演示应用的全部页面，也没有实现所有陀螺仪光照、任意形状融合、系统级玻璃、View/OpenGL 旧系统后端或自动 UI 截图回归。没有重写内置浏览器、视频画面、二维码像素与媒体解析引擎。

完整 AGSL 镜片仍要求 Android 13+ / API 33+ 及硬件加速。Android 12 / API 31–32 为模糊回退；API 26–30 为可读实色回退。软件渲染、减少透明度、分区域关闭及着色器失败也可能回退。这一点不能通过提高“折射强度”绕过。

没有证明 120Hz 稳定流畅。布局尺寸变化时的 shader/离屏层开销、低端 GPU、长列表快速滚动、旋转屏幕与多指取消仍需要实机分析。代码没有后台壁纸动画或每帧 Bitmap 截屏，但这不等于没有 GPU 成本。

## 业务边界

不修改 auth、engine、download、Python 提取器、Cookie 存储、二维码生成与网络请求文件。不删除原解析平台，不更换所选引擎，不重置账号/下载设置。对 UI 的修改仍可能产生交互回归，必须走下述验收，而不能仅因为业务文件没改就认定业务完全无风险。

## 构建与发布

VERSION 是版本名来源；debug 工作流增加 testDebugUnitTest。release 工作流在 main 上 VERSION 变化、版本标签推送或手动触发时运行，检查版本/标签一致性，然后运行测试并编译 release APK。只有成功后才发布预览 Release，包括四种 ABI、通用 APK、对应提交的源码归档、校验值和构建信息。

不会移动已属于其他提交的旧版本标签。签名沿用仓库已有 secrets 路径；缺少固定签名时仍可能使用临时 debug 签名，不能保证覆盖安装旧 APK。构建类型 release 与签名类型是两回事，工作流另外检查 APK 不可调试。

当前补丁制作环境没有执行这些 GitHub Actions 步骤，不能将工作流代码当作一次成功运行记录。

## 真机验收清单（全部待执行）

1. 在 Android 13+ 硬件加速设备上长按底栏不松手，依次拖过四个标签再拖回；镜片和页面应连续联动，取消恢复起始页。
2. 快速连续点击标签，确认没有黑屏、空白页、重复回调、列表位置丢失；宽屏侧栏仍能正常导航。
3. 开关按住不移动、来回拖动、拖动取消、键盘/TalkBack；确认一次有效手势只提交正确值。
4. 滑块长按、轨道点击、快速连续拖动；确认实际数值与保存值一致，没有把动画中间值写入设置。
5. 浏览每个设置分组，检查背景可见、文字清楚、菜单不重影、滚动没有裁切/白块；旋转屏幕后再次检查。
6. 分别测试关闭玻璃、关闭按压高光、减少动态效果、减少透明度、增加对比度、深色模式；不得因关闭高光就强制关闭全部光学反馈。
7. Android 12、Android 8–11、软件渲染或着色器失败时，应使用所说明的回退，不应崩溃。
8. 实际运行一次解析、登录、图片预览、下载、暂停/继续、文件打开/分享与扫码。使用帧时间工具测量快速切页和设置滚动，再决定是否标记正式版。
