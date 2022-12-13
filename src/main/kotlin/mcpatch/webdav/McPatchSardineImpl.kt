package mcpatch.webdav

import com.github.sardine.impl.SardineException
import com.github.sardine.impl.SardineImpl
import com.github.sardine.impl.handler.VoidResponseHandler
import com.github.sardine.impl.io.ContentLengthInputStream
import com.github.sardine.impl.io.HttpMethodReleaseInputStream
import mcpatch.data.GlobalOptions
import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.config.ConnectionConfig
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.HttpClientBuilder
import java.io.IOException

class McPatchSardineImpl(username: String, password: String, options: GlobalOptions)
    : SardineImpl(
        HttpClientBuilder.create()
            .setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(options.httpConnectTimeout)
                .build())
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setBufferSize(1024 * 1024)
                .build())
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(options.httpConnectTimeout)
                .build())
        , username, password
    )
{
    fun getAlt(url: String): Pair<ContentLengthInputStream, HttpResponse>
    {
        val get = HttpGet(url)

        // Must use #execute without handler, otherwise the entity is consumed
        // already after the handler exits.
        val response = this.execute(get)
        val handler = VoidResponseHandler()

        return try {
            try {
                handler.handleResponse(response)
            } catch (e: SardineException) {
                val body = ContentLengthInputStream(HttpMethodReleaseInputStream(response), response.entity.contentLength).readBytes().decodeToString()
                throw GetException(e, body)
            }
            // Will abort the read when closed before EOF.

            Pair(ContentLengthInputStream(HttpMethodReleaseInputStream(response), response.entity.contentLength), response)
        } catch (ex: IOException) {
            get.abort()
            throw ex
        }
    }

    class GetException(val ex: SardineException, val body: String) : Exception(ex)
}