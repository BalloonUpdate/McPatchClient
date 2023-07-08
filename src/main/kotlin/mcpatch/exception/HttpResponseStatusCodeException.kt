package mcpatch.exception

import mcpatch.util.PathUtils

class HttpResponseStatusCodeException(statusCode: Int, expected: IntRange, url: String, body: String?)
    : BaseException("HTTP状态码 $statusCode 不在 $expected 之间\n" +
        "文件: ${PathUtils.getFileNamePart(url)}($url)\n" +
        "Body: \n$body"
)