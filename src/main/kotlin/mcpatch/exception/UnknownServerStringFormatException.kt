package mcpatch.exception

class UnknownServerStringFormatException(serverString: String)
    : BaseException("未知格式的服务器字符串 $serverString")