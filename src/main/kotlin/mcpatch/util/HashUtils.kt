package mcpatch.util

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.CRC32

object HashUtils
{
    /**
     * 计算文件的的crc32
     * @param file 要计算的文件
     * @return 文件的crc32十六进制小写字符串
     */
    fun crc32(file: File): String
    {
        val crc32 = CRC32()

        FileChannel.open(Paths.get(file.toURI()), StandardOpenOption.READ).use { channel ->
            val buffer = ByteBuffer.allocate(chooseBufferSize(file.length()))
            var len: Int
            while (channel.read(buffer).also { len = it } > 0)
            {
                crc32.update(buffer.array(), 0, len)
                buffer.clear()
            }
            channel.close()
        }

        val value = crc32.value
        val array = ByteArray(4)
        array[3] = (value shr (8 * 0) and 0xFF).toByte()
        array[2] = (value shr (8 * 1) and 0xFF).toByte()
        array[1] = (value shr (8 * 2) and 0xFF).toByte()
        array[0] = (value shr (8 * 3) and 0xFF).toByte()

        return bin2str(array)
    }

    /**
     * 计算文件的的sha1
     * @param file 要计算的文件
     * @return 文件的sha1十六进制小写字符串
     */
    fun sha1(file: File, onProgress: ((current: Long, total: Long) -> Unit)? = null): String
    {
        val sha1 = MessageDigest.getInstance("sha1")

        FileChannel.open(Paths.get(file.toURI()), StandardOpenOption.READ).use { channel ->
            val fileLen = file.length()
            val buffer = ByteBuffer.allocate(chooseBufferSize(fileLen))
            var len: Int
            var totalBytes = 0L
            while (channel.read(buffer).also { len = it; totalBytes += it } > 0)
            {
                sha1.update(buffer.array(), 0, len)
                buffer.clear()
                onProgress?.invoke(totalBytes, fileLen)
            }
            channel.close()
        }

        return bin2str(sha1.digest())
    }

    /**
     * 计算文件的的md5
     * @param file 要计算的文件
     * @return 文件的md5十六进制小写字符串
     */
    fun md5(file: File): String
    {
        val sha1 = MessageDigest.getInstance("md5")

        FileChannel.open(Paths.get(file.toURI()), StandardOpenOption.READ).use { channel ->
            val buffer = ByteBuffer.allocate(chooseBufferSize(file.length()))
            var len: Int
            while (channel.read(buffer).also { len = it } > 0)
            {
                sha1.update(buffer.array(), 0, len)
                buffer.clear()
            }
            channel.close()
        }

        return bin2str(sha1.digest())
    }

    /**
     * 计算data的的sha1
     * @param data 要计算的数据
     * @return 文件的sha1十六进制小写字符串
     */
    fun sha1(data: ByteArray): String
    {
        val sha1 = MessageDigest.getInstance("sha1")
        sha1.update(data)
        return bin2str(sha1.digest())
    }

    /**
     * 根据文件大小选择合适的缓冲区大小
     * @param size 文件大小
     * @return 缓冲区大小
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun chooseBufferSize(size: Long): Int {
        val kb = 1024
        val mb = 1024 * 1024
        val gb = 1024 * 1024 * 1024
        return when {
            size < 1 * mb   -> 16 * kb
            size < 2 * mb   -> 32 * kb
            size < 4 * mb   -> 64 * kb
            size < 8 * mb   -> 256 * kb
            size < 16 * mb  -> 512 * kb
            size < 32 * mb  -> 1 * mb
            size < 64 * mb  -> 2 * mb
            size < 128 * mb -> 4 * mb
            size < 256 * mb -> 8 * mb
            size < 512 * mb -> 16 * mb
            size < 1 * gb   -> 32 * mb
            else -> 64 * mb
        }
    }

    /**
     * 将字节数组转换为十六进制小写字符串
     * @param binary 要转换的数组
     * @param separator 分隔符号
     * @return 转换后的字符串
     */
    fun bin2str(binary: ByteArray, separator: String = ""): String
    {
        val buffer = StringBuffer()

        for (byte in binary)
        {
            val high = (byte.toInt() shr 4) and 0x0F
            val low = byte.toInt() and 0x0F

            val most = if (high > 9) (high - 10 + 'a'.code.toByte()).toChar() else (high + '0'.code.toByte()).toChar()
            val least = if (low > 9) (low - 10 + 'a'.code.toByte()).toChar() else (low + '0'.code.toByte()).toChar()

            buffer.append(most)
            buffer.append(least)
            buffer.append(separator)
        }

        return buffer.toString()
    }
}