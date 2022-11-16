package mcpatch.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 代表一个版本文件所记录的文件差异元信息
 */
class VersionMetadata(jsonObject: JSONObject? = null)
{
    val oldFiles: MutableSet<String> = mutableSetOf()
    val newFiles: MutableSet<NewFile> = mutableSetOf()
    val oldFolders: MutableSet<String> = mutableSetOf()
    val newFolders: MutableSet<String> = mutableSetOf()
    var changeLogs: String = ""
    var patchHash: String = ""
    var patchLength: Long = 0

    init {
        if (jsonObject != null)
        {
            oldFiles.addAll(jsonObject.getJSONArray("old_files").map { it as String })
            newFiles.addAll(jsonObject.getJSONArray("new_files").map { NewFile.FromString(it as String) })
            oldFolders.addAll(jsonObject.getJSONArray("old_folders").map { it as String })
            newFolders.addAll(jsonObject.getJSONArray("new_folders").map { it as String })
            changeLogs = jsonObject.getJSONArray("change_logs").joinToString("\n") { it as String }
            patchHash = jsonObject.getString("patch_hash")
            patchLength = jsonObject.getLong("patch_length")
        }
    }

    fun serializeToJson(): JSONObject
    {
        val json = JSONObject()

        json.put("old_files", JSONArray(oldFiles))
        json.put("new_files", JSONArray(newFiles.map { it.toString() }))
        json.put("old_folders", JSONArray(oldFolders))
        json.put("new_folders", JSONArray(newFolders))
        json.put("change_logs", changeLogs.split("\n"))
        json.put("patch_hash", patchHash)
        json.put("patch_length", patchLength)

        return json
    }
}