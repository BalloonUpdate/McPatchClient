package mcpatch.exception

class PatchCorruptedException(version: String, part: String)
    : BaseException("版本 $version 的补丁文件的 $part 已损坏，无法进行更新")