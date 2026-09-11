package com.luma.downloader.data

/** FrameMetrics-based observations, not FPS predictions. No URLs, account data or input text. */
class FrameStats(private val budgetNanos:Long) {
    init {require(budgetNanos>0)}
    private val samples=LongArray(2048)
    private var cursor=0
    var frames=0L;private set
    var overBudget=0L;private set
    var droppedCallbacks=0L;private set
    fun add(totalNanos:Long,dropped:Int=0,frameBudgetNanos:Long=budgetNanos) {
        droppedCallbacks+=dropped.coerceAtLeast(0)
        if(totalNanos<0)return
        samples[cursor]=totalNanos;cursor=(cursor+1)%samples.size
        frames++;if(totalNanos>frameBudgetNanos.coerceAtLeast(1))overBudget++
    }
    fun summary():String {
        val n=minOf(frames,samples.size.toLong()).toInt()
        if(n==0)return "frames=0"
        val sorted=samples.copyOf(n).sortedArray()
        fun ms(percent:Double)=sorted[((n-1)*percent).toInt()]/1_000_000.0
        return "frames=$frames overBudget=$overBudget callbackDrops=$droppedCallbacks budgetMs=${budgetNanos/1_000_000.0} recentN=$n p50Ms=${ms(.5)} p95Ms=${ms(.95)}"
    }
}
