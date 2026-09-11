# 控件覆盖与保留行为

| 区域 | 实际调用路径 | 未承诺的部分 |
|---|---|---|
| 主按钮、文字/图标按钮 | GlassButtonBody / LiquidActionButton / GlassTextButton / GlassIconButton | 不修改按钮实际业务行为；尚未设备点击验收 |
| 输入与文件搜索 | 原生 TextField + GlassSurface；IME、光标、选择保持原生 | 系统键盘不玻璃化 |
| 开关/滑块 | 原 toggleable / 原生 Slider 输入 + 玻璃轨道和滑块 | 不以动画值写回开关业务；触控跟手需真机验证 |
| 图片/合集选择、单选 | GlassSelectionMark / GlassRadioButton | 选择规则和解析格式来源未改变 |
| 下拉菜单/确认框 | 同 Activity GlassOverlayHost + GlassSurface | 不生成第二个 Android Popup；菜单密集文本保持必要遮蔽 |
| 下载/浏览器进度 | GlassLinearProgressIndicator / GlassCircularProgressIndicator | 不产生虚构进度或速度；未知进度仍为等待状态 |
| 手机底栏、平板侧栏、设置导航 | 共享页面或背景纹理；选中块保留原弹簧 | 不承诺 120Hz 下无掉帧 |
| 浏览器和预览操作栏 | GlassWindow / GlassTopAppBar，按钮使用相同材质 | 不采样敏感 WebView 页面，不改变平台网页，PlayerView 内部控制器未重绘 |
| 账号 / 扫码 | 现有会话流程、单保存按钮和二维码原像素 | 无新的平台授权能力；Cookie 隔离和来源限制未解除 |

## 本次补全的缺失配套

上一轮仅有 8 个已返回的生产源码文件，缺少它们引用的 LensPolicy 等定义，也没有覆盖所有业务页上的控件调用。本次在完整 0.8.0 修复底包中补齐了这些定义、参数、入口和采样关系，不能把这一轮描述成“不做任何接线而直接压缩文件”。

## 参考（不等于直接集成这些第三方控件库）

- https://github.com/Kyant0/AndroidLiquidGlass — 上一轮选定的 Compose 背景/材质方向。
- https://github.com/QWEA0/Liquid-Glass-Android — 上一轮选定的 View 系统光学表现参考。
- https://developer.android.com/develop/ui/compose/graphics/draw/modifiers — 图层记录/重放与 drawWithCache。
- https://developer.android.com/reference/android/graphics/RenderEffect — Android 图层效果链。
- https://developer.android.com/reference/android/graphics/RuntimeShader — Android AGSL 运行时着色器。

本包不附 Apple 字体、平台私有光学代码或第三方完整 Demo。不声称复制了所有未实现的 Demo 控件。
