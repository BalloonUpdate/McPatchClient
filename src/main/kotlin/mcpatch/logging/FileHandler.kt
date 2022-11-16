package mcpatch.logging

import mcpatch.util.File2
import java.io.PrintWriter
import java.text.SimpleDateFormat

class FileHandler(logsys: Log, val logFile: File2) : AbstractHandler(logsys)
{
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS")
    var fileWriter: PrintWriter? = null

    override fun onInit()
    {
        if (logFile.exists)
            logFile.clear()
        else
            logFile.content = ""

        fileWriter = PrintWriter(logFile.file)
    }

    override fun onDestroy() {
        fileWriter?.close()
    }

    override fun onMessage(message: Message)
    {
        if (fileWriter == null)
            return

        if (!message.newLine)
        {
            fileWriter!!.print(message.message)
            fileWriter?.flush()
            return
        }

        val ts = fmt.format(System.currentTimeMillis())
        val level = message.level.name.uppercase()
        val tag = if (message.tag != "") "[${message.tag}] " else ""
        val rangedTags = message.rangedTags.joinToString("/").run { if (isNotEmpty()) "[$this] " else "" }
        val prefix = String.format("[ %s %-5s ] %s%s", ts, level, rangedTags, tag)

        var text = prefix + message.message
        if(message.newLineIndent)
            text = text.replace(Regex("\n"), "\n"+prefix)

        fileWriter!!.println(text)
        fileWriter?.flush()
    }
}