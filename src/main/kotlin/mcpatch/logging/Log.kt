package mcpatch.logging

import java.util.*

object Log
{
    val handlers: MutableList<AbstractHandler> = mutableListOf()

    val rangedTags = LinkedList<String>()

    fun debug(message: String, newLine: Boolean = true) = message(LogLevel.DEBUG, "", message, newLine)

    fun info(message: String, newLine: Boolean = true) = message(LogLevel.INFO, "", message, newLine)

    fun warn(message: String, newLine: Boolean = true) = message(LogLevel.WARN, "", message, newLine)

    fun error(message: String, newLine: Boolean = true) = message(LogLevel.ERROR, "", message, newLine)

    fun debug(tag: String, message: String, newLine: Boolean = true) = message(LogLevel.DEBUG, tag, message, newLine)

    fun info(tag: String, message: String, newLine: Boolean = true) = message(LogLevel.INFO, tag, message, newLine)

    fun warn(tag: String, message: String, newLine: Boolean = true) = message(LogLevel.WARN, tag, message, newLine)

    fun error(tag: String, message: String, newLine: Boolean = true) = message(LogLevel.ERROR, tag, message, newLine)

    fun message(level: LogLevel, tag: String, message: String, newLine: Boolean)
    {
        for (h in handlers)
            if(level.ordinal >= h.filter.ordinal)
                h.onMessage(
                    Message(
                    time = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = message,
                    newLineIndent = true,
                    rangedTags = rangedTags,
                    newLine = newLine,
                )
                )
    }

    fun openRangedTag(tag: String)
    {
        if (rangedTags.lastOrNull().run { this == null || this != tag })
            rangedTags.addLast(tag)
    }

    fun closeRangedTag()
    {
        if (rangedTags.isNotEmpty())
            rangedTags.removeLast()
    }

    fun withRangedTag(tag: String, scope: () -> Unit)
    {
        val split = tag.split("/")

        for (s in split)
            openRangedTag(s)

        scope()

        for (s in split)
            closeRangedTag()
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

