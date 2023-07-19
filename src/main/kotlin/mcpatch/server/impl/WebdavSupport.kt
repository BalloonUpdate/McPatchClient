package mcpatch.server.impl

import com.github.sardine.impl.SardineException
import mcpatch.data.GlobalOptions
import mcpatch.exception.*
import mcpatch.extension.FileExtension.bufferedOutputStream
import mcpatch.logging.Log
import mcpatch.server.AbstractServerSource
import mcpatch.server.OnDownload
import mcpatch.util.File2
import mcpatch.util.MiscUtils
import mcpatch.webdav.McPatchSardineImpl
import org.apache.http.conn.HttpHostConnectException
import java.io.ByteArrayOutputStream
import java.net.SocketException
import javax.net.ssl.SSLPeerUnverifiedException

class WebdavSupport(serverString: String, val options: GlobalOptions)
    : AutoCloseable, AbstractServerSource()
{
    val webdav: McPatchSardineImpl

    val scheme: String
    val host: String
    val port: Int
    val username: String
    val password: String
    val basepath: String

    val retryTimes: Int = options.retryTimes

    init {
        val reg = Regex("^(webdavs?)://(.+?):(.+?):(.+?):(\\d+)((?:/[^/]+)*)\$")

        val matchResult = reg.matchEntire(serverString) ?: throw InvalidWebdavServerStringException(serverString)
        val gourps = matchResult.groupValues.drop(1)

        scheme = if (gourps[0] == "webdav") "http" else "https"
        username = gourps[1]
        password = gourps[2]
        host = gourps[3]
        port = gourps[4].toInt()
        basepath = if (gourps.size >= 6) gourps[5] else ""

//        Log.info("scheme: $scheme")
//        Log.info("host: $host")
//        Log.info("port: $port")
//        Log.info("username: $username")
//        Log.info("password: $password")
//        Log.info("basepath: $basepath")

        webdav = McPatchSardineImpl(username, password, options)
        webdav.enableCompression()
        webdav.enablePreemptiveAuthentication(host)

//        Log.debug("Current Directory: ${webdav.list(buildURI("")).joinToString { it.path }}")
    }

    override fun fetchText(relativePath: String): String
    {
        val url = buildURI(relativePath)
        Log.debug("webdav request on $url")

        return withRetrying(retryTimes, 1000) {
            ByteArrayOutputStream().use { temp ->
                try {
                    webdav.getAlt(url).first.use { remote ->
                        remote.copyTo(temp)
                    }
                } catch (e: HttpHostConnectException) {
                    throw WebdavConnectException(e, url)
                } catch (e: McPatchSardineImpl.GetException) {
                    throw HttpResponseStatusCodeException(e.ex.statusCode, 200..300, url, e.body)
                } catch (e: SardineException) {
                    throw HttpResponseStatusCodeException(e.statusCode, 200..300, url, "")
                } catch (e: SSLPeerUnverifiedException) {
                    throw SslCertificateUnverifiedException(url, e.toString())
                } catch (e: SocketException) {
                    throw ConnectionInterruptedException(url, e.message ?: "")
                }

                return@withRetrying temp.toByteArray().decodeToString()
            }
        }
    }

    override fun downloadFile(relativePath: String, writeTo: File2, callback: OnDownload)
    {
        val url = buildURI(relativePath)
        Log.debug("webdav request on $url, write to: ${writeTo.path}")

        return withRetrying(retryTimes, 1000) {
            try {
                webdav.getAlt(url).first.use { remote ->
                    val bodyLen = if (remote.length >= 0) remote.length else options.httpFallbackFileSize

                    writeTo.file.bufferedOutputStream(1024 * 1024).use { output ->
                        var bytesReceived: Long = 0
                        var len: Int
                        val buffer = ByteArray(MiscUtils.chooseBufferSize(bodyLen))
                        val rrf = ReduceReportingFrequency()

                        while (remote.read(buffer).also { len = it; bytesReceived += it } != -1) {
                            output.write(buffer, 0, len)

                            val report = rrf.feed(len)
                            if (report > 0)
                                callback(report, bytesReceived, bodyLen)

                            callback(len.toLong(), bytesReceived, bodyLen)
                        }
                    }
                }
            } catch (e: HttpHostConnectException) {
                throw WebdavConnectException(e, url)
            } catch (e: McPatchSardineImpl.GetException) {
                throw HttpResponseStatusCodeException(e.ex.statusCode, 200..300, url, e.body)
            } catch (e: SardineException) {
                throw HttpResponseStatusCodeException(e.statusCode, 200..300, url, "")
            } catch (e: SSLPeerUnverifiedException) {
                throw SslCertificateUnverifiedException(url, e.toString())
            } catch (e: SocketException) {
                throw ConnectionInterruptedException(url, e.message ?: "")
            }
        }
    }

    override fun buildURI(relativePath: String): String
    {
        return "$scheme://$host:$port$basepath/$relativePath"
    }

    override fun close()
    {

    }
}