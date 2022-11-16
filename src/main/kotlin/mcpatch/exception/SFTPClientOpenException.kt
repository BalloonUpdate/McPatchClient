package mcpatch.exception

class SFTPClientOpenException(hostport: String)
    : BaseException("SFTP Client 打开失败 $hostport")