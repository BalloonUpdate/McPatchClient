package mcpatch.exception

import mcpatch.util.PathUtils
import org.apache.http.conn.HttpHostConnectException

class WebdavConnectException(ex: HttpHostConnectException, url: String)
    : BaseException("Webdav连接失败(${PathUtils.getFileNamePart(url)}): $url (${ex.cause})")