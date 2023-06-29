package mcpatch.server

import mcpatch.exception.ConnectionInterruptedException
import mcpatch.exception.ConnectionRejectedException
import mcpatch.exception.ConnectionTimeoutException
import mcpatch.logging.Log
import mcpatch.util.File2

typealias OnDownload = (packageLength: Long, bytesReceived: Long, lengthExpected: Long) -> Unit

abstract class AbstractServerSource : AutoCloseable
{
    /**
     * 获取文本文件的内容
     * @param relativePath 文本文件的相对路径
     * @return 文本内容
     * @throws ConnectionRejectedException 当连接被拒绝时
     * @throws ConnectionInterruptedException 当连接意外断开时
     * @throws ConnectionTimeoutException 当发生超时时
     */
    abstract fun fetchText(relativePath: String): String

    /**
     * 下载一个二进制文件
     * @param relativePath 文件的相对路径
     * @param writeTo 写到哪里
     * @param callback 报告下载进度的回调
     * @throws ConnectionRejectedException 当连接被拒绝时
     * @throws ConnectionInterruptedException 当连接意外断开时
     * @throws ConnectionTimeoutException 当发生超时时
     */
    abstract fun downloadFile(relativePath: String, writeTo: File2, callback: OnDownload)

    /**
     * 构建一个URI
     */
    abstract fun buildURI(relativePath: String): String

    /**
     * 自动重试机制
     * @param retryTimes 重试次数
     * @param delay 重试间隔
     * @param func 报告函数
     */
    protected fun <TResult> withRetrying(retryTimes: Int, delay: Int, func: () -> TResult): TResult
    {
        var ex: Throwable? = null
        var retries = retryTimes

        while (--retries >= 0)
        {
            try {
                return func()
            } catch (e: Throwable) {
                ex = e
            }

            Log.warn("")
            Log.warn(ex.toString())
            Log.warn("retrying $retries ...")

            Thread.sleep(delay.toLong())
        }

        throw ex!!
    }

    protected class ReduceReportingFrequency
    {
        var lastReport = System.currentTimeMillis()
        var accumulated = 0L

        fun feed(bytes: Int): Long
        {
            accumulated += bytes

            val now = System.currentTimeMillis()
            if (now - lastReport > 200)
            {
                lastReport = now
                val value = accumulated
                accumulated = 0
                return value
            }

            return 0
        }
    }
}