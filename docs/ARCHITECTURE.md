# 模块和真实任务生命周期

`MainActivity → LumaViewModel → ExtractorRouter → NativeExtractors / MediaRuntime → Media / Format / MediaEntry`

`LumaViewModel.enqueue → TaskStore.add → QueueController → WorkManager.DownloadWorker → ResumableTransfer / MediaRuntime.downloadStream → mux → MediaPublisher → COMPLETE`

`删除确认 → QueueController.delete → 标记 DELETING → 取消进程/连接 → DeleteWorker 等待同任务 Mutex → 删除 pending/final URI 与临时目录 → 移除账本`

取消确认框不会调用 delete。失败的删除保留 DELETE_FAILED，不把列表移除冒充文件已删。

## 状态与并发

- 每个任务有固定 UUID；路径只允许 UUID，不接受服务器标题作目录。
- 每次新的 WorkRequest 写入 executionId，旧 Worker 不可以覆盖新一轮进度/失败状态。
- 同任务由 Mutex 串行；不同任务通过并发名额调度。当前任务的地址在每次开始/重试时重新获取，不永久依赖过期 CDN URL。
- TaskStore 的原子 JSON 是账本；WorkManager 是调度器。启动检查已有 WorkRequest，避免重复入队；不使用只有内存的假任务。
- 下载进度来自实际字节；分离视频/音频/合并/保存阶段独立显示。合并本身未伪造百分比。
- 减少并发不会突然截断已有任务，新增任务等待名额；Wi-Fi-only 在后续进度采样检查。系统 VPN 可能隐藏底层 Wi-Fi transport，需要真机验证，不能视作精确绑定物理 Wi-Fi。

## UI

使用 Compose 原生布局、文字输入、Slider、系统返回、权限和文件选择，玻璃导航/弹簧按钮/开关自定义绘制。内容与背景模糊操作层分离；Android 26–30 使用不透明回退。不是苹果原生 UIKit/SwiftUI，也没有逐帧相等保证。

## 必须在设备补验

后台/前台切换、低内存重启、应用被强行停止、长任务系统配额、通知权限拒绝、共享存储满、Android 8/10/13/15、横竖屏与分屏、连续暂停/恢复/删除，以及不同 ABI 的 Python/FFmpeg 二进制加载。恢复网络后执行时间由 WorkManager 调度，不承诺立即恢复或永久后台运行。
