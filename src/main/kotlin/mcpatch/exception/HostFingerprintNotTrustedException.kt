package mcpatch.exception

class HostFingerprintNotTrustedException(more: String)
    : BaseException("主机指纹不可信任 $more")