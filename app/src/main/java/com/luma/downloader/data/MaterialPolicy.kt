package com.luma.downloader.data

/** Publicly documented approximations. No Apple system material or shader is claimed. */
data class MaterialPlan(val opaque:Boolean,val blur:Float,val tint:Float,val rim:Float,val shadow:Float,val noise:Float)
object MaterialPolicy {
 fun plan(s:UiSettings,supported:Boolean=true,roleEnabled:Boolean=true):MaterialPlan {
  val opaque=!supported||!roleEnabled||s.enabled("reduceTransparency")||s.text("material")=="solid"
  val efficient=s.text("performance")=="efficient"
  val tint=if(opaque)1f else when(s.text("material")) {
   "frost"->.56f+.38f*(1f-s.number("transparency")/100f)
   "regular"->.28f+.52f*(1f-s.number("transparency")/100f)
   else->1f-s.number("transparency")/100f
  }
  return MaterialPlan(opaque,if(opaque)0f else if(efficient)s.number("blur").coerceAtMost(12f)else s.number("blur"),tint,
   if(s.enabled("increaseContrast"))(s.number("highlight")/100f).coerceAtLeast(.7f)else s.number("highlight")/100f,
   if(opaque)0f else s.number("shadow")*(if(efficient).15f else .3f),
   if(!opaque&&s.enabled("grain")) s.number("grainStrength") / 100f else 0f)
 }
}
