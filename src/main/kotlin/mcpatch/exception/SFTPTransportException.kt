package mcpatch.exception

class SFTPTransportException(uri: String, more: String)
    : BaseException("SFTP 下载文件失败 ($uri) $more")