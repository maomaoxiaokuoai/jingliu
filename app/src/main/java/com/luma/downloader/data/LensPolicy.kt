package com.luma.downloader.data

/** Capability and geometry policy for the app-owned AGSL material. Values are not Apple defaults. */
enum class OpticalTier { SOLID, BLUR, LENS }
data class LensPlan(val tier:OpticalTier,val depth:Float,val bevel:Float,val dispersion:Float,val saturation:Float,val edgeSoftPx:Float,val fusion:Boolean)
object LensPolicy {
    fun plan(s:UiSettings,role:GlassRole,sdk:Int,hardware:Boolean,enabled:Boolean,
             minSide:Float,density:Float,powerSave:Boolean=false):LensPlan {
        if(!enabled || !hardware || sdk<31 || s.text("material")=="solid" || s.enabled("reduceTransparency") ||
            powerSave ||
            !minSide.isFinite() || minSide<=0f || !density.isFinite() || density<=0f)
            return LensPlan(OpticalTier.SOLID,0f,1f,0f,1f,1f,false)
        val saturation=(s.number("saturation")/100f).coerceIn(1f,1.8f)
        val lens=sdk>=33 && s.text("material") in setOf("glass","regular") && s.number("refraction")>0f
        val edgeSoftPx=LiquidFusionMath.edgeSoftPx(s.number("edgeSoft"))
        val fusion=s.enabled("lensFusion")
        if(!lens)return LensPlan(OpticalTier.BLUR,0f,1f,0f,saturation,edgeSoftPx,false)
        val roleScale=when(role){GlassRole.GROUP->.75f;GlassRole.MENU->.55f;GlassRole.THUMB->.65f;else->1f}
        val depth=(s.number("refraction")*density*roleScale).coerceIn(0f,minSide*.24f)
        val bevel=(s.number("lensBevel")*density).coerceIn(1f, minSide.coerceAtLeast(2f)*.5f)
        return LensPlan(OpticalTier.LENS,depth,bevel,(s.number("dispersion")/100f).coerceIn(0f,.12f),saturation,edgeSoftPx,fusion)
    }
}
