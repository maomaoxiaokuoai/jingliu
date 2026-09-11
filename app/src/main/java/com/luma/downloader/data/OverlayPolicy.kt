package com.luma.downloader.data

/** Versioned overlay lifecycle. Animation callbacks must present the generation they started with. */
class OverlayLifecycle {
    enum class Phase { CLOSED, OPENING, OPEN, CLOSING }
    var generation:Long=0; private set
    var phase:Phase=Phase.CLOSED; private set
    val acceptsInput get()=phase==Phase.OPENING || phase==Phase.OPEN
    fun open():Long { generation++;phase=Phase.OPENING;return generation }
    fun opened(ticket:Long):Boolean {if(ticket!=generation||phase!=Phase.OPENING)return false;phase=Phase.OPEN;return true}
    fun close():Long? {if(!acceptsInput)return null;generation++;phase=Phase.CLOSING;return generation}
    fun closed(ticket:Long):Boolean {if(ticket!=generation||phase!=Phase.CLOSING)return false;phase=Phase.CLOSED;return true}
    fun dispose(){generation++;phase=Phase.CLOSED}
}
