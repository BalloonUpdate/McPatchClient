package mcpatch.exception

import java.io.File

class HotUpdateSignalException(val temporal: File) : Exception()