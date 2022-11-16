package mcpatch.logging

import java.text.SimpleDateFormat

class ConsoleHandler(logsys: Log, var logLevel: Log.LogLevel) : AbstractHandler(logsys)
{
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS")

    override fun onMessage(message: Message)
    {
        if (!message.newLine)
        {
            print(message.message)
            System.out.flush()
            return
        }

        val tag = if (message.tag != "") "[${message.tag}] " else ""
        val rangedTags = message.rangedTags.joinToString("/").run { if (isNotEmpty()) "[$this] " else "" }
        val prefix = String.format("%s%s", rangedTags, tag)

        var text = prefix + message.message
        if(message.newLineIndent)
            text = text.replace(Regex("\n"), "\n"+prefix)

        if (message.level.ordinal >= logLevel.ordinal)
        {
            println(text)
//                System.err.println(text)
//            else
//                System.out.println(text)
        }
    }
}