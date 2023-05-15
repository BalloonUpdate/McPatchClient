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


@Suppress("DuplicatedCode")
class HttpSupport(serverString: String, private val options: GlobalOptions)
    : AbstractServerSource()
{
    val baseUrl = serverString
        .run { if (!this.endsWith("/")) "$this/" else this }
        .run { this.substring(0, this.lastIndexOf("/") + 1) }

    val okClient = OkHttpClient.Builder()
        .connectTimeout(this.options.httpConnectTimeout.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(this.options.httpResponseTimeout.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(this.options.httpResponseTimeout.toLong(), TimeUnit.MILLISECONDS)
        .build()

    val retryTimes: Int = this.options.retryTimes

    override fun fetchText(relativePath: String): String
    {
        val url = this.buildURI(relativePath)
        val req = Request.Builder()
            .url(url)
            .also {
                //如果 UA 非空，则填入自定义 UA。
                if (options.clientUserAgent.isNotEmpty())
                    it.addHeader("User-Agent", this.options.clientUserAgent)
            }
            .build()
        Log.debug("http request on $url")

        return this.withRetrying(this.retryTimes, 1000) {
            try {
                this.okClient.newCall(req).execute().use { r ->
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
        val url = this.buildURI(relativePath)
        Log.debug("http request on $url, write to: ${writeTo.path}")
        val link = url.replace("+", "%2B")

        writeTo.makeParentDirs()
        val req = Request.Builder()
            .url(link)
            .also {
                //同 43 行
                if (options.clientUserAgent.isNotEmpty())
                    it.addHeader("User-Agent", this.options.clientUserAgent)
            }
            .build()
        return this.withRetrying(this.retryTimes, 1000) {
            try {
                this.okClient.newCall(req).execute().use { r ->
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

                            while (input.read(buffer).also { len = it } != -1) {
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
        return this.baseUrl + relativePath
    }

    override fun close() { }

    private fun String.limitLength(limit: Int = 500): String
    {
        return if (this.length > limit) this.substring(0, limit) + "\n..." else this
    }
}