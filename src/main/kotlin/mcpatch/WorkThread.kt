package mcpatch

import com.lee.bsdiff.BsPatch
import mcpatch.core.PatchFileReader
import mcpatch.data.GlobalOptions
import mcpatch.data.ModificationMode
import mcpatch.exception.DoNotHideFileException
import mcpatch.exception.InvalidVersionException
import mcpatch.exception.InvalidVersionNameException
import mcpatch.exception.PatchCorruptedException
import mcpatch.extension.FileExtension.bufferedInputStream
import mcpatch.extension.FileExtension.bufferedOutputStream
import mcpatch.extension.StreamExtension.copyAmountTo1
import mcpatch.gui.ChangeLogs
import mcpatch.gui.McPatchWindow
import mcpatch.localization.LangNodes
import mcpatch.localization.Localization
import mcpatch.logging.FileHandler
import mcpatch.logging.Log
import mcpatch.server.MultipleServers
import mcpatch.util.*
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import javax.swing.JOptionPane

class WorkThread(
    val window: McPatchWindow?,
    val options: GlobalOptions,
    val updateDir: File2,
    val progDir: File2,
) : Thread() {
    var downloadedVersionCount = 0

    /**
     * McPatch工作线程
     */
    override fun run()
    {
        if (!options.quietMode)
            window?.show()

        MultipleServers(options).use { servers ->
            val currentVersionFile = progDir + options.verionFile
            val (versionFileContent, encoded) = if (currentVersionFile.exists) tryDecodeVersionFile(currentVersionFile.content) else Pair("", false)

            val allVersions = servers.fetchText(options.versionsFileName).split("\n").filter { it.isNotEmpty() }
            val newestVersion = allVersions.lastOrNull()
            val currentVersion = if (currentVersionFile.exists) versionFileContent else null
            val downloadedVersions = mutableListOf<String>()

            Log.debug("all versions: ")
            allVersions.forEach { Log.debug("  - $it") }
            Log.info("current version: $currentVersion, newest version: $newestVersion")

            if (newestVersion == null)
                throw InvalidVersionException("服务端版本号列表为空，更新失败！")

            // 如果当前版本和最新版本不一致
            if (currentVersion != newestVersion)
            {
                // 更新UI
                window?.labelText = Localization[LangNodes.fetch_metadata]

                // 是否是合法的版本号
                if (currentVersion !in allVersions && currentVersion != null)
                {
                    if (!options.autoRestartVersion)
                        throw InvalidVersionException("当前客户端版本号 $currentVersion 不在服务端的版本号列表里，无法确定版本前后关系，更新失败！")

                    Log.info("restarted the version")
                }

                // 收集落后的版本
                val position = allVersions.indexOf(currentVersion)
                val missingVersions = allVersions.drop(if (position == -1) 0 else position + 1)
                downloadedVersions.addAll(missingVersions)

                Log.info("missing versions: $missingVersions")

                // 收集到的更新记录
                val changeLogs = mutableListOf<Pair<String, String>>()

                try {
                    // 依次下载缺失的版本
                    for (version in missingVersions)
                    {
                        try {
                            Log.openTag(version)
                            val showWindow = window != null && options.quietMode

                            // 延迟打开窗口
                            if (showWindow)
                                window!!.show()

                            window?.labelText = "正在下载资源更新包 $version"

                            // 下载更新包
                            val patchFile = downloadPatchFile("$version.mcpatch.zip", version, servers)

                            // 读取文件
                            val reader = PatchFileReader(version, patchFile)
                            val meta = reader.meta

                            // 不能更新自己
                            val jarPath = Environment.JarFile
                            if (jarPath != null)
                            {
                                val relativePath = jarPath.relativizedBy(updateDir)

                                if (meta.oldFiles.remove(relativePath))
                                    Log.warn("skiped the old file $relativePath, because it is not allowed to update the McPatchClient execuable file itself")

                                if (meta.newFiles.removeIf { it.path == relativePath })
                                    Log.warn("skiped the new file $relativePath, because it is not allowed to update the McPatchClient execuable file itself")
                            }

                            window?.labelText = "正在解压更新包 $version"

                            // 输出日志
                            meta.moveFiles.forEach { Log.debug("move files: ${it.from} => ${it.to}") }
                            meta.oldFiles.forEach { Log.debug("old files: $it") }
                            meta.oldFolders.forEach { Log.debug("old dirs:  $it") }
                            meta.newFiles.forEach { Log.debug("new files: $it") }
                            meta.newFolders.forEach { Log.debug("new dirs:  $it") }

                            // 不能更新日志文件
                            val logFile = (Log.handlers.firstOrNull { it is FileHandler } as FileHandler?)?.logFile
                            var logFileUpdated = false
                            logFileUpdated = logFileUpdated or meta.moveFiles.removeIf { (updateDir + it.from) == logFile || (updateDir + it.to) == logFile }
                            logFileUpdated = logFileUpdated or meta.oldFiles.removeIf { (updateDir + it) == logFile }
                            logFileUpdated = logFileUpdated or meta.newFiles.removeIf { (updateDir + it.path) == logFile }

                            if (logFileUpdated)
                                Log.warn("Do not try to update the logging file of McPatchClient!")

                            // 处理移动，删除和创建目录
                            meta.moveFiles.forEach {
                                val from = updateDir + it.from
                                val to = updateDir + it.to
                                if (from.exists)
                                    from.move(to)
                            }
                            meta.oldFiles.map { (updateDir + it) }.forEach { it.delete() }
                            meta.oldFolders.map { (updateDir + it) }.forEach { it.delete() }
                            meta.newFolders.map { (updateDir + it) }.forEach { it.mkdirs() }

                            // 处理所有新文件
                            val timer = IntervalTimer(150)
                            val skipped = mutableListOf<String>()

                            for (entry in reader)
                            {
                                val rawFile = updateDir + entry.newFile.path
                                val tempFile = rawFile.parent + (rawFile.name + ".mcpatch-temporal.bin")

                                rawFile.parent.mkdirs()

                                // 更新UI
                                if (window != null && timer.timeout)
                                    window.progressBarText = entry.newFile.path

                                when(entry.mode)
                                {
                                    ModificationMode.Empty -> {
                                        Log.info("Empty: ${entry.newFile.path}")
                                        rawFile.delete()
                                        rawFile.create()
                                    }

                                    ModificationMode.Fill -> {
                                        Log.info("Fill: ${entry.newFile.path}")

                                        // 如果本地文件已经存在，且校验一致，就跳过更新
                                        if (rawFile.exists && HashUtils.sha1(rawFile.file) == entry.newFile.newHash)
                                        {
                                            skipped.add(entry.newFile.path)
                                            continue
                                        }

                                        var time = System.currentTimeMillis() + 300

                                        tempFile.file.bufferedOutputStream().use { dest ->
                                            entry.getInputStream().use { src ->

                                                // 解压数据
                                                src.copyAmountTo1(dest, entry.newFile.rawLength, callback = { copied, total ->
                                                    if (System.currentTimeMillis() - time < 100)
                                                        return@copyAmountTo1
                                                    time = System.currentTimeMillis()
                                                    window?.progressBarValue = ((copied.toFloat() / total.toFloat()) * 1000).toInt()
                                                })
                                            }
                                        }

                                        if (HashUtils.sha1(tempFile.file) != entry.newFile.newHash)
                                            throw PatchCorruptedException(version, "中的文件更新后数据校验不通过 ${entry.newFile.path} (fill)", 0)
                                    }

                                    ModificationMode.Modify -> {
                                        val notFound = !rawFile.exists
                                        val notMatched =
                                            if (notFound) false else HashUtils.sha1(rawFile.file) != entry.newFile.oldHash

                                        // 文件不存在或者校验不匹配
                                        val extraMsg = if (notFound) " (skip because the old file not found)" else
                                            if (notMatched) " (skip because hash not matched)" else ""

                                        Log.info("Modify: ${entry.newFile.path}$extraMsg")

                                        if (extraMsg.isNotEmpty()) {
                                            skipped.add(entry.newFile.path)
                                            continue
                                        }

                                        rawFile.file.bufferedInputStream().use { old ->
                                            entry.getInputStream().use { patch ->
                                                tempFile.file.bufferedOutputStream().use { result ->
                                                    BsPatch().bspatch(old, patch, result, old.available(), entry.newFile.rawLength.toInt())
                                                }
                                            }
                                        }

                                        if (HashUtils.sha1(tempFile.file) != entry.newFile.newHash)
                                            throw PatchCorruptedException(version, "中的文件更新后数据校验不通过 ${entry.newFile.path} (modify)", 0)
                                    }
                                }
                            }

                            window?.labelText = "正在合并更新数据，请不要关闭程序"

                            // 合并临时文件
                            for ((index, newFile) in meta.newFiles.withIndex())
                            {
                                val rawFile = updateDir + newFile.path
                                val tempFile = rawFile.parent + (rawFile.name + ".mcpatch-temporal.bin")

                                if ((newFile.mode == ModificationMode.Fill || newFile.mode == ModificationMode.Modify)
                                    && newFile.path !in skipped)
                                {
                                    tempFile.move(rawFile)
                                }

                                if (timer.timeout)
                                {
                                    window?.progressBarText = "$index/${meta.newFiles.size}"
                                    window?.progressBarValue = index * 1000 / meta.newFiles.size
                                }
                            }

                            window?.labelText = "正在更新版本号"

                            // 更新版本号
                            try {
                                currentVersionFile.content = tryEncodeVersionFile(version, encoded)
                            } catch (e: FileNotFoundException) {
                                throw DoNotHideFileException(currentVersionFile)
                            }

                            window?.labelText = "正在做最后的清理工作"

                            // 删除更新包
                            patchFile.delete()

                            window?.labelText = "更新完成"

                            // 处理更新记录
                            if (window != null && options.showChangelogs)
                            {
                                changeLogs.add(Pair(version, meta.changeLogs.trim()))
                            } else {
                                val content = meta.changeLogs.trim()
                                Log.info("========== $version ==========")
                                if (content.isNotEmpty())
                                {
                                    Log.info(content)
                                    Log.info("")
                                }
                            }

                            // 隐藏窗口
                            if (showWindow)
                                window!!.hide()
                        } finally {
                            Log.closeTag()
                        }
                    }

                } finally {
                    if (changeLogs.isNotEmpty())
                    {
                        val content = changeLogs.joinToString("\n\n\n") { cl ->
                            val title = cl.first
                            val content = cl.second.ifEmpty { "已更新" }
                            "========== $title ==========\n$content"
                        }

                        val cl = ChangeLogs()
                        cl.titleText = "更新记录"
                        cl.contentText = content
                        if (options.autoCloseChangelogs > 0)
                            cl.setAutoClose(options.autoCloseChangelogs)
                        cl.waitForClose()
                    }
                }
            }

            // 提示没有更新
            if (window != null && downloadedVersions.isEmpty() && options.showFinishMessage && !options.quietMode)
            {
                val title = Localization[LangNodes.finish_title_no_update]
                val content = Localization[LangNodes.finish_content_no_update]
                JOptionPane.showMessageDialog(null, content, title, JOptionPane.INFORMATION_MESSAGE)
            }

            // 输出一些调试信息
            if (downloadedVersions.isNotEmpty())
                Log.info("successfully applied these versions：$downloadedVersions")
            else
                Log.info("no missing versions and all files is up-to-date!")

            Log.info("continue to start Minecraft!")

            downloadedVersionCount = downloadedVersions.size
        }
    }

    /**
     * 下载patch文件
     * @param relativePath 要下载的文件的相对路径
     * @param version 版本号
     * @param servers 可用服务器源
     * @return 下载好的问题
     */
    private fun downloadPatchFile(relativePath: String, version: String, servers: MultipleServers): File2
    {
        val tempFile = File2(File.createTempFile("mcpatch-$version", ".zip"))
        val sampler = SpeedSampler(3000)
        var time = System.currentTimeMillis()

        servers.downloadFile(relativePath, tempFile) { packageLength, bytesReceived, lengthExpected ->
            if (window == null)
                return@downloadFile

            sampler.feed(packageLength.toInt())

            // 每隔500年更新一次ui
            if (System.currentTimeMillis() - time < 200)
                return@downloadFile
            time = System.currentTimeMillis()

            val progress = bytesReceived / lengthExpected.toFloat() * 100
            val progressText = String.format("%.1f", progress)
            val currentBytes = MiscUtils.convertBytes(bytesReceived)
            val totalBytes = MiscUtils.convertBytes(lengthExpected)
            val speedText = MiscUtils.convertBytes(sampler.speed)

            window.progressBarText = "$progressText%  -  $currentBytes/$totalBytes   -   $speedText/s"
            window.progressBarValue = (progress * 10).toInt()
        }

        return tempFile
    }

    private fun tryDecodeVersionFile(text: String): Pair<String, Boolean>
    {
        if (!text.startsWith(":"))
            return Pair(text, false)

        try {
            return Pair(Base64.getDecoder().decode(text.drop(1)).decodeToString(), true)
        } catch (e: IllegalArgumentException) {
            throw InvalidVersionNameException()
        }
    }

    private fun tryEncodeVersionFile(text: String, encode: Boolean): String
    {
        if (!encode)
            return text

        return ":" + Base64.getEncoder().encodeToString(text.encodeToByteArray())
    }
}