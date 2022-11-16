package mcpatch.logging

abstract class AbstractHandler(
    private val logsys: Log,
    val filter: Log.LogLevel = Log.LogLevel.ALL
) {
    open fun onInit() {}

    open fun onDestroy() {}

    abstract fun onMessage(message: Message)
}