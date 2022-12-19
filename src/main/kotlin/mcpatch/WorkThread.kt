package mcpatch

import com.lee.bsdiff.BsPatch
import mcpatch.data.*
import mcpatch.exception.InvalidVersionException
import mcpatch.exception.InvalidVersionNameException
import mcpatch.exception.PatchCorruptedException
import mcpatch.extension.FileExtension.bufferedInputStream
import mcpatch.extension.FileExtension.bufferedOutputStream
import mcpatch.extension.StreamExtension.actuallySkip
import mcpatch.extension.StreamExtension.copyAmountTo
import mcpatch.gui.McPatchWindow
import mcpatch.localization.LangNodes
import mcpatch.localization.Localization
import mcpatch.logging.Log
import mcpatch.server.MultipleAvailableServers
import mcpatch.util.*
import org.apache.tools.bzip2.CBZip2InputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.*
import javax.swing.JOptionPane

class WorkThread(
    val window: McPatchWindow?,
    val options: GlobalOptions,
    val updateDir: File2,
    val progDir: File2,
): Thread() {
    var downloadedVersionCount = 0

    /**
     * McPatch工作线程
     */
    override fun run()
    {
        if (!options.quietMode)
            window?.show()

        MultipleAvailableServers(options).use { servers ->
            val currentVersionFile = progDir + options.verionFile
            val (versionFileContent, encoded) = tryDecodeVersionFile(currentVersionFile.content)

            val allVersions = servers.fetchText("mc-patch-versions.txt").split("\n").filter { it.isNotEmpty() }
            val newestVersion = allVersions.lastOrNull() ?: "unknown"
            val currentVersion = if (currentVersionFile.exists) versionFileContent else "none"
            val downloadedVersions: MutableList<Pair<String, VersionMetadata>> = mutableListOf()

            Log.debug("all versions: ")
            allVersions.forEach { Log.debug("  - $it") }
            Log.info("current version: $currentVersion, newest version: $newestVersion")

            // 如果当前版本和最新版本不一致
            if (newestVersion != "unknown" && currentVersion != newestVersion)
            {
                // 更新UI
                window?.labelText = Localization[LangNodes.fetch_metadata]

                // 是否是合法的版本号
                if (currentVersion !in allVersions && currentVersion != "none")
                    throw InvalidVersionException(currentVersion)

                // 收集落后的版本
                val position = allVersions.indexOf(currentVersion)
                val missingVersions = allVersions.drop(if (position == -1) 0 else position + 1)

                Log.info("missing versions: $missingVersions")

                // 获取落后的版本的元数据
                downloadedVersions.addAll(missingVersions.map { version ->
                    val meta = servers.fetchJsonObject("$version.mc-patch.json", "版本Meta文件 $version")
                    version to VersionMetadata(meta)
                })

                // 依次下载缺失的版本
                for ((version, meta) in downloadedVersions)
                {
                    try {
                        Log.openTag(version)
                        val showWindow = window != null && options.quietMode && meta.newFiles.isNotEmpty()

                        // 延迟打开窗口
                        if (showWindow)
                            window!!.show()

                        val jarPath = Environment.JarFile
                        if (jarPath != null)
                        {
                            val relativePath = jarPath.relativizedBy(updateDir)

                            if (meta.oldFiles.remove(relativePath))
                                Log.warn("skiped the old file $relativePath, because it is not allowed to update the McPatchClient execuable file itself")

                            if (meta.newFiles.removeIf { it.path == relativePath })
                                Log.warn("skiped the new file $relativePath, because it is not allowed to update the McPatchClient execuable file itself")
                        }

                        meta.oldFiles.forEach { Log.debug("old files: $it") }
                        meta.oldFolders.forEach { Log.debug("old dirs:  $it") }
                        meta.newFiles.forEach { Log.debug("new files: $it") }
                        meta.newFolders.forEach { Log.debug("new dirs:  $it") }

                        // 删除旧文件和旧目录，还有创建新目录
                        meta.oldFiles.map { (updateDir + it) }.forEach { it.delete() }
                        meta.oldFolders.map { (updateDir + it) }.forEach { it.delete() }
                        meta.newFolders.map { (updateDir + it) }.forEach { it.mkdirs() }

                        // 下载文件
                        if (meta.newFiles.isNotEmpty())
                        {
                            val patchFile = downloadPatch(meta, version, servers)
                            applyPatch(meta, version, updateDir, patchFile)
                            patchFile.delete()
                        }

                        // 更新版本号
                        currentVersionFile.content = tryEncodeVersionFile(version, encoded)

                        // 显示更新记录
                        if (window != null && options.showChangelogs)
                        {
                            val logs = meta.changeLogs.trim()
                            val title = if (logs.isEmpty()) "" else "已更新至版本 $version"
                            val content = logs.ifEmpty { "已更新至版本 $version" }
                            JOptionPane.showMessageDialog(null, content, title, JOptionPane.INFORMATION_MESSAGE)
                        } else {
                            val content = meta.changeLogs.trim().ifEmpty { "No change logs for the version $version" }
                            Log.info("========== changelogs for version $version ==========")
                            Log.info(content.prependIndent("|"))
                            Log.info("")
                        }

                        // 隐藏窗口
                        if (showWindow)
                            window!!.hide()
                    } finally {
                        Log.closeTag()
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
                Log.info("successfully applied these versions：${downloadedVersions.map { it.first }}")
            else
                Log.info("no missing versions and all files is up-to-date!")

            Log.info("continue to start Minecraft!")

            downloadedVersionCount = downloadedVersions.size
        }
    }

    /**
     * 下载patch文件
     * @param meta 版本的元信息
     * @param version patch对应的版本号
     * @param servers 可用服务器源
     */
    private fun downloadPatch(meta: VersionMetadata, version: String, servers: MultipleAvailableServers): File2
    {
        val tempFile = File2(File.createTempFile("mc-patch-$version", ".bin"))

        val sampler = SpeedSampler(3000)
        var time = System.currentTimeMillis()

        servers.downloadFile("$version.mc-patch.bin", tempFile, meta.patchLength) { packageLength, bytesReceived, lengthExpected ->
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

            window.labelText = "正在下载资源更新包 $version"
            window.progressBarText = "$progressText%  -  $currentBytes/$totalBytes   -   $speedText/s"
            window.progressBarValue = (progress * 10).toInt()
        }

        Log.debug("calculating the hash of the patch file")

        // 校验patch文件
        window?.labelText = "正在校验资源更新包 $version"
        window?.progressBarValue = 0
        window?.progressBarText = ""

        fun onProgress(current: Long, total: Long)
        {
            window?.progressBarText = "${MiscUtils.convertBytes(current)} / ${MiscUtils.convertBytes(total)}"
            window?.progressBarValue = (current * 1000 / total).toInt()
        }

        val corrupted = tempFile.length != meta.patchLength
                || HashUtils.sha1(tempFile.file) { current, total -> onProgress(current, total) } != meta.patchHash
        if (corrupted)
            throw PatchCorruptedException(version, "二进制数据")

        return tempFile
    }

    /**
     * 合并patch文件
     * @param meta 版本的元信息
     * @param version patch对应的版本号
     * @param dir 更新的其实目录
     * @param patchFile patch文件
     */
    private fun applyPatch(meta: VersionMetadata, version: String, dir: File2, patchFile: File2)
    {
        val timer = IntervalTimer(150)

        window?.labelText = "正在应用更新包 $version"

        patchFile.file.bufferedInputStream().use { patch ->
            val pointer = ObjectLong(0)
            val uiUpdatingTimer = IntervalTimer(150)

            val skipped = mutableListOf<String>()

            // 解压到临时文件里
            for (newFile in meta.newFiles)
            {
                val rawFile = dir + newFile.path
                val tempFile = rawFile.parent + (rawFile.name + ".mc-patch-temporal.bin")

                rawFile.parent.mkdirs()

                if (extractPatch(version, newFile, rawFile, tempFile, patch, pointer, uiUpdatingTimer))
                    skipped.add(newFile.path)
            }

            // 应用临时文件
            window?.labelText = "正在进行收尾工作"

            for ((index, newFile) in meta.newFiles.withIndex())
            {
                val rawFile = dir + newFile.path
                val tempFile = rawFile.parent + (rawFile.name + ".mc-patch-temporal.bin")

                if ((newFile.mode == ModificationMode.Fill || newFile.mode == ModificationMode.Modify)
                    && newFile.path !in skipped)
                {
                    // 合回最终文件，删除临时文件
                    tempFile.copy(rawFile)
                    tempFile.delete()
                }

                if (timer.timeout)
                {
                    window?.progressBarText = "$index/${meta.newFiles.size}"
                    window?.progressBarValue = index * 1000 / meta.newFiles.size
                }
            }
        }

        patchFile.delete()
    }

    /**
     * 解压patch
     * @param version patch对应的版本号
     * @param newFile 新文件的元信息
     * @param rawFile 要被更新的原始文件
     * @param tempFile 先解压到临时文件里
     * @param patch patch文件的输入流
     * @param pointer patch文件的读取指针
     * @param uiUpdatingTimer UI更新计时器
     * @return 是否跳过了更新
     */
    fun extractPatch(
        version: String,
        newFile: NewFile,
        rawFile: File2,
        tempFile: File2,
        patch: InputStream,
        pointer: ObjectLong,
        uiUpdatingTimer: IntervalTimer,
    ): Boolean {
        when (newFile.mode)
        {
            ModificationMode.Empty -> {
                Log.info("Empty: ${newFile.path}")

                rawFile.delete()
                rawFile.create()
            }

            ModificationMode.Fill -> {
                Log.info("Fill: ${newFile.path}")

                // 添加UI更新间隔
                if (window != null && uiUpdatingTimer.timeout)
                    window.progressBarText = newFile.path

                // 如果本地文件已经存在，且hash一致，就跳过更新
                if (rawFile.exists && HashUtils.sha1(rawFile.file) == newFile.newFileHash)
                    return true

                tempFile.file.bufferedOutputStream().use { temp ->
                    patch.actuallySkip(newFile.blockOffset - pointer.value)

                    // 拿到解压好的原始数据
                    ByteArrayOutputStream().use { decompressed ->
                        val bzip = CBZip2InputStream(patch)
                        var time = System.currentTimeMillis() + 300 // 300ms后再开始更新
                        bzip.copyAmountTo(decompressed, 1024 * 1024, newFile.rawLength) { copied, total ->
                            if (System.currentTimeMillis() - time < 100)
                                return@copyAmountTo
                            time = System.currentTimeMillis()
                            window?.progressBarValue = ((copied.toFloat() / total.toFloat()) * 1000).toInt()
                        }

                        // 检查解压后的二进制块
                        if (HashUtils.sha1(decompressed.toByteArray()) != newFile.newFileHash)
                            throw PatchCorruptedException(version, "解压后的二进制数据(${newFile.path})")

                        decompressed.writeTo(temp)
                    }

                    pointer.value = newFile.blockOffset + newFile.blockLength
                }
            }

            ModificationMode.Modify -> {
                val notFound = !rawFile.exists
                val notMatched = if (notFound) false else HashUtils.sha1(rawFile.file) != newFile.oldFileHash

                // 文件不存在
                val extraMsg = if (notFound) " (skip because the old file not found)" else
                    if (notMatched) " (skip because hash not matched)" else ""

                Log.info("Modify: ${newFile.path}$extraMsg")

                if (extraMsg.isNotEmpty())
                    return true

                // 添加UI更新间隔
                if (window != null && uiUpdatingTimer.timeout)
                    window.progressBarText = newFile.path

                // 将修补好的文件输出到临时文件里
                rawFile.file.bufferedInputStream().use { old ->
                    tempFile.file.bufferedOutputStream().use { temp ->
                        patch.skip(newFile.blockOffset - pointer.value)

                        // 拿到解压好的原始数据
                        ByteArrayOutputStream().use { decompressed ->
                            val bzip = CBZip2InputStream(patch)
                            var time = System.currentTimeMillis() + 300 // 300ms后再开始更新
                            bzip.copyAmountTo(decompressed, 1024 * 1024, newFile.rawLength) { copied, total ->
                                if (System.currentTimeMillis() - time < 100)
                                    return@copyAmountTo
                                time = System.currentTimeMillis()
                                window?.progressBarValue = ((copied.toFloat() / total.toFloat()) * 1000).toInt()
                            }

                            val decompressedBlock = decompressed.toByteArray()

                            // 检查解压后的二进制块
                            if (HashUtils.sha1(decompressedBlock) != newFile.patchFileHash)
                                throw PatchCorruptedException(version, "解压后的二进制数据(${newFile.path})")

                            ByteArrayInputStream(decompressedBlock).use { patchStream ->
                                BsPatch().bspatch(old, patchStream, temp, old.available(), newFile.rawLength.toInt())
                            }
                        }

                        pointer.value = newFile.blockOffset + newFile.blockLength
                    }

                    // 检查合并后的文件
                    if (HashUtils.sha1(tempFile.file) != newFile.newFileHash)
                        throw PatchCorruptedException(version, "合并后的文件数据(${newFile.path})")
                }
            }
        }

        return false
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