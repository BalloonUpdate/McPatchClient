package mcpatch.data

import org.json.JSONObject

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
    val oldHash: String,

    /**
     * 新文件hash（更新后的文件hash）
     */
    val newHash: String,

    /**
     * 数据bzipped后的hash
     */
    val bzippedHash: String,

    /**
     * 数据完全解压后的hash
     */
    val rawHash: String,

    /**
     * 数据完全解压后的长度
     */
    val rawLength: Long,
) {
    fun toJsonObject(): JSONObject
    {
        val jo = JSONObject()

        jo.put("path", path)
        jo.put("mode", mode.flag)
        jo.put("old-hash", oldHash)
        jo.put("new-hash", newHash)
        jo.put("bzipped-hash", bzippedHash)
        jo.put("raw-hash", rawHash)
        jo.put("raw-length", rawLength)

        return jo
    }

    companion object {
        fun FromJsonObject(jo: JSONObject): NewFile
        {
            return NewFile(
                path = jo.getString("path"),
                mode = ModificationMode.FromString(jo.getString("mode")),
                oldHash = jo.getString("old-hash"),
                newHash = jo.getString("new-hash"),
                bzippedHash = jo.getString("bzipped-hash"),
                rawHash = jo.getString("raw-hash"),
                rawLength = jo.getLong("raw-length"),
            )
        }
    }
}