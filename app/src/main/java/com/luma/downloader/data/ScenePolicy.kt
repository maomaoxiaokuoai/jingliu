package com.luma.downloader.data

/** Pure layer mathematics; it never queues navigation and never changes application state.
 * Fixed z order + prefix-normalised alpha avoids a full-screen dim/flash at mid-transition. */
object ScenePolicy {
    fun weight(raw:Float):Float = if(raw.isFinite()) raw.coerceIn(0f,1f) else 0f
    fun alpha(index:Int,weights:FloatArray):Float {
        require(index in weights.indices)
        var prefix=0f
        for(i in 0..index)prefix+=weight(weights[i])
        return if(prefix<=0.00001f)0f else (weight(weights[index])/prefix).coerceIn(0f,1f)
    }
    /** Put the logical target on top for hit testing; adjust alpha to keep the same visual mix. */
    fun targetOnTopAlpha(index:Int,target:Int,weights:FloatArray):Float {
        require(index in weights.indices && target in weights.indices)
        var prefix=0f
        if(index==target) {for(raw in weights)prefix+=weight(raw)}
        else {for(i in 0..index)if(i!=target)prefix+=weight(weights[i])}
        return if(prefix<=0.00001f)0f else (weight(weights[index])/prefix).coerceIn(0f,1f)
    }
    fun offset(slot:Int,target:Int,distance:Float):Float = when {
        slot==target -> 0f
        slot<target -> -distance
        else -> distance
    }
    fun draw(raw:Float,selected:Boolean):Boolean = selected || weight(raw)>0.0001f
}
