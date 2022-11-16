package mcpatch.util

import java.io.IOException
import java.net.URLDecoder
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest

object Environment
{
    /**
     * 版本号
     */
    @JvmStatic
    val Version: String by lazy { Manifest["Version"] ?: "0.0.0" }

    /**
     * 提交记录
     */
    @JvmStatic
    val GitCommit: String by lazy { Manifest["Git-Commit"] ?: "<development>" }

    /**
     * 是否被打包
     */
    @JvmStatic
    val IsProduction: Boolean by lazy { JarFile != null }

    /**
     * 获取当前Jar文件的打包路径，如果是开发环境则返回null
     */
    @JvmStatic
    val JarFile: File2? by lazy {
        val isPackaged = javaClass.getResource("")?.protocol != "file"

        if (!isPackaged)
            return@lazy null

        val url = URLDecoder.decode(javaClass.protectionDomain.codeSource.location.file, "UTF-8")
            .replace("\\", "/")

        return@lazy File2(if (url.endsWith(".class") && "!" in url) {
            val path = url.substring(0, url.lastIndexOf("!"))
            if ("file:/" in path) path.substring(path.indexOf("file:/") + "file:/".length) else path
        } else url)
    }


    /**
     * 读取版本信息（程序打包成Jar后才有效）
     * @return Application版本号，如果为打包成Jar则返回null
     */
    @JvmStatic
    val Manifest: Map<String, String> get() {
        return try {
            OriginManifest.entries.associate { it.key.toString() to it.value.toString() }
        } catch (e: IOException) {
            mapOf()
        }
    }

    @JvmStatic
    val OriginManifest: Attributes by lazy {
        val jar = JarFile ?: throw IOException("Manifest信息获取失败")

        JarFile(jar.path).use { j ->
            j.getInputStream(j.getJarEntry("META-INF/MANIFEST.MF")).use {
                return@lazy Manifest(it).mainAttributes
            }
        }
    }
}