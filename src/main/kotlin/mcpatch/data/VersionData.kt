package mcpatch.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 代表一个版本文件所记录的文件差异元信息
 */
class VersionData(jsonObject: JSONObject? = null)
{
    val oldFiles: MutableSet<String> = mutableSetOf()
    val newFiles: MutableSet<NewFile> = mutableSetOf()
    val oldFolders: MutableSet<String> = mutableSetOf()
    val newFolders: MutableSet<String> = mutableSetOf()
    val moveFiles: MutableSet<MoveFile> = mutableSetOf()
    var changeLogs: String = ""

    init {
        if (jsonObject != null)
        {
            oldFiles.addAll(jsonObject.getJSONArray("old-files").map { it as String })
            newFiles.addAll(jsonObject.getJSONArray("new-files").map { NewFile.FromJsonObject(it as JSONObject) })
            oldFolders.addAll(jsonObject.getJSONArray("old-folders").map { it as String })
            newFolders.addAll(jsonObject.getJSONArray("new-folders").map { it as String })
            moveFiles.addAll(jsonObject.getJSONArray("move-files").map { MoveFile.FromJsonObject(it as JSONObject) })
            changeLogs = jsonObject.getJSONArray("change-logs").joinToString("\n") { it as String }
        }
    }

    fun serializeToJson(): JSONObject
    {
        val json = JSONObject()

        json.put("change-logs", changeLogs.split("\n"))
        json.put("old-files", JSONArray(oldFiles))
        json.put("new-files", JSONArray(newFiles.map { it.toJsonObject() }))
        json.put("old-folders", JSONArray(oldFolders))
        json.put("new-folders", JSONArray(newFolders))
        json.put("move-files", JSONArray(moveFiles.map { it.toJsonObject() }))

        return json
    }
}