package mcpatch.util

import java.util.*

/**
 * 网速采样
 * @param samplingPeriod 采样周期，单位毫秒，在采样周期内速度会被取平均值
 */
class SpeedSampler(val samplingPeriod: Int)
{
    private var startTimestamp = 0L
    private val samplingFrames = LinkedList<Long>() // Long分两部分用，低32位存字节数，高32位存时间戳

    /**
     * 获取当前速度，单位字节
     */
    var speed: Long = 0

    /**
     * 进行采样
     * @param bytes 字节数
     */
    fun feed(bytes: Int)
    {
        synchronized(samplingFrames) {
            val now = getNow()

            val first = samplingFrames.firstOrNull()
            if (first != null && first.high32 == now)
            {
                samplingFrames[0] = first + bytes
                return
            }

            samplingFrames.addLast((now.toLong() shl 32) + bytes)

            var lastInvalidIndex = -1
            var firstValidIndex = -1
            var lastInvalid = -1L
            var firstValid = -1L

            for ((index, frame) in samplingFrames.withIndex())
            {
                if (lastInvalidIndex == -1)
                {
                    if ((now - frame.high32) > samplingPeriod)
                    {
                        lastInvalidIndex = index
                        lastInvalid = frame
                    }
                } else {
                    if ((now - frame.high32) < samplingPeriod)
                    {
                        firstValidIndex = index
                        firstValid = frame
                        break
                    }
                }
            }

            if (lastInvalidIndex != -1 && firstValidIndex != -1)
            {
                var min = (now - firstValid) - samplingPeriod
                var max = (now - lastInvalid) - samplingPeriod
                var threshold = samplingPeriod.toLong()
                min += -min
                max += -min
                threshold += -min

                val insideValid = threshold.toFloat() / max

                for (i in 0 until (lastInvalidIndex + 1))
                    samplingFrames.removeFirst()

                val h32 = now - samplingPeriod
                val l32 = (firstValid.low32 * insideValid).toInt()
                samplingFrames.addFirst(((h32 shl 32) + l32).toLong())

            } else {
                for (i in 0 until (lastInvalidIndex))
                    samplingFrames.removeFirst()
            }

            val timeSpan = now - samplingFrames.first.high32

            if (timeSpan > 0)
                speed = (samplingFrames.sumOf { it.low32 } / timeSpan * 1000).toLong()
        }
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