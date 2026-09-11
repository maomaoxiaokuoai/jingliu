package com.luma.downloader.data

/** Bounded deterministic grayscale-alpha tile; cached by the renderer and never time dependent. */
object GrainPixels {
    fun create(side:Int):IntArray {
        require(side in 8..256)
        var seed=0x6d2b79f5
        return IntArray(side*side) {
            seed=seed xor (seed shl 13);seed=seed xor (seed ushr 17);seed=seed xor (seed shl 5)
            val alpha=72+((seed ushr 1) and 127)
            (alpha shl 24) or (if((seed and 1)==0)0x00ffffff else 0)
        }
    }
}
