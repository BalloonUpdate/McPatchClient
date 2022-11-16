package mcpatch.data

/**
 * 代表一个新文件的元信息
 */
data class NewFile(
    /**
     * 新文件文件路径
     */
    val path: String,

    /**
     * 文件修改的类型
     */
    val mode: ModificationMode,

    /**
     * 旧文件hash（仅当mode为Modify时有值）
     */
    val oldFileHash: String,

    /**
     * 新文件hash
     */
    val newFileHash: String,

    /**
     * patch文件hash（仅当mode为Modify时有值）
     */
    val patchFileHash: String,

    /**
     * patch二进制块的hash
     */
    val blockHash: String,

    /**
     * patch中的偏移
     */
    val blockOffset: Long,

    /**
     * patch中的长度
     */
    val blockLength: Long,

    /**
     * 新文件原始大小
     */
    val rawLength: Long,
) {
    override fun toString() =
        "$path|$mode|$oldFileHash|$newFileHash|$patchFileHash|$blockHash|${blockOffset.toString(16)}|${blockLength.toString(16)}|${rawLength.toString(16)}"

    companion object {
        fun FromString(newFile: String): NewFile
        {
            val split = newFile.split("|")

            if (split.size != 9)
                throw RuntimeException("The new file meta '$newFile' is invalid")

            return NewFile(
                path = split[0],
                mode = ModificationMode.FromString(split[1]),
                oldFileHash = split[2],
                newFileHash = split[3],
                patchFileHash = split[4],
                blockHash = split[5],
                blockOffset = split[6].toLong(16),
                blockLength = split[7].toLong(16),
                rawLength = split[8].toLong(16),
            )
        }
    }
}