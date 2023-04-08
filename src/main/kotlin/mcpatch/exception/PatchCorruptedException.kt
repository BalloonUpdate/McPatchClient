package mcpatch.exception

class PatchCorruptedException(version: String, reson: String, a: Int)
    : BaseException("版本 $version $reson，无法进行更新")