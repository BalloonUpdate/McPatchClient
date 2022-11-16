package mcpatch.exception

class SFTPAuthenticationException(hostport: String)
    : BaseException("SFTP 身份验证失败 $hostport")