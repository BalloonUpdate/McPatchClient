package mcpatch.util

import java.util.LinkedList

/**
 * 网速采样
 * @param samplingPeriod 采样周期，单位毫秒，在采样周期内速度会被取平均值
 */
class SpeedSampler(val samplingPeriod: Int)
{
    private val samplingFrames = LinkedList<Pair<Long, Long>>()

    private var speed: Long = 0

    /**
     * 进行采样
     * @param bytes 字节数
     */
    fun feed(bytes: Long)
    {
        val now = System.currentTimeMillis()

        synchronized(samplingFrames)
        {
            samplingFrames.addLast(Pair(System.currentTimeMillis(), bytes))
            samplingFrames.removeIf { frame -> (now - frame.first) > samplingPeriod }

            val firstTime = samplingFrames.first.first
            val timeSpan = now - firstTime

            if (timeSpan > 0)
                speed = samplingFrames.sumOf { frame -> frame.second } / timeSpan * 1000
        }
    }

    /**
     * 获取当前速度，单位字节
     */
    fun speed(): Long
    {
        return speed
    }
}