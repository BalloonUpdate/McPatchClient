package mcpatch.exception

class InvalidVersionException(currentVersion: String)
    : BaseException("当前客户端版本号 $currentVersion 不在服务端的版本号列表里，无法确定版本前后关系，更新失败！")