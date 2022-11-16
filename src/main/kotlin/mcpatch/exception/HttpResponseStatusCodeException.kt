package mcpatch.logging

import mcpatch.exception.BaseException
import mcpatch.util.PathUtils

class HttpResponseStatusCodeException(statusCode: Int, url: String, body: String?)
    : BaseException("Http状态码($statusCode)不在2xx-3xx之间(${PathUtils.getFileNamePart(url)})\n$url\n${body ?: "<No Body>"}")