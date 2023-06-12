package mcpatch.server

import mcpatch.data.GlobalOptions
import mcpatch.exception.*
import mcpatch.logging.Log
import mcpatch.server.impl.HttpSupport
import mcpatch.server.impl.SFTPSupport
import mcpatch.server.impl.WebdavSupport
import mcpatch.util.File2

/**
 * 代表一个多服务器地址支持的数据源
 */
class MultipleServers(options: GlobalOptions) : AutoCloseable
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
     * 当前正在使用的源的索引编号
      */
    private var currentServerIndex = 0

    /**
     * 下载文本文件
     * @param relativePath 文本文件的相对路径
     * @return 文本内容
     * @throws ConnectionRejectedException 当连接被拒绝时
     * @throws ConnectionInterruptedException 当连接意外断开时
     * @throws ConnectionTimeoutException 当发生超时时
     */
    fun fetchText(relativePath: String): String
        = fallback { it.fetchText(relativePath) }

    /**
     * 下载二进制文件
     * @param relativePath 文件的相对路径
     * @param writeTo 写到哪里
     * @param callback 报告下载进度的回调
     * @throws ConnectionRejectedException 当连接被拒绝时
     * @throws ConnectionInterruptedException 当连接意外断开时
     * @throws ConnectionTimeoutException 当发生超时时
     */
    fun downloadFile(relativePath: String, writeTo: File2, callback: OnDownload)
        = fallback { it.downloadFile(relativePath, writeTo, callback) }

    /**
     * 自动切换服务器源，如果遇到网络失败则直接切换到下一个源
     */
    private inline fun <T> fallback(fn: (server: AbstractServerSource) -> T): T
    {
        var ex: Throwable? = null

        for (i in 0 until (servers.size - currentServerIndex))
        {
            val server = servers[currentServerIndex]

            ex = try {
                return fn(server)
            } catch (e: Throwable) { e }

            if (currentServerIndex + 1 < servers.size)
                Log.error(ex!!.toString())

            currentServerIndex += 1
        }

        throw ex!!
    }

    override fun close()
    {
        for (source in servers)
            source.close()
    }
}