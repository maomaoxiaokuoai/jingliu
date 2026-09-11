package com.luma.downloader

import com.luma.downloader.data.*

/** Runs on JVM only. It does not compile AGSL, render a GPU frame, or load Android. */
object LensPolicyChecks {
    @JvmStatic fun main(args:Array<String>) {
        var count=0
        fun test(name:String,block:()->Unit){block();count++;println("PASS $name")}
        val base=UiSettings()
        fun plan(s:UiSettings=base,sdk:Int=33,hw:Boolean=true,enabled:Boolean=true,side:Float=140f,density:Float=3f,role:GlassRole=GlassRole.BUTTON)=LensPolicy.plan(s,role,sdk,hw,enabled,side,density)
        test("Android 13 supported glass selects lens"){check(plan().tier==OpticalTier.LENS)}
        test("Android 12 uses blur"){check(plan(sdk=31).tier==OpticalTier.BLUR);check(plan(sdk=32).tier==OpticalTier.BLUR)}
        test("Old API does not select RenderEffect"){check(plan(sdk=30).tier==OpticalTier.SOLID)}
        test("Software canvas falls back"){check(plan(hw=false).tier==OpticalTier.SOLID)}
        test("Disabled role selects solid"){check(plan(enabled=false).tier==OpticalTier.SOLID)}
        test("Accessibility transparency overrides optics"){check(plan(base.withValue("reduceTransparency","true")).tier==OpticalTier.SOLID)}
        test("Explicit solid removes optics"){check(plan(base.withValue("material","solid")).tier==OpticalTier.SOLID)}
        test("Frost has saturation and blur but no refraction"){check(plan(base.withValue("material","frost")).tier==OpticalTier.BLUR)}
        test("Zero refraction really disables lens"){val p=plan(base.withValue("refraction","0"));check(p.tier==OpticalTier.BLUR&&p.depth==0f)}
        test("Refraction parameter affects depth"){check(plan(base.withValue("refraction","10"),side=500f).depth<plan(base.withValue("refraction","20"),side=500f).depth)}
        test("Bevel parameter affects width"){check(plan(base.withValue("lensBevel","8"),side=500f).bevel<plan(base.withValue("lensBevel","30"),side=500f).bevel)}
        test("Dispersion parameter affects fringe"){check(plan(base.withValue("dispersion","0")).dispersion==0f);check(plan(base.withValue("dispersion","12")).dispersion==.12f)}
        test("Saturation follows the setting"){check(plan(base.withValue("saturation","180")).saturation==1.8f)}
        test("Small thumbs do not exceed geometric budget"){val p=plan(role=GlassRole.THUMB,side=20f);check(p.depth<=4.8f&&p.bevel<=10f)}
        test("Different roles have intentional optical amplitudes"){check(plan(side=1000f,role=GlassRole.MENU).depth<plan(side=1000f,role=GlassRole.BUTTON).depth)}
        test("Nonfinite and absent geometry falls back"){for(v in listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY))check(plan(side=v).tier==OpticalTier.SOLID)}
        test("Nonfinite density falls back"){for(v in listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY))check(plan(density=v).tier==OpticalTier.SOLID)}
        test("Random supported geometry remains finite and bounded") {
            val random=java.util.Random(81)
            repeat(500){val side=random.nextFloat()*1800f+.1f;val p=plan(side=side,density=random.nextFloat()*5f+.1f)
                check(p.depth.isFinite()&&p.depth>=0f&&p.depth<=side*.24f+.0001f);check(p.bevel>=1f&&p.bevel.isFinite());check(p.dispersion in 0f..0.12f)}
        }
        test("All surface toggles remove optical finish"){for(role in GlassRole.entries){val p=GlassPolicy.plan(base.withValue(role.setting,"false"),role);check(!p.enabled&&p.blur==0f&&p.noise==0f&&p.rim==0f)}}
        test("Optical controls are active catalog entries"){for(key in listOf("refraction","lensBevel","dispersion","saturation","pointerLight"))check(SettingsCatalog.active.any{it.key==key})}
        test("API and frost availability explains unavailable lens"){check(GlassPolicy.explanation(base,"dispersion",true,31)!=null);check(GlassPolicy.explanation(base.withValue("material","frost"),"refraction",true,33)!=null)}
        test("Visual reset does not change account or download preferences"){val s=base.withValue("wifiOnly","true").withValue("retry","7").withValue("parserEngine","parse_video_py").withValue("refraction","2");val n=VisualDefaults.reset(s,VisualResetScope.GLASS);check(n.enabled("wifiOnly")&&n.number("retry")==7f&&n.text("parserEngine")=="parse_video_py"&&n.number("refraction")==12f)}
        test("Old custom choices survive migration"){val s=VisualDefaults.migrate(mapOf("_visualSchema" to "3","glassNav" to "false","refraction" to "6"));check(!s.enabled("glassNav")&&s.number("refraction")==6f)}
        println("LensPolicyChecks: $count JVM checks passed. NOT an Android or shader test.")
    }
}
