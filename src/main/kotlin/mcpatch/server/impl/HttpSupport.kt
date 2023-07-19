package mcpatch.server.impl

import mcpatch.data.GlobalOptions
import mcpatch.exception.ConnectionInterruptedException
import mcpatch.exception.ConnectionRejectedException
import mcpatch.exception.ConnectionTimeoutException
import mcpatch.exception.HttpResponseStatusCodeException
import mcpatch.extension.FileExtension.bufferedOutputStream
import mcpatch.logging.Log
import mcpatch.server.AbstractServerSource
import mcpatch.server.OnDownload
import mcpatch.stream.ExposedByteArrayOutputStream
import mcpatch.util.File2
import mcpatch.util.MiscUtils
import mcpatch.webdav.CreateIgnoreVerifySsl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class HttpSupport(serverString: String, val options: GlobalOptions)
    : AbstractServerSource()
{
    val baseUrl = serverString
        .run { if (!endsWith("/")) "$this/" else this }
        .run { substring(0, lastIndexOf("/") + 1) }

    val ssl = CreateIgnoreVerifySsl()

    val okClient = OkHttpClient.Builder()
        .sslSocketFactory(ssl.first.socketFactory, ssl.second)
        .connectTimeout(options.httpConnectTimeout.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(options.httpResponseTimeout.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(options.httpResponseTimeout.toLong(), TimeUnit.MILLISECONDS)
        .build()

    val retryTimes: Int = options.retryTimes

    override fun fetchText(relativePath: String): String
    {
        val url = buildURI(relativePath)
        val req = buildRequest(url)
        Log.debug("http request on $url")

        return withRetrying(retryTimes, 1000) {
            try {
                okClient.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) {
                        val body = r.body?.string()?.limitLength()
                        throw HttpResponseStatusCodeException(r.code, 200..300, url, body)
                    }

                    return@withRetrying r.body!!.string()
                }
            } catch (e: ConnectException) {
                throw ConnectionRejectedException(url, e.message ?: "")
            } catch (e: SocketException) {
                throw ConnectionInterruptedException(url, e.message ?: "")
            } catch (e: SocketTimeoutException) {
                throw ConnectionTimeoutException(url, e.message ?: "")
            } catch (e: Throwable) {
                throw e
            }
        }
    }

    override fun downloadFile(relativePath: String, writeTo: File2, callback: OnDownload)
    {
        val url = buildURI(relativePath)
        val link = url.replace("+", "%2B")

        writeTo.makeParentDirs()

        return withRetrying(retryTimes, 1000) {
            try {
                // 测试请求
                val testing = buildRequest(link, mapOf("Range" to "bytes=0-0"))
                val rangeSupported: Boolean
                val length:Long

                Log.debug("http range test request on $url")
                okClient.newCall(testing).execute().use { r ->
                    if(!r.isSuccessful)
                        throw HttpResponseStatusCodeException(r.code, 200..300, link, r.body?.string()?.limitLength())

                    rangeSupported = r.code == 206
                    length = if (rangeSupported) r.headers["Content-Range"].toString().split("/")[1].toLong() else -1
                }

                Log.debug("http request on $url, concurrent is ${if (rangeSupported) "on" else "off"}, write to: ${writeTo.path}")

                if (rangeSupported)
                    concurrentDownload(url, link, length, writeTo, callback)
                else
                    normalDownload(url, link, writeTo, callback)
            } catch (e: ConnectException) {
                throw ConnectionInterruptedException(link, e.message ?: "")
            } catch (e: SocketException) {
                throw ConnectionRejectedException(link, e.message ?: "")
            } catch (e: SocketTimeoutException) {
                throw ConnectionTimeoutException(link, e.message ?: "")
            } catch (e: Throwable) {
                throw e
            }
        }
    }

    override fun buildURI(relativePath: String): String
    {
        return baseUrl + relativePath
    }

    override fun close() { }

    private fun String.limitLength(limit: Int = 500): String
    {
        return if (length > limit) substring(0, limit) + "\n..." else this
    }

    private fun buildRequest(url: String, headers: Map<String, String>? = null): Request
    {
        val req = Request.Builder().url(url)

        if (options.clientUserAgent.isNotEmpty())
            req.addHeader("User-Agent", this.options.clientUserAgent)

        if (headers != null)
            for (header in headers.entries)
                req.addHeader(header.key, header.value)

        for (header in options.httpHeaders)
            req.addHeader(header.key, header.value)
        
        return req.build()
    }

    private fun normalDownload(url: String, link: String, writeTo: File2, callback: OnDownload)
    {
        val req = buildRequest(url)

        okClient.newCall(req).execute().use { r ->
            if(!r.isSuccessful)
                throw HttpResponseStatusCodeException(r.code, 200..300, link, r.body?.string()?.limitLength())

            val body = r.body!!
            val bodyLen = if (body.contentLength() != -1L) body.contentLength() else options.httpFallbackFileSize
            val bufferSize = MiscUtils.chooseBufferSize(bodyLen)

            body.source().use { input ->
                writeTo.file.bufferedOutputStream(bufferSize).use { output ->
                    var bytesReceived: Long = 0
                    var len: Int
                    val buffer = ByteArray(bufferSize)
                    val rrf = ReduceReportingFrequency()

                    while (input.read(buffer).also { len = it } != -1)
                    {
                        output.write(buffer, 0, len)
                        bytesReceived += len

                        val report = rrf.feed(len)
                        if (report > 0)
                            callback(report, bytesReceived, bodyLen)
                    }
                }
            }
        }
    }

    private fun concurrentDownload(url: String, link: String, length: Long, writeTo: File2, callback: OnDownload)
    {
        val blockSize = options.concurrentBlockSize
        val taskBlocks = LinkedBlockingQueue<Pair<Int, LongRange>>()
        val downloadedBlocks = mutableMapOf<Int, ExposedByteArrayOutputStream>()

        for (i in 0 until length / blockSize)
            taskBlocks.put(Pair(i.toInt(), i * blockSize until i * blockSize + blockSize))

        if (length % blockSize > 0)
        {
            val i = taskBlocks.size
            taskBlocks.put(Pair(i, i * blockSize until length))
        }

        val totalDownloadedBytes = AtomicLong()
        val reporter = ReduceReportingFrequency()
        val threads = Integer.max(1, min(options.concurrentThreads, taskBlocks.size))
        val pool = Executors.newFixedThreadPool(threads)
        var ex: Exception? = null

        for (i in 0 until threads)
        {
            pool.execute {
                try {
                    while (true)
                    {
                        val (blockindex, block) = taskBlocks.poll() ?: return@execute

                        Log.debug("http request on $url part $blockindex (${block.first} to ${block.last}), write to: ${writeTo.path}")

                        val req = buildRequest(link, mapOf("Range" to "bytes=${block.first}-${block.last}"))

                        okClient.newCall(req).execute().use { r ->
                            if(!r.isSuccessful)
                                throw HttpResponseStatusCodeException(r.code, 200..300, link, r.body?.string()?.limitLength())

                            if (r.code != 206)
                                throw HttpResponseStatusCodeException(r.code, 206..206, link, r.body?.string()?.limitLength())

                            val body = r.body!!
                            val bodyLen = if (body.contentLength() != -1L) body.contentLength() else blockSize.toLong()
                            val bufferSize = MiscUtils.chooseBufferSize(bodyLen)

                            body.source().use { input ->
                                val buf = ExposedByteArrayOutputStream(bufferSize)

                                buf.use { output ->
                                    var len: Int
                                    val buffer = ByteArray(bufferSize)

                                    while (input.read(buffer).also { len = it } != -1)
                                    {
                                        output.write(buffer, 0, len)

                                        val total = totalDownloadedBytes.addAndGet(len.toLong())
                                        val report = reporter.feed(len)

                                        if (report > 0)
                                            callback(report, total, length)
                                    }
                                }

                                downloadedBlocks[blockindex] = buf
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e !is InterruptedIOException)
                    {
                        ex = e
                        pool.shutdownNow()
                    }
                }
            }
        }

        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.DAYS)

        if (ex != null)
            throw ex!!

        RandomAccessFile(writeTo.file, "rw").use { file ->
            for (i in 0 until downloadedBlocks.size)
            {
                val block = downloadedBlocks[i]!!
                val buf = block.internalBuffer()
                val len = block.size()

                file.write(buf, 0, len)
            }
        }
    }
}