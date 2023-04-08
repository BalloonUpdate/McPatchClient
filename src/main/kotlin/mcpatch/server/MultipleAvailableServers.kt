package mcpatch.server

import mcpatch.data.GlobalOptions
import mcpatch.exception.*
import mcpatch.logging.Log
import mcpatch.server.impl.HttpSupport
import mcpatch.server.impl.SFTPSupport
import mcpatch.server.impl.WebdavSupport
import mcpatch.util.File2
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 代表一个多服务器地址支持的数据源
 */
class MultipleAvailableServers(options: GlobalOptions) : AutoCloseable
{
    /**
     * 所有可用服务器
     */
    private val servers = options.server.map { serverString ->
        if (serverString.startsWith("http"))
            HttpSupport(serverString, options)
        else if (serverString.startsWith("sftp"))
            SFTPSupport(serverString, options)
        else if (serverString.startsWith("webdav"))
            WebdavSupport(serverString, options)
        else
            throw UnknownServerStringFormatException(serverString)
    }.also { if (it.isEmpty()) throw NoServerException() }

    /**
     * 获取一个JsonObject
     * @param relativePath 文件的相对路径
     * @param name 文件的描述
     */
    fun fetchJsonObject(relativePath: String, name: String): JSONObject
    {
        val (body, source) = fetchTextInternal(relativePath)

        try {
            return JSONObject(body)
        } catch (e: JSONException) {
            val uri = source.buildURI(relativePath)
            throw FailedToParsingException(name, "json", "$uri ${e.message}")
        }
    }

    /**
     * JsonArray
     * @param relativePath 文件的相对路径
     * @param name 文件的描述
     */
    fun fetchJsonArray(relativePath: String, name: String): JSONArray
    {
        val (body, source) = fetchTextInternal(relativePath)

        try {
            return JSONArray(body)
        } catch (e: JSONException) {
            val uri = source.buildURI(relativePath)
            throw FailedToParsingException(name, "json", "$uri ${e.message}")
        }
    }

    /**
     * 获取文本文件的内容
     * @param relativePath 文本文件的相对路径
     * @return 文本内容
     * @throws ConnectionRejectedException 当连接被拒绝时
     * @throws ConnectionInterruptedException 当连接意外断开时
     * @throws ConnectionTimeoutException 当发生超时时
     */
    fun fetchText(relativePath: String): String
    {
        return fetchTextInternal(relativePath).first
    }

    /**
     * 下载一个二进制文件
     * @param relativePath 文件的相对路径
     * @param writeTo 写到哪里
     * @param callback 报告下载进度的回调
     * @throws ConnectionRejectedException 当连接被拒绝时
     * @throws ConnectionInterruptedException 当连接意外断开时
     * @throws ConnectionTimeoutException 当发生超时时
     */
    fun downloadFile(relativePath: String, writeTo: File2, callback: OnDownload)
    {
        downloadFileInternal(relativePath, writeTo, callback)
    }

    private fun fetchTextInternal(relativePath: String): Pair<String, AbstractServerSource>
    {
        var ex: Throwable? = null

        for (source in servers)
        {
            ex = try {
                return Pair(source.fetchText(relativePath), source)
            } catch (e: Throwable) { e }

            if (servers.size > 1)
                Log.error(ex!!.toString())
        }

        throw ex!!
    }

    private fun downloadFileInternal(
        relativePath: String,
        writeTo: File2,
        callback: OnDownload
    ): AbstractServerSource {
        var ex: Throwable? = null

        for (source in servers)
        {
            ex = try {
                source.downloadFile(relativePath, writeTo, callback)
                return source
            } catch (e: Throwable) { e }

            if (servers.size > 1)
                Log.error(ex!!.toString())
        }

        throw ex!!
    }

    override fun close()
    {
        for (source in servers)
            source.close()
    }
}