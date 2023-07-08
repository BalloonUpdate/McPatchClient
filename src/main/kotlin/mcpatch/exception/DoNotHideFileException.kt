package mcpatch.exception

import mcpatch.util.File2

class DoNotHideFileException(file: File2)
    : BaseException("无法更新本地版本号文件，此文件可能被设为了隐藏文件，请取消隐藏：${file.path}")