package mcpatch.extension

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream

object StreamExtension
{
    fun BufferedInputStream.actuallySkip(n: Long)
    {
        var target = n
        while (target > 0)
            target -= skip(target)
    }

    fun InputStream.copyAmountTo(
        out: OutputStream,
        buffer: Int,
        amount: Long,
        callback: ((copied: Long, total: Long) -> Unit)? = null
    ): Long {
        var bytesCopied: Long = 0
        val buf = ByteArray(buffer)

        val times = amount / buffer
        val remain = amount % buffer

        for (i in 0 until times)
        {
            val bytes = read(buf)
            out.write(buf, 0, bytes)
            bytesCopied += bytes
            callback?.invoke(bytesCopied, amount)
        }

        if (remain > 0)
        {
            val bytes = read(buf, 0, remain.toInt())
            out.write(buf, 0, bytes)
            bytesCopied += bytes
            callback?.invoke(bytesCopied, amount)
        }

        return bytesCopied
    }
}