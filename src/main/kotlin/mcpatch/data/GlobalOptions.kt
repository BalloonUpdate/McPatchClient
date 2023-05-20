package mcpatch.data

import mcpatch.exception.ConfigFieldException

/**
 * 应用程序全局选项
 */
data class GlobalOptions(
    /**
     * 服务端的更新用目录地址，用来获取服务端的文件并计算差异
     */
    val server: List<String>,

    /**
    * 客户端的 UserAgent，用于鉴权和分流
    */
    val clientUserAgent: String,

    /**
     * 是否在运行结束时显示提示框
     */
    val showFinishMessage: Boolean,

    /**
     * 是否显示更新记录
     */
    val showChangelogs: Boolean,

    /**
     * 存储版本号的文件
     */
    val verionFile: String,

    /**
     * 更新的基本路径
     */
    val basePath: String,

    /**
     * 是否开启不抛异常模式，以避免在更新失败时，不打断 Minecraft 游戏的启动
     */
    val noThrowing: Boolean,

    /**
     * 安静模式，仅在有文件需要被更新时显示下载窗口
     */
    val quietMode: Boolean,

    /**
     * 全局 http 连接超时（单位毫秒）
     */
    val httpConnectTimeout: Int,

    /**
     * 全局 http 响应超时，也叫 TTFB（单位毫秒）
     */
    val httpResponseTimeout: Int,

    /**
     * 是否禁用主题
     */
    val disableTheme: Boolean,

    /**
     * 重试次数
     */
    val retryTimes: Int,

    /**
     * 当遇到不存在的版本时是否自动从头开始下载所有版本
      */
    val autoRestartVersion: Boolean,

    /**
     * 是否自动关闭更新记录窗口
     */
    val autoCloseChangelogs: Int,
) {
    companion object {
        /**
         * 从 Map 里创建一个 GlobalOptions
         */
        @JvmStatic
        fun CreateFromMap(map: Map<String, Any>): GlobalOptions
        {
            val serverAsList = getOption<List<String>>(map, "server")
            val serverAsString = getOption<String>(map, "server")
            val server = serverAsList ?: listOf(serverAsString ?: throw ConfigFieldException("server"))


            return GlobalOptions(
                server = server,

                clientUserAgent = (getOption<String>(map, "client-UserAgent") ?: "McPatchClient"),
                showFinishMessage = getOption<Boolean>(map, "show-finish-message") ?: true,
                showChangelogs = getOption<Boolean>(map, "show-changelogs-message") ?: true,
                verionFile = getOption<String>(map, "version-file") ?: "mc-patch-version.txt",
                basePath = getOption<String>(map, "base-path") ?: "",
                noThrowing = getOption<Boolean>(map, "no-throwing") ?: false,
                quietMode = getOption<Boolean>(map, "quiet-mode") ?: false,
                httpConnectTimeout = getOption<Int>(map, "http-connect-timeout") ?: 3000,
                httpResponseTimeout = getOption<Int>(map, "http-response-timeout") ?: 2000,
                disableTheme = getOption<Boolean>(map, "disable-theme") ?: true,
                retryTimes = getOption<Int>(map, "retry-times") ?: 5,
                autoRestartVersion = getOption<Boolean>(map, "auto-restart-version") ?: true,
                autoCloseChangelogs = getOption<Int>(map, "changelogs-auto-close") ?: 0,
            )
        }

        /**
         * 从配置文件里读取东西，并校验
         */
        inline fun <reified Type> getOption(config: Map<String, Any>, key: String): Type?
        {
            return if(key in config && config[key] != null && config[key] is Type) config[key] as Type else null
        }
    }
}
