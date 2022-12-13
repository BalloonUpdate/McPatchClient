package mcpatch.exception

class UpdateDirNotFoundException
    : BaseException("找不到.minecraft目录。请将软件放到.minecraft目录的同级或者.minecraft目录下（最大7层深度）然后再次尝试运行")