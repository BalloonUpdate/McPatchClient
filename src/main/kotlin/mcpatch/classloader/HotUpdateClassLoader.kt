package mcpatch.classloader

import mcpatch.util.File2
import java.io.Closeable
import java.net.URLClassLoader
import java.util.jar.JarFile

class HotUpdateClassLoader : URLClassLoader(arrayOf(), HotUpdateClassLoader::class.java.classLoader), Closeable
{
    private val jars = mutableListOf<JarRegistry>()
    private val classesLoaded = mutableMapOf<String, Class<*>>()
    private var previousClassLoader: ClassLoader? = null
    private val nonHotUpdateFilters = mutableListOf<Regex>()

    fun addJar(jar: File2)
    {
        val connection = JarFile(jar.path)
        jars.add(JarRegistry(jar, connection))
    }

    fun addNonHotUpdateFilter(reg: Regex)
    {
        nonHotUpdateFilters.add(reg)
    }

    override fun loadClass(name: String): Class<*>
    {
        return classesLoaded[name] ?: run {
            val classPath = name.replace(".", "/") + ".class"

            if (!nonHotUpdateFilters.any { it.matches(name) })
            {
                for (jar in jars)
                {
                    val entry = jar.connection.getJarEntry(classPath) ?: continue

//                    println("load: $name")

                    jar.connection.getInputStream(entry).use { inputStream ->
                        val data = inputStream.readBytes()
                        val clazz = defineClass(name, data, 0, data.size)
                        classesLoaded[name] = clazz
                        return clazz
                    }
                }
            }

            return super.loadClass(name)
        }
    }

    override fun close()
    {
        for (jar in jars)
            jar.connection.close()

        jars.clear()

        if (previousClassLoader != null)
            restoreClassLoader()
    }

    fun saveClassLoader(): HotUpdateClassLoader
    {
        previousClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this
        return this
    }

    fun restoreClassLoader()
    {
        if (previousClassLoader != null)
        {
            Thread.currentThread().contextClassLoader = previousClassLoader
            previousClassLoader = null
        }
    }

    private data class JarRegistry(
        val file: File2,
        val connection: JarFile,
    )
}