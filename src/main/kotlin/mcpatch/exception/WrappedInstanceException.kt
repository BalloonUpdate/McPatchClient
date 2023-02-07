package mcpatch.exception

class WrappedInstanceException(exitCode: Int)
    : BaseException("The wrapped instance of McPatchClient returned a non-zero process exit code $exitCode. " +
        "You see this message because McPatchClient runs in the JavaAgent mode and the subprocess of itself fails. " +
        "If you want to analyze why Minecraft process crashs, see the logs just before this message instead of this one.")