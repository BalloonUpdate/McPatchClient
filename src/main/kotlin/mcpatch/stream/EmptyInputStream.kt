package mcpatch.stream

import java.io.InputStream

class EmptyInputStream : InputStream()
{
    override fun read(): Int
    {
        return -1
    }

    override fun available(): Int
    {
        return 0
    }
}