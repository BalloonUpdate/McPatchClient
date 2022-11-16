package mcpatch.extension

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileExtension
{
    @Suppress("NOTHING_TO_INLINE")
    inline fun File.bufferedInputStream(bufferSize: Int = 128 * 1024): BufferedInputStream
    {
        return FileInputStream(this).buffered(bufferSize)
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun File.bufferedOutputStream(bufferSize: Int = 128 * 1024): BufferedOutputStream
    {
        return FileOutputStream(this).buffered(bufferSize)
    }
}