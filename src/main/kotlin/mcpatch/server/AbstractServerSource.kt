package mcpatch.server

import mcpatch.exception.ConnectionInterruptedException
import mcpatch.exception.ConnectionRejectedException
import mcpatch.exception.ConnectionTimeoutException
import mcpatch.util.File2

typealias OnDownload = (block: Long, received: Long, total: Long?) -> Unit

interface AbstractServerSource : AutoCloseable
{
    /**
     * 获取文本文件的内容
     * @param relativePath 文本文件的相对路径
     * @return 文本内容
     * @throws ConnectionRejectedException 当连接被拒绝时
     * @throws ConnectionInterruptedException 当连接意外断开时
     * @throws ConnectionTimeoutException 当发生超时时
     */
    fun fetchText(relativePath: String): String

    /**
     * 下载一个二进制文件
     * @param relativePath 文件的相对路径
     * @param writeTo 写到哪里
     * @param lengthExpected 文件的预期长度，用来报告下载进度
     * @param callback 报告下载进度的回调
     * @throws ConnectionRejectedException 当连接被拒绝时
     * @throws ConnectionInterruptedException 当连接意外断开时
     * @throws ConnectionTimeoutException 当发生超时时
     */
    fun downloadFile(relativePath: String, writeTo: File2, lengthExpected: Long?, callback: OnDownload)

    /**
     * 构建一个URI
     */
    fun buildURI(relativePath: String): String
}