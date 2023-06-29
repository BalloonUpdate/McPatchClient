package mcpatch.stream

import java.io.ByteArrayOutputStream

class ExposedByteArrayOutputStream(initialSize: Int = 4 * 1024) : ByteArrayOutputStream()
{
    fun internalBuffer(): ByteArray = buf
}