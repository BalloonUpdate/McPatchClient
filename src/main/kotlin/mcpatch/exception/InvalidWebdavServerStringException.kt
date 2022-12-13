package mcpatch.exception

class InvalidWebdavServerStringException(serverString: String)
    : BaseException("无效的webdav服务器字符串 $serverString")