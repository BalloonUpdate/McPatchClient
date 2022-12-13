package mcpatch.exception

import mcpatch.util.PathUtils

class ConnectionInterruptedException(url: String, more: String)
    : BaseException("连接中断(${PathUtils.getFileNamePart(url)}): $url ($more)")