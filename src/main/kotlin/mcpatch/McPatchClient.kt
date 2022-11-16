package mcpatch

import mcpatch.data.*
import mcpatch.exception.BaseException
import mcpatch.exception.ConfigFileNotFoundException
import mcpatch.exception.FailedToParsingException
import mcpatch.exception.UpdateDirNotFoundException
import mcpatch.gui.McPatchWindow
import mcpatch.localization.LangNodes
import mcpatch.localization.Localization
import mcpatch.logging.ConsoleHandler
import mcpatch.logging.FileHandler
import mcpatch.logging.Log
import mcpatch.util.*
import GUI.SetupSwing
import mcpatch.extension.RuntimeExtension.usedMemory
import mcpatch.util.DialogUtils
import org.json.JSONException
import org.yaml.snakeyaml.Yaml
import java.awt.Desktop
import java.io.File
import java.io.InterruptedIOException
import java.lang.instrument.Instrumentation
import java.nio.channels.ClosedByInterruptException
import java.util.jar.JarFile

class McPatchClient
{
    /**
     * McPatchClient主逻辑
     * @param graphicsMode 是否以图形模式启动（桌面环境通常以图形模式启动，安卓环境通常不以图形模式启动）
     * @param hasStandaloneProgress 程序是否拥有独立的进程。从JavaAgent参数启动没有独立进程，双击启动有独立进程（java -jar xx.jar也属于独立启动）
     * @param externalConfigFile 可选的外部配置文件路径，如果为空则使用 progDir/config.yml
     * @param enableLogFile 是否写入日志文件
     * @param disableTheme 是否禁用主题
     */
    fun run(
        graphicsMode: Boolean,
        hasStandaloneProgress: Boolean,
        externalConfigFile: File2?,
        enableLogFile: Boolean,
        disableTheme: Boolean,
    ): Boolean {
        try {
            val workDir = getWorkDirectory()
            val progDir = getProgramDirectory(workDir)
            val options = GlobalOptions.CreateFromMap(readConfig(externalConfigFile ?: (progDir + "config.yml")))
            val updateDir = getUpdateDirectory(workDir, options)

            // 初始化日志系统
            if (enableLogFile)
                Log.addHandler(FileHandler(Log, progDir + (if (graphicsMode) "mc-patch-client.log" else "mc-patch-client.txt")))

            val consoleLogLevel = if (Environment.IsProduction)
                    (if (graphicsMode || !enableLogFile) Log.LogLevel.DEBUG else Log.LogLevel.INFO)
                else
                    Log.LogLevel.INFO
            Log.addHandler(ConsoleHandler(Log, consoleLogLevel))
            if (!hasStandaloneProgress)
                Log.openRangedTag("McPatchClient")

            // 收集并打印环境信息
            Log.info("RAM: " + MiscUtils.convertBytes(Runtime.getRuntime().usedMemory()))
            Log.info("Graphics Mode: $graphicsMode")
            Log.info("Standalone: $hasStandaloneProgress")
            val jvmVersion = System.getProperty("java.version")
            val jvmVender = System.getProperty("java.vendor")
            val osName = System.getProperty("os.name")
            val osArch = System.getProperty("os.arch")
            val osVersion = System.getProperty("os.version")
            Log.info("Updating Directory:   ${updateDir.path}")
            Log.info("Working Directory:    ${workDir.path}")
            Log.info("Executable Directory: ${if(Environment.IsProduction) Environment.JarFile!!.parent.path else "dev-mode"}")
            Log.info("Application Version:  ${Environment.Version} (${Environment.GitCommit})")
            Log.info("Java virtual Machine: $jvmVender $jvmVersion")
            Log.info("Operating System: $osName $osVersion $osArch")

            Localization.init(readLangs())

            // 应用主题
            if (graphicsMode && !disableTheme && !options.disableTheme)
                SetupSwing.init()

            // 初始化UI
            val window = if (graphicsMode) McPatchWindow() else null

            // 将更新任务单独分进一个线程执行，方便随时打断线程
            var ex: Throwable? = null
            val workthread = WorkThread(window, options, updateDir, progDir)
            workthread.isDaemon = true
            workthread.setUncaughtExceptionHandler { _, e -> ex = e }

            if (!options.quietMode)
                window?.show()

            window?.titleTextSuffix = ""
            window?.titleText = Localization[LangNodes.window_title]
            window?.labelText = Localization[LangNodes.connecting_message]
            window?.onWindowClosing?.once { win ->
                win.hide()
                if (workthread.isAlive)
                    workthread.interrupt()
            }

            workthread.start()
            workthread.join()

            window?.destroy()

            // 处理工作线程里的异常
            if (ex != null)
            {
                if (//            ex !is SecurityException &&
                    ex !is InterruptedException &&
                    ex !is InterruptedIOException &&
                    ex !is ClosedByInterruptException)
                {
                    try {
                        Log.error(ex!!.javaClass.name)
                        Log.error(ex!!.stackTraceToString())
                    } catch (e: Exception) {
                        println("------------------------")
                        println(e.javaClass.name)
                        println(e.stackTraceToString())
                    }

                    if (graphicsMode)
                    {
                        val className = if (ex!! !is BaseException) ex!!.javaClass.name + "\n" else ""
                        val errMessage = MiscUtils.stringBreak(className + (ex!!.message ?: "<No Exception Message>"), 80)
                        val title = "Error occurred ${Environment.Version}"
                        var content = errMessage + "\n"
                        content += if (!hasStandaloneProgress) "点击\"是\"显示错误详情并崩溃Minecraft，" else "点击\"是\"显示错误详情并退出，"
                        content += if (!hasStandaloneProgress) "点击\"否\"继续启动Minecraft" else "点击\"否\"直接退出程序"
                        val choice = DialogUtils.confirm(title, content)
                        if (!hasStandaloneProgress)
                        {
                            if (choice)
                            {
                                DialogUtils.error("Callstack", ex!!.stackTraceToString())
                                throw ex!!
                            }
                        } else {
                            if (choice)
                                DialogUtils.error("Callstack", ex!!.stackTraceToString())
                            throw ex!!
                        }
                    } else {
                        if (options.noThrowing)
                            println("文件更新失败！但因为设置了no-throwing参数，游戏仍会继续运行！\n\n\n")
                        else
                            throw ex!!
                    }
                } else {
                    Log.info("updating thread interrupted by user")
                }
            } else {
                return workthread.downloadedVersionCount > 0
            }
        } catch (e: UpdateDirNotFoundException) {
            if (graphicsMode)
                DialogUtils.error("", e.message ?: "<No Exception Message>")
        } catch (e: ConfigFileNotFoundException) {
            if (graphicsMode)
                DialogUtils.error("", e.message ?: "<No Exception Message>")
        } catch (e: FailedToParsingException) {
            if (graphicsMode)
                DialogUtils.error("", e.message ?: "<No Exception Message>")
        } finally {
            Log.info("RAM: " + MiscUtils.convertBytes(Runtime.getRuntime().usedMemory()))
        }

        return false
    }

    /**
     * 向上搜索，直到有一个父目录包含.minecraft目录，然后返回这个父目录。最大搜索7层目录
     * @param basedir 从哪个目录开始向上搜索
     * @return 包含.minecraft目录的父目录。如果找不到则返回Null
     */
    fun searchDotMinecraft(basedir: File2): File2?
    {
        try {
            var d = basedir

            for (i in 0 until 7)
            {
                if (d.contains(".minecraft"))
                    return d

                d = d.parent
            }
        } catch (e: NullPointerException) {
            return null
        }

        return null
    }

    /**
     * 从外部/内部读取配置文件并将内容返回（当外部不可用时会从内部读取）
     * @param externalConfigFile 外部配置文件
     * @return 解码后的配置文件对象
     * @throws ConfigFileNotFoundException 配置文件找不到时
     * @throws FailedToParsingException 配置文件无法解码时
     */
    fun readConfig(externalConfigFile: File2): Map<String, Any>
    {
        try {
            val content: String
            if(!externalConfigFile.exists)
            {
                if(!Environment.IsProduction)
                    throw ConfigFileNotFoundException("config.yml")

                JarFile(Environment.JarFile!!.path).use { jar ->
                    val configFileInZip = jar.getJarEntry("config.yml") ?: throw ConfigFileNotFoundException("config.yml")
                    jar.getInputStream(configFileInZip).use { content = it.readBytes().decodeToString() }
                }
            } else {
                content = externalConfigFile.content
            }
            return Yaml().load(content)
        } catch (e: JSONException) {
            throw FailedToParsingException("配置文件config.yml", "yaml", e.message ?: "")
        }
    }

    /**
     * 从Jar文件内读取语言配置文件（仅图形模式启动时有效）
     * @return 语言配置文件对象
     * @throws ConfigFileNotFoundException 配置文件找不到时
     * @throws FailedToParsingException 配置文件无法解码时
     */
    fun readLangs(): Map<String, String>
    {
        try {
            val content: String
            if (Environment.IsProduction)
                JarFile(Environment.JarFile!!.path).use { jar ->
                    val langFileInZip = jar.getJarEntry("lang.yml") ?: throw ConfigFileNotFoundException("lang.yml")
                    jar.getInputStream(langFileInZip).use { content = it.readBytes().decodeToString() }
                }
            else
                content = (File2(System.getProperty("user.dir")) + "src/main/resources/lang.yml").content

            return Yaml().load(content)
        } catch (e: JSONException) {
            throw FailedToParsingException("语言配置文件lang.yml", "yaml", e.message ?: "")
        }
    }

    /**
     * 获取进程的工作目录
     */
    fun getWorkDirectory(): File2
    {
        return System.getProperty("user.dir").run {
            if(Environment.IsProduction)
                File2(this)
            else
                File2("$this${File.separator}testdir").also { it.mkdirs() }
        }
    }

    /**
     * 获取需要更新的起始目录
     * @throws UpdateDirNotFoundException 当.minecraft目录搜索不到时
     */
    fun getUpdateDirectory(workDir: File2, options: GlobalOptions): File2
    {
        return if(Environment.IsProduction) {
            if (options.basePath != "") Environment.JarFile!!.parent + options.basePath
            else searchDotMinecraft(workDir) ?: throw UpdateDirNotFoundException()
        } else {
            workDir
        }.apply { mkdirs() }
    }

    /**
     * 获取Jar文件所在的目录
     */
    fun getProgramDirectory(workDir: File2): File2
    {
        return if(Environment.IsProduction) Environment.JarFile!!.parent else workDir
    }

    companion object {
        /**
         * 从JavaAgent启动
         */
        @JvmStatic
        fun premain(agentArgs: String?, ins: Instrumentation?)
        {
            val useGraphicsMode = agentArgs != "windowless" && Desktop.isDesktopSupported()
            McPatchClient().run(
                graphicsMode = useGraphicsMode,
                hasStandaloneProgress = false,
                externalConfigFile = null,
                enableLogFile = true,
                disableTheme = false
            )
            Log.info("finished!")
        }

        /**
         * 独立启动
         */
        @JvmStatic
        fun main(args: Array<String>)
        {
            val useGraphicsMode = !(args.isNotEmpty() && args[0] == "windowless") && Desktop.isDesktopSupported()
            McPatchClient().run(
                graphicsMode = useGraphicsMode,
                hasStandaloneProgress = true,
                externalConfigFile = null,
                enableLogFile = true,
                disableTheme = false
            )
            Log.info("finished!")
        }

        /**
         * 从ModLoader启动
         * @return 是否有文件更新，如果有返回true。其它情况返回false
         */
        @JvmStatic
        fun modloader(enableLogFile: Boolean, disableTheme: Boolean): Boolean
        {
            val result = McPatchClient().run(
                graphicsMode = Desktop.isDesktopSupported(),
                hasStandaloneProgress = false,
                externalConfigFile = null,
                enableLogFile = enableLogFile,
                disableTheme = disableTheme
            )
            Log.info("finished!")
            return result
        }
    }
}