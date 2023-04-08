package mcpatch.stream

import java.io.InputStream

class ActionedInputStream(
    val input: InputStream,
    val actionAmount: Int,
    val action: (ActionedInputStream) -> Unit
) : InputStream() {
    private var bytesRead = 0

    override fun read(): Int
    {
        val value = input.read()

        if (value != -1)
            bytesRead += 1

        if (bytesRead == actionAmount)
            action(this)

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