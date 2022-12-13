package mcpatch.exception

import mcpatch.util.PathUtils

class SslCertificateUnverifiedException(url: String, more: String)
    : BaseException("服务器SSL证书无法验证(${PathUtils.getFileNamePart(url)}): $url \n$more")