package mcpatch.util

object MiscUtils
{
    const val kb = 1024
    const val mb = 1024 * 1024
    const val gb = 1024 * 1024 * 1024

    /**
     * 拆分较长的字符串到多行里
     */
    @JvmStatic
    fun stringBreak(str: String, lineLength: Int, newline: String="\n"): String
    {
        val lines = mutableListOf<String>()

        val lineCount = str.length / lineLength
        val remains = str.length % lineLength

        for (i in 0 until lineCount)
            lines += str.substring(lineLength * i, lineLength * (i + 1))

        if (remains > 0)
            lines += str.substring(lineLength * lineCount)

        return lines.joinToString(newline)
    }

    /**
     * 字节转换为kb, mb, gb等单位
     */
    @JvmOverloads
    fun convertBytes(bytes: Long, b: String = "B", kb: String = "KB", mb: String = "MB", gb: String = "GB"): String
    {
        return when {
            bytes < 1024 -> "$bytes $b"
            bytes < 1024 * 1024 -> "${String.format("%.2f", (bytes / 1024f))} $kb"
            bytes < 1024 * 1024 * 1024 -> "${String.format("%.2f", (bytes / 1024 / 1024f))} $mb"
            else -> "${String.format("%.2f", (bytes / 1024 / 1024 / 1024f))} $gb"
        }
    }


    /**
     * 根据文件大小选择合适的缓冲区大小
     * @param size 文件大小
     * @return 缓冲区大小
     */
    fun chooseBufferSize(size: Long): Int {
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
     * 根据文件大小选择合适的下载进度报告大小
     * @param size 文件大小
     * @return 报告大小
     */
    fun chooseReportSize(size: Long): Int {
        return when {
            size < 32 * kb  -> 8 * kb
            size < 128 * kb -> 16 * kb
            size < 256 * kb -> 32 * kb
            size < 512 * kb -> 64 * kb
            size < 4 * mb   -> 128 * kb
            size < 16 * mb   -> 256 * kb
            size < 64 * mb   -> 512 * kb
            else -> 1 * mb
        }
    }

}