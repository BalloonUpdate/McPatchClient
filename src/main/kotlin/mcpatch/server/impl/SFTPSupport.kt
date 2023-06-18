package mcpatch.server.impl

import mcpatch.data.GlobalOptions
import mcpatch.exception.*
import mcpatch.extension.FileExtension.bufferedOutputStream
import mcpatch.logging.Log
import mcpatch.server.AbstractServerSource
import mcpatch.server.OnDownload
import mcpatch.util.File2
import mcpatch.util.MiscUtils
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException
import java.io.ByteArrayOutputStream
import java.io.IOException

class SFTPSupport(serverString: String, val options: GlobalOptions)
    : AutoCloseable, AbstractServerSource()
{
    val ssh = SSHClient()
    var sftp: SFTPClient? = null

    val host: String
    val port: Int
    val fingerprint: String
    val username: String
    val password: String
    val basepath: String

    val retryTimes: Int = options.retryTimes

    init {
        val reg = Regex("^sftp://(.+?):(.+?):(.+?)@(.+?):(\\d+)/((.+)(?<!/))?\$")

        val matchResult = reg.matchEntire(serverString) ?: throw InvalidSFTPServerStringException(serverString)
        val gourps = matchResult.groupValues.drop(1)

        username = gourps[0]
        password = gourps[1]
        fingerprint = gourps[2]
        host = gourps[3]
        port = gourps[4].toInt()
        basepath = if (gourps.size >= 6) gourps[5] else ""

//        Log.info("host: $host")
//        Log.info("port: $port")
//        Log.info("fingerprint: $fingerprint")
//        Log.info("username: $username")
//        Log.info("password: $password")
//        Log.info("basepath: $basepath")

//        ssh.loadKnownHosts()
        ssh.addHostKeyVerifier(fingerprint)
    }

    fun checkOpen()
    {
        if (!ssh.isConnected)
        {
            Log.info("open ssh connection on $host:$port")

            try {
                ssh.connect(host, port)
                ssh.authPassword(username, password)
            } catch (e: TransportException) {
                throw HostFingerprintNotTrustedException(e.message ?: "")
            } catch (e: UserAuthException) {
                throw SFTPAuthenticationException("$host:$port")
            }

            try {
                sftp = ssh.newSFTPClient()
            } catch (e: IOException) {
                throw SFTPClientOpenException("$host:$port")
            }
        }
    }

    override fun close()
    {
        if (ssh.isConnected)
        {
            Log.info("close ssh connection on $host:$port")

            sftp = null
            ssh.disconnect()
        }
    }

    override fun fetchText(relativePath: String): String
    {
        checkOpen()
        Log.info("sftp reqeust on ${buildURI(relativePath)}")

        return withRetrying(retryTimes, 1000) {
            ByteArrayOutputStream().use { temp ->
                try {
                    sftp!!.open(compositePath(relativePath)).use { file ->
                        file.RemoteFileInputStream().use { remote ->
                            remote.copyTo(temp)
                        }
                    }

                    return@withRetrying temp.toByteArray().decodeToString()
                } catch (e: SFTPException) {
                    throw SFTPTransportException(buildURI(relativePath), e.message ?: "")
                } catch (e: Throwable) {
                    throw e
                }
            }
        }
    }

    override fun downloadFile(relativePath: String, writeTo: File2, callback: OnDownload)
    {
        checkOpen()
        Log.info("sftp reqeust on ${buildURI(relativePath)}, write to: ${writeTo.path}")

        return withRetrying(retryTimes, 1000) {
            try {
                sftp!!.open(compositePath(relativePath)).use { file ->
                    val lengthExpected = file.length()

                    file.RemoteFileInputStream().use { remote ->
                        writeTo.file.bufferedOutputStream(1024 * 1024).use { output ->
                            var bytesReceived: Long = 0
                            var len: Int
                            val buffer = ByteArray(MiscUtils.chooseBufferSize(lengthExpected))
                            val rrf = ReduceReportingFrequency()

                            while (remote.read(buffer).also { len = it } != -1)
                            {
                                output.write(buffer, 0, len)
                                bytesReceived += len

                                val report = rrf.feed(len)
                                if (report > 0)
                                    callback(report, bytesReceived, lengthExpected)
                            }
                        }
                    }
                }

                return@withRetrying
            } catch (e: SFTPException) {
                throw SFTPTransportException(buildURI(relativePath), e.message ?: "")
            } catch (e: Throwable) {
                throw e
            }
        }
    }

    override fun buildURI(relativePath: String): String
    {
        return "sftp://$host:$port/${compositePath(relativePath)}"
    }

    fun compositePath(relativePath: String): String
    {
        return "$basepath${if (basepath.isNotEmpty()) "/" else ""}$relativePath"
    }
}