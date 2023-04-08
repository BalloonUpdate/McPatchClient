package mcpatch.data

import org.json.JSONObject

/**
 * 代表一个文件移动操作
 */
data class MoveFile(val from: String, val to: String)
{
    fun toJsonObject(): JSONObject
    {
        val jo = JSONObject()

        jo.put("from", from)
        jo.put("to", to)

        return jo
    }

    companion object {
        fun FromJsonObject(jo: JSONObject): MoveFile
        {
            return MoveFile(
                from = jo.getString("from"),
                to = jo.getString("to"),
            )
        }
    }
}