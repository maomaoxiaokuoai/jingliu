package com.luma.downloader.data

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.round

/** Mass is 1. These are LUMA-authored spring presets, NOT Apple's private parameters. */
enum class MotionChannel { PAGE, TAB, CONTROL, LIFT, BUTTON, PRESS, MENU, FADE, INDICATOR }
data class SpringTuning(val responseSeconds:Float,val dampingRatio:Float,val instant:Boolean) {
    val stiffness:Float get()=(2.0*PI/responseSeconds).pow(2).toFloat()
}
object MotionPolicy {
    val defaults=mapOf("motion" to "standard","duration" to "420","bounce" to "64","press" to "72",
        "controlLift" to "116","tabTransition" to "slide","tabDistance" to "44",
        "swipeBack" to "true","tabMorph" to "true","menuMorph" to "true")
    fun preset(settings:UiSettings,mode:String="standard"):UiSettings =
        if(mode !in listOf("standard","soft","reduce"))settings
        else settings.copy(values=settings.values+defaults+("motion" to mode))
    fun tuning(settings:UiSettings,channel:MotionChannel,reduced:Boolean=false):SpringTuning {
        val b=settings.number("bounce")/100f
        val (rate,damping)=when(channel) {
            MotionChannel.PAGE -> 1f to (.99f-.11f*b)
            MotionChannel.TAB -> .84f to (.98f-.18f*b)
            MotionChannel.CONTROL -> .74f to (.88f-.30f*b)
            MotionChannel.LIFT -> .60f to (.88f-.34f*b)
            MotionChannel.BUTTON -> .64f to (.9f-.30f*b)
            MotionChannel.PRESS -> .28f to 1f
            MotionChannel.MENU -> .83f to (.96f-.22f*b)
            MotionChannel.FADE -> .60f to 1f
            MotionChannel.INDICATOR -> .72f to (.92f-.24f*b)
        }
        return SpringTuning(settings.number("duration")/1000f*rate,
            if(settings.text("motion")=="soft")1f else damping,
            reduced||settings.enabled("reduceMotion")||settings.text("motion")=="reduce")
    }
    fun snapRange(spec:SettingSpec,value:Float):Float {
        if(!value.isFinite())return spec.default.toFloat()
        return (spec.minimum+round((value.coerceIn(spec.minimum,spec.maximum)-spec.minimum)/spec.step)*spec.step)
            .coerceIn(spec.minimum,spec.maximum)
    }
    fun backCommits(distance:Float,width:Float,velocity:Float,cancelled:Boolean=false):Boolean =
        !cancelled && width>0 && (distance>width*.35f || distance>45f && velocity>500f)
}
