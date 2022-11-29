package mcpatch.util

import java.util.LinkedList

/**
 * 网速采样
 * @param samplingPeriod 采样周期，单位毫秒，在采样周期内速度会被取平均值
 */
class SpeedSampler(val samplingPeriod: Int)
{
    private var startTimestamp = 0L
    private val samplingFrames = LinkedList<Long>()

    /**
     * 获取当前速度，单位字节
     */
    var speed: Long = 0

    /**
     * 进行采样
     * @param bytes 字节数
     */
    fun feed(bytes: Long)
    {
        val now = getNow()

        samplingFrames.addLast((now.toLong() shl 32) + bytes)
        samplingFrames.removeIf { (now - it.high32) > samplingPeriod }

        val firstTime = samplingFrames.first.high32
        val timeSpan = now - firstTime

        if (timeSpan > 0)
            speed = (samplingFrames.sumOf { it.low32 } / timeSpan * 1000).toLong()
    }

    private fun getNow(): Int
    {
        val ts = System.currentTimeMillis()

        if (startTimestamp == 0L)
            startTimestamp = ts

        return (ts - startTimestamp).toInt()
    }

    private inline val Long.high32: Int get() = ((this shr 32) and 0xffffffff).toInt()

    private inline val Long.low32: Int get() = (this and 0xffffffff).toInt()
}