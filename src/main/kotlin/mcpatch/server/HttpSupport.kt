package mcpatch.server

import mcpatch.data.GlobalOptions
import mcpatch.exception.*
import mcpatch.extension.FileExtension.bufferedOutputStream
import mcpatch.logging.HttpResponseStatusCodeException
import mcpatch.logging.Log
import mcpatch.util.File2
import mcpatch.util.MiscUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class HttpSupport(serverString: String, options: GlobalOptions) : AbstractServerSource
{
    val baseUrl = serverString
        .run { if (!endsWith("/")) "$this/" else this }
        .run { substring(0, lastIndexOf("/") + 1) }

    val okClient = OkHttpClient.Builder()
        .connectTimeout(options.httpConnectTimeout.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(options.httpReadTimeout.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(options.httpWriteTimeout.toLong(), TimeUnit.MILLISECONDS)
        .build()

    val retryTimes: Int = options.retryTimes

    override fun fetchText(relativePath: String): String
    {
        val url = buildURI(relativePath)
        val req = Request.Builder().url(url).build()
        Log.debug("http request on $url")

        var ex: Throwable? = null
        var retries = retryTimes
        while (--retries >= 0)
        {
            try {
                okClient.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) {
                        val body = r.body?.string()?.run { if (length > 300) substring(0, 300) + "\n..." else this }
                        throw HttpResponseStatusCodeException(r.code, url, body)
                    }

                    return r.body!!.string()
                }
            } catch (e: ConnectException) {
                ex = ConnectionRejectedException(url, e.message ?: "")
            } catch (e: SocketException) {
                ex = ConnectionInterruptedException(url, e.message ?: "")
            } catch (e: SocketTimeoutException) {
                ex = ConnectionTimeoutException(url, e.message ?: "")
            } catch (e: Throwable) {
                ex = e
            }

            Log.warn("")
            Log.warn(ex.toString())
            Log.warn("retrying $retries ...")

            Thread.sleep(1000)
        }

        throw ex!!
    }

    override fun downloadFile(relativePath: String, writeTo: File2, lengthExpected: Long, callback: OnDownload)
    {
        val url = buildURI(relativePath)
        Log.debug("http request on $url, write to: ${writeTo.path}")

        val link = url.replace("+", "%2B")

        writeTo.makeParentDirs()
        val req = Request.Builder().url(link).build()

        var ex: Throwable? = null
        var retries = retryTimes
        while (--retries >= 0)
        {
            try {
                okClient.newCall(req).execute().use { r ->
                    if(!r.isSuccessful)
                        throw HttpResponseStatusCodeException(r.code, link, r.body?.string())

                    r.body!!.byteStream().use { input ->
                        writeTo.file.bufferedOutputStream(1024 * 1024).use { output ->
                            var bytesReceived: Long = 0
                            var len: Int
                            val buffer = ByteArray(MiscUtils.chooseBufferSize(lengthExpected))

                            while (input.read(buffer).also { len = it; bytesReceived += it } != -1)
                            {
                                output.write(buffer, 0, len)
                                callback(len.toLong(), bytesReceived, lengthExpected)
                            }
                        }
                    }

                    return
                }
            } catch (e: ConnectException) {
                ex = ConnectionInterruptedException(link, e.message ?: "")
            } catch (e: SocketException) {
                ex = ConnectionRejectedException(link, e.message ?: "")
            } catch (e: SocketTimeoutException) {
                ex = ConnectionTimeoutException(link, e.message ?: "")
            } catch (e: Throwable) {
                ex = e
            }

            Log.warn("")
            Log.warn(ex.toString())
            Log.warn("retrying $retries ...")

            Thread.sleep(1000)
        }

        throw ex!!
    }

    override fun buildURI(relativePath: String): String
    {
        return baseUrl + relativePath
    }

    override fun close() { }
}