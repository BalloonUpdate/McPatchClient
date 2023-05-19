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
import mcpatch.util.File2
import mcpatch.util.MiscUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class HttpSupport(serverString: String, private val options: GlobalOptions)
    : AbstractServerSource()
{
    val baseUrl = serverString
        .run { if (!endsWith("/")) "$this/" else this }
        .run { substring(0, lastIndexOf("/") + 1) }

    val okClient = OkHttpClient.Builder()
        .connectTimeout(options.httpConnectTimeout.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(options.httpResponseTimeout.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(options.httpResponseTimeout.toLong(), TimeUnit.MILLISECONDS)
        .build()

    val retryTimes: Int = options.retryTimes

    override fun fetchText(relativePath: String): String
    {
        val url = buildURI(relativePath)
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", value = options.clientUserAgent)
            .build()
        Log.debug("http request on $url")

        return withRetrying(retryTimes, 1000) {
            try {
                okClient.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) {
                        val body = r.body?.string()?.limitLength()
                        throw HttpResponseStatusCodeException(r.code, url, body)
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
        Log.debug("http request on $url, write to: ${writeTo.path}")

        val link = url.replace("+", "%2B")

        writeTo.makeParentDirs()
        val req = Request.Builder().url(link).build()

        return withRetrying(retryTimes, 1000) {
            try {
                okClient.newCall(req).execute().use { r ->
                    if(!r.isSuccessful)
                        throw HttpResponseStatusCodeException(r.code, link, r.body?.string()?.limitLength())

                    val body = r.body!!
                    val bodyLen = if (body.contentLength() != -1L) body.contentLength() else 1024 * 1024 * 1024
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

                    return@withRetrying
                }
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
}