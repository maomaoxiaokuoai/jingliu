package com.luma.downloader.data


/** Visible options have render or business consumers; unavailable platform optics remain hidden. */
enum class SettingKind { CHOICE, TOGGLE, RANGE }
data class SettingSpec(val key:String,val section:String,val title:String,val kind:SettingKind,val default:String,
 val options:List<Pair<String,String>> = emptyList(),val minimum:Float=0f,val maximum:Float=1f,val step:Float=1f,val unit:String="",val integration:Boolean=false)
object SettingsCatalog {
 val all:List<SettingSpec> = listOf(
  SettingSpec("parserEngine","engine","解析引擎",SettingKind.CHOICE,"auto",options=listOf("auto" to "自动 · 本机原生优先","native" to "原生 Kotlin","ytdlp" to "yt-dlp · 本机通用","parse_video_py" to "parse-video-py · 手机本地")),
  SettingSpec("performance","glass","渲染策略",SettingKind.CHOICE,"balanced",options=listOf("balanced" to "均衡","efficient" to "省资源")),
  SettingSpec("reduceTransparency","accessibility","减少透明度",SettingKind.TOGGLE,"false"),
  SettingSpec("increaseContrast","accessibility","增强对比度",SettingKind.TOGGLE,"false"),
  SettingSpec("reduceMotion","accessibility","减少动态效果",SettingKind.TOGGLE,"false"),
  SettingSpec("switchColor","buttons","开关颜色",SettingKind.CHOICE,"system",options=listOf("system" to "系统绿色","accent" to "跟随强调色")),
  SettingSpec("theme","appearance","外观模式",SettingKind.CHOICE,"light",options=listOf("light" to "浅色","dark" to "深色","auto" to "跟随系统")),
  SettingSpec("accent","appearance","强调色",SettingKind.CHOICE,"blue",options=listOf("blue" to "蓝色","graphite" to "石墨","green" to "绿色","orange" to "琥珀","rose" to "玫瑰","violet" to "紫色")),
  SettingSpec("density","appearance","内容间距",SettingKind.CHOICE,"comfortable",options=listOf("comfortable" to "宽松","compact" to "标准")),
  SettingSpec("fontScale","appearance","文字大小",SettingKind.RANGE,"100",minimum=90.0f,maximum=120.0f,step=5.0f,unit="%"),
  SettingSpec("corner","appearance","分组圆角",SettingKind.RANGE,"20",minimum=10.0f,maximum=30.0f,step=1.0f,unit="dp"),
  SettingSpec("groupGap","appearance","分组间距",SettingKind.RANGE,"28",minimum=16.0f,maximum=40.0f,step=2.0f,unit="dp"),
  SettingSpec("titleLines","appearance","视频标题行数",SettingKind.CHOICE,"3",options=listOf("2" to "2 行","3" to "3 行","5" to "5 行")),
  SettingSpec("stats","appearance","显示视频互动数据",SettingKind.TOGGLE,"true"),
  SettingSpec("material","glass","材质",SettingKind.CHOICE,"glass",options=listOf("glass" to "清透玻璃","regular" to "标准玻璃","frost" to "细腻磨砂","solid" to "实色")),
  SettingSpec("blur","glass","背景模糊",SettingKind.RANGE,"22",minimum=0.0f,maximum=36.0f,step=1.0f,unit="dp"),
  SettingSpec("transparency","glass","透光程度",SettingKind.RANGE,"68",minimum=15.0f,maximum=90.0f,step=1.0f,unit="%"),
  SettingSpec("refraction","glass","镜片折射",SettingKind.RANGE,"12",minimum=0.0f,maximum=36.0f,step=1.0f,unit="dp"),
  SettingSpec("lensBevel","glass","镜片边缘宽度",SettingKind.RANGE,"20",minimum=4f,maximum=40f,step=1f,unit="dp"),
  SettingSpec("dispersion","glass","边缘色散",SettingKind.RANGE,"4",minimum=0f,maximum=12f,step=1f,unit="%"),
  SettingSpec("highlight","glass","边缘高光",SettingKind.RANGE,"66",minimum=0.0f,maximum=100.0f,step=1.0f,unit="%"),
  SettingSpec("saturation","glass","背景饱和度",SettingKind.RANGE,"125",minimum=100.0f,maximum=180.0f,step=5.0f,unit="%"),
  SettingSpec("shadow","glass","悬浮阴影",SettingKind.RANGE,"14",minimum=0.0f,maximum=40.0f,step=1.0f,unit="%"),
  SettingSpec("grain","glass","细腻颗粒",SettingKind.TOGGLE,"false"),
  SettingSpec("grainStrength","glass","颗粒强度",SettingKind.RANGE,"20",minimum=0f,maximum=45f,step=1f,unit="%"),
  SettingSpec("glassNav","glass","导航栏使用玻璃",SettingKind.TOGGLE,"true"),
  SettingSpec("glassButtons","glass","按钮使用玻璃",SettingKind.TOGGLE,"true"),
  SettingSpec("glassMenus","glass","菜单使用液态玻璃",SettingKind.TOGGLE,"true"),
  SettingSpec("glassSwitches","glass","开关滑块使用玻璃",SettingKind.TOGGLE,"true"),
  SettingSpec("glassFields","glass","输入与搜索使用玻璃",SettingKind.TOGGLE,"true"),
  SettingSpec("glassCards","glass","分组使用液态玻璃",SettingKind.TOGGLE,"true"),
  SettingSpec("gravityLight","glass","重力感应高光",SettingKind.TOGGLE,"true"),
  SettingSpec("lensFusion","glass","双镜片黏连融合",SettingKind.TOGGLE,"true"),
  SettingSpec("touchBulge","glass","触摸鼓起",SettingKind.TOGGLE,"true"),
  SettingSpec("scrollFade","glass","滚动边缘渐隐",SettingKind.TOGGLE,"true"),
  SettingSpec("edgeSoft","glass","边缘柔化",SettingKind.RANGE,"55",minimum=0f,maximum=100f,step=1f,unit="%"),
  SettingSpec("buttonStyle","buttons","主按钮样式",SettingKind.CHOICE,"tinted",options=listOf("glass" to "清透","tinted" to "浅色着色","solid" to "实色","outline" to "轮廓")),
  SettingSpec("buttonShape","buttons","主按钮形状",SettingKind.CHOICE,"capsule",options=listOf("capsule" to "胶囊","rounded" to "圆角矩形")),
  SettingSpec("buttonHeight","buttons","主按钮高度",SettingKind.RANGE,"50",minimum=44.0f,maximum=60.0f,step=1.0f,unit="dp"),
  SettingSpec("press","buttons","按压幅度",SettingKind.RANGE,"72",minimum=0.0f,maximum=100.0f,step=1.0f,unit="%"),
  SettingSpec("pressLight","buttons","触碰时提亮",SettingKind.TOGGLE,"true"),
  SettingSpec("pointerLight","buttons","高光跟随指针",SettingKind.TOGGLE,"true"),
  SettingSpec("motion","motion","动态效果",SettingKind.CHOICE,"standard",options=listOf("standard" to "灵动弹簧 · 默认","soft" to "轻柔过渡","reduce" to "减少动态效果")),
  SettingSpec("duration","motion","响应节奏",SettingKind.RANGE,"420",minimum=150.0f,maximum=650.0f,step=10.0f,unit="ms"),
  SettingSpec("bounce","motion","弹性强度",SettingKind.RANGE,"64",minimum=0.0f,maximum=100.0f,step=1.0f,unit="%"),
  SettingSpec("controlLift","motion","滑块按住放大",SettingKind.RANGE,"116",minimum=100f,maximum=128f,step=2f,unit="%"),
  SettingSpec("tabTransition","motion","一级页面切换",SettingKind.CHOICE,"slide",options=listOf("slide" to "弹簧滑移","fade" to "柔和淡入")),
  SettingSpec("tabDistance","motion","一级页面位移",SettingKind.RANGE,"44",minimum=24f,maximum=120f,step=4f,unit="dp"),
  SettingSpec("swipeBack","motion","边缘滑动返回",SettingKind.TOGGLE,"true"),
  SettingSpec("tabMorph","motion","导航选中块滑动",SettingKind.TOGGLE,"true"),
  SettingSpec("menuMorph","motion","菜单贴近按钮展开",SettingKind.TOGGLE,"true"),
  SettingSpec("navCollapse","motion","滚动时收拢导航",SettingKind.TOGGLE,"false"),
  SettingSpec("remember","privacy","记住账号",SettingKind.TOGGLE,"true"),
  SettingSpec("hideProfile","privacy","隐藏头像与昵称",SettingKind.TOGGLE,"false"),
  SettingSpec("expireNotify","privacy","会话失效提醒",SettingKind.TOGGLE,"true"),
  SettingSpec("authMethod","accounts","优先登录方式",SettingKind.CHOICE,"auto",options=listOf("auto" to "按平台推荐","app" to "App 授权","qr" to "扫码","browser" to "浏览器")),
  SettingSpec("clipboardPrompt","downloads","读取剪贴板前询问",SettingKind.TOGGLE,"true"),
  SettingSpec("defaultQuality","downloads","默认画质",SettingKind.CHOICE,"best",options=listOf("best" to "优先最高","1080p" to "1080p","720p" to "720p","audio" to "仅音频")),
  SettingSpec("concurrency","downloads","下载并发数",SettingKind.RANGE,"3",minimum=1.0f,maximum=6.0f,step=1.0f,unit="个"),
  SettingSpec("filename","downloads","文件命名",SettingKind.CHOICE,"title",options=listOf("title" to "视频标题","author-title" to "作者 — 标题","platform-title" to "平台 — 标题")),
  SettingSpec("notifyDone","downloads","完成时通知",SettingKind.TOGGLE,"true"),
  SettingSpec("wifiOnly","downloads","仅通过 Wi-Fi 下载",SettingKind.TOGGLE,"false"),
  SettingSpec("retry","downloads","网络错误重试次数",SettingKind.RANGE,"10",minimum=0f,maximum=10f,step=1f,unit="次"),
  SettingSpec("autoRefresh","privacy","自动维护 B站会话",SettingKind.TOGGLE,"true"),
  SettingSpec("haptics","motion","轻触振动（设备支持时）",SettingKind.TOGGLE,"true"),
 )
 val byKey=all.associateBy { it.key }
 // Unsupported optical/account options are not rendered as interactive controls.
 val nativeReserved=setOf("remember","authMethod")
 val active get()=all.filter { it.key !in nativeReserved }
 fun valid(key:String,value:String):Boolean {
  val d=byKey[key]?:return false
  return when(d.kind) {
   SettingKind.TOGGLE -> value=="true" || value=="false"
   SettingKind.CHOICE -> d.options.any { it.first==value }
   SettingKind.RANGE -> value.toFloatOrNull()?.let { it.isFinite() && it>=d.minimum && it<=d.maximum && kotlin.math.abs((it-d.minimum)/d.step-kotlin.math.round((it-d.minimum)/d.step))<.0001f } ?: false
  }
 }
}
data class UiSettings(val values:Map<String,String> = SettingsCatalog.all.associate { it.key to it.default }) {
 fun text(key:String):String = values[key]?.takeIf{SettingsCatalog.valid(key,it)} ?: SettingsCatalog.byKey.getValue(key).default
 fun number(key:String):Float = text(key).toFloat()
 fun enabled(key:String):Boolean = text(key)=="true"
 fun withValue(key:String,value:String):UiSettings = if(SettingsCatalog.valid(key,value))copy(values=values+(key to value)) else this
 fun resetVisual():UiSettings = copy(values=values+SettingsCatalog.all.filter { it.section in setOf("appearance","glass","buttons","motion") }.associate { it.key to it.default })
}
