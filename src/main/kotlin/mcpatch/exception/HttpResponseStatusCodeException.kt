package mcpatch.exception

import mcpatch.util.PathUtils

class HttpResponseStatusCodeException(statusCode: Int, url: String, body: String?)
    : BaseException("Http状态码($statusCode)不在2xx-3xx之间(${PathUtils.getFileNamePart(url)})\n$url\n" +
        if (body?.isNotEmpty() == true) "以下为服务端返回的消息(HttpBody):\n${body}" else "服务端没有返回任何附加的消息(HttpBody)"
)