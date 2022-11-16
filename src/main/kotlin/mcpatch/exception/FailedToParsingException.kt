package mcpatch.exception

class FailedToParsingException(name: String, format: String, more: String)
    : BaseException("$name 无法解码为 $format 格式 ($more)")