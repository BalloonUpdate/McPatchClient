package mcpatch.stream

import mcpatch.util.HashUtils
import java.io.InputStream
import java.security.MessageDigest

class SHA1CheckInputStream(val input: InputStream) : InputStream()
{
    val sha1: MessageDigest = MessageDigest.getInstance("sha1")

    fun digest(): String
    {
        return HashUtils.bin2str(sha1.digest())
    }

    override fun read(): Int
    {
        val value = input.read()

        if (value != -1)
            sha1.update(value.toByte())

        return value
    }

    override fun available(): Int
    {
        return input.available()
    }

    override fun mark(readlimit: Int)
    {
        input.mark(readlimit)
    }

    override fun markSupported(): Boolean
    {
        return input.markSupported()
    }

    override fun reset()
    {
        input.reset()
    }

    override fun close()
    {
        input.close()
    }
}