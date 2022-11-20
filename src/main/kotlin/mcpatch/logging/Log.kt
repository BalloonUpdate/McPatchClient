package mcpatch.logging

import java.util.*

object Log
{
    val handlers: MutableList<AbstractHandler> = mutableListOf()

    val rangedTags = LinkedList<String>()

    @JvmOverloads
    fun debug(message: String, newLine: Boolean = true) = message(LogLevel.DEBUG, "", message, newLine)

    @JvmOverloads
    fun info(message: String, newLine: Boolean = true) = message(LogLevel.INFO, "", message, newLine)

    @JvmOverloads
    fun warn(message: String, newLine: Boolean = true) = message(LogLevel.WARN, "", message, newLine)

    @JvmOverloads
    fun error(message: String, newLine: Boolean = true) = message(LogLevel.ERROR, "", message, newLine)

    @JvmOverloads
    fun debug(message: Any, newLine: Boolean = true) = message(LogLevel.DEBUG, "", message.toString(), newLine)

    @JvmOverloads
    fun info(message: Any, newLine: Boolean = true) = message(LogLevel.INFO, "", message.toString(), newLine)

    @JvmOverloads
    fun warn(message: Any, newLine: Boolean = true) = message(LogLevel.WARN, "", message.toString(), newLine)

    @JvmOverloads
    fun error(message: Any, newLine: Boolean = true) = message(LogLevel.ERROR, "", message.toString(), newLine)

    fun message(level: LogLevel, tag: String, message: String, newLine: Boolean)
    {
        for (h in handlers)
            if(level.ordinal >= h.filter.ordinal)
                h.onMessage(Message(
                    time = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = message,
                    newLineIndent = true,
                    rangedTags = rangedTags,
                    newLine = newLine,
                ))
    }

    fun openTag(tag: String)
    {
        if (rangedTags.lastOrNull().run { this == null || this != tag })
            rangedTags.addLast(tag)
    }

    fun closeTag()
    {
        if (rangedTags.isNotEmpty())
            rangedTags.removeLast()
    }

    fun destory()
    {
        handlers.forEach { it.onDestroy() }
        handlers.clear()
    }

    fun addHandler(handler: AbstractHandler)
    {
        if(handler in handlers)
            return
        handlers += handler.also { it.onInit() }
    }

    inline fun <reified T> removeHandler() where T: AbstractHandler
    {
        for (h in handlers)
        {
            if(T::class == h.javaClass)
            {
                h.onDestroy()
                handlers.remove(h)
                return
            }
        }
    }

    enum class LogLevel
    {
        ALL, DEBUG, INFO, WARN, ERROR, NONE
    }
}

