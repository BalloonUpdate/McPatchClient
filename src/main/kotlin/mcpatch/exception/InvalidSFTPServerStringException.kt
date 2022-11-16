package mcpatch.exception

class InvalidSFTPServerStringException(serverString: String)
    : BaseException("无效的sftp服务器字符串 $serverString")