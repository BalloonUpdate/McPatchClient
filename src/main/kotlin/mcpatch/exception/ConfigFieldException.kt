package mcpatch.exception

import mcpatch.exception.BaseException

class ConfigFieldException(fieldName: String)
    : BaseException("配置文件中的选项($fieldName)无效")