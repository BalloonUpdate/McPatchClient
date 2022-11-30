package mcpatch

import mcpatch.classloader.HotUpdateClassLoader
import mcpatch.logging.Log
import mcpatch.util.*
import mcpatch.exception.*
import mcpatch.extension.RuntimeExtension.usedMemory
import mcpatch.logging.ConsoleHandler
import mcpatch.logging.FileHandler
import mcpatch.util.DialogUtils
import java.awt.Desktop
import java.io.File
import java.lang.Exception
import java.lang.instrument.Instrumentation
import java.lang.reflect.InvocationTargetException
import java.util.jar.JarFile
import java.util.jar.Manifest

class McPatchClient
{
    /**
     * McPatchClient主线程
     * @param graphicsMode 是否以图形模式启动（桌面环境通常以图形模式启动，安卓环境通常不以图形模式启动）
     * @param hasStandaloneProgress 程序是否拥有独立的进程。从JavaAgent参数启动没有独立进程，双击启动有独立进程（java -jar xx.jar也属于独立启动）
     * @param forceExternalConfig 可选的优先外部配置文件路径，如果为空则使用 progDir/config.yml
     * @param enableLogFile 是否写入日志文件
     * @param disableTheme 是否禁用主题
     */
    fun main(
        graphicsMode: Boolean,
        hasStandaloneProgress: Boolean,
        forceExternalConfig: File2?,
        enableLogFile: Boolean,
        disableTheme: Boolean,
    ): Boolean {
        val appVersion = "McPatchClient ${Environment.Version} (${Environment.GitCommit})"

        try {
            val workDir = getWorkDirectory()
            val progDir = getProgramDirectory(workDir)
            val hotupdate = progDir + (if (Environment.IsProduction) (PathUtils.getFileSuffix(Environment.JarFile!!.name) + ".hotupdate.jar") else "hotupdate.jar")

            // 初始化日志系统
            if (enableLogFile)
                Log.addHandler(FileHandler(Log, progDir + (if (graphicsMode) "mc-patch.log" else "mc-patch.log.txt")))

            val consoleLogLevel = if (Environment.IsProduction)
                (if (graphicsMode || !enableLogFile) Log.LogLevel.DEBUG else Log.LogLevel.INFO)
            else
                Log.LogLevel.INFO
            Log.addHandler(ConsoleHandler(Log, consoleLogLevel))
            if (!hasStandaloneProgress)
                Log.openTag("McPatchClient")

            for (i in 0 until 10)
            {
                try {
                    // 从本地启动
                    if (!hotupdate.exists)
                        return HotupdateRun().hotupdateRun(
                            graphicsMode, hasStandaloneProgress, disableTheme,
                            workDir.file, progDir.file, hotupdate.file, forceExternalConfig?.file
                        )

                    // 从热更新启动
                    val hotupdateVersion = JarFile(hotupdate.path).use { j ->
                        j.getInputStream(j.getJarEntry("META-INF/MANIFEST.MF")).use { i ->
                            val ma = Manifest(i).mainAttributes.entries.associate { it.key.toString() to it.value.toString() }
                            ma["Version"] as String
                        }
                    }
                    Log.openTag("HotUpdate")
                    Log.info("start from hotupdate ${hotupdate.path} ($hotupdateVersion)")

                    HotUpdateClassLoader().saveClassLoader().use { classLoader ->
                        classLoader.addJar(hotupdate)
                        classLoader.addNonHotUpdateFilter(Regex("^org\\.yaml\\.snakeyaml.*$"))
                        classLoader.addNonHotUpdateFilter(Regex("^mcpatch\\.logging.*$"))

                        val clazz = classLoader.loadClass("mcpatch.HotupdateRun")
                        val run = clazz.declaredMethods.first { it.name == "hotupdateRun" }
                        val mpc = clazz.getConstructor().newInstance()
                        try {
                            run.invoke(mpc,
                                graphicsMode, hasStandaloneProgress, disableTheme,
                                workDir.file, progDir.file, hotupdate.file, forceExternalConfig?.file
                            )
                        } catch (e: InvocationTargetException) {
                            Log.error("InvocationTargetException:")
                            Log.error(e.targetException.stackTraceToString())
                            throw e.targetException
                        }
                    }
                    Log.closeTag()

                    break
                } catch (e: Exception) {
                    if (e.javaClass.simpleName != "HotUpdateSignalException")
                        throw e

                    if (!Environment.IsProduction)
                        DialogUtils.info("", "即将进行热更新文件替换")

                    val temporal = File2(e.javaClass.getDeclaredMethod("getTemporal").invoke(e) as File)
                    temporal.copy(hotupdate)
                    temporal.delete()
                    Log.info("replace ${temporal.path} => ${hotupdate.path}")
                }

                if (i >= 3)
                    throw RuntimeException("连续触发热更新次数达到10次的上限，请向开发者报告此问题")
            }
        } catch (e: UpdateDirNotFoundException) {
            if (graphicsMode)
                DialogUtils.error(appVersion, e.message ?: "<No Exception Message>")
        } catch (e: ConfigFileNotFoundException) {
            if (graphicsMode)
                DialogUtils.error(appVersion, e.message ?: "<No Exception Message>")
        } catch (e: FailedToParsingException) {
            if (graphicsMode)
                DialogUtils.error(appVersion, e.message ?: "<No Exception Message>")
        } catch (e: Throwable) {
            Log.error(e.stackTraceToString())

            if (graphicsMode)
                DialogUtils.error(appVersion, e.stackTraceToString())
        } finally {
            Log.info("RAM: " + MiscUtils.convertBytes(Runtime.getRuntime().usedMemory()))
        }

        return false
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
            McPatchClient().main(
                graphicsMode = useGraphicsMode,
                hasStandaloneProgress = false,
                forceExternalConfig = null,
                enableLogFile = true,
                disableTheme = false,
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
            McPatchClient().main(
                graphicsMode = useGraphicsMode,
                hasStandaloneProgress = true,
                forceExternalConfig = null,
                enableLogFile = true,
                disableTheme = false,
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
            val result = McPatchClient().main(
                graphicsMode = Desktop.isDesktopSupported(),
                hasStandaloneProgress = false,
                forceExternalConfig = null,
                enableLogFile = enableLogFile,
                disableTheme = disableTheme,
            )
            Log.info("finished!")
            return result
        }
    }
}