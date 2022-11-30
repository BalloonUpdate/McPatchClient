package mcpatch.util

object PathUtils
{
    /**
     * 获取路径字符串中目录路径部分
     *
     * /a/b/c返回/a/b
     * @param path 要处理的路径字符串
     * @return 目录路径部分。如果/没有出现则返回null
     */
    fun getDirPathPart(path: String): String? = path.lastIndexOf("/")
        .run { if (this == -1) null else path.substring(0, this) }

    /**
     * 获取路径字符串中文件名部分
     *
     * /a/b/c返回c
     * @param path 要处理的路径字符串
     * @return 文件名部分
     */
    fun getFileNamePart(path: String): String = path.lastIndexOf("/")
        .run { if (this == -1) path else path.substring(this + 1) }

    /**
     * 获取文件名中的后缀部分
     *
     * a.b返回a
     * @param filename 文件名
     * @return 文件名的后缀部分
     */
    fun getFileSuffix(filename: String): String = filename.lastIndexOf(".")
        .run { if (this == -1) filename else filename.substring(0, this) }
}