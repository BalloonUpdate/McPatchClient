package mcpatch.data

import mcpatch.exception.ConfigFieldException

/**
 * 应用程序全局选项
 */
data class GlobalOptions(
    /**
     * 更新服务器的地址列表，多个地址之间互为备份
     */
    val server: List<String>,

    /**
    * 客户端的 UserAgent，用于鉴权和分流
    */
    val clientUserAgent: String,

    /**
     * 自定义Http协议头
     */
    val httpHeaders: Map<String, String>,

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

    /**
     * 多线程下载时使用的线程数，仅对http源有效，且需要服务端支持断点续传功能
     */
    val concurrentThreads: Int,

    /**
     * 多线程下载时每个文件块的大小
     */
    val concurrentBlockSize: Int,

    /**
     * 服务端上的版本号的文件名，用于支持灰度更新
     */
    val versionsFileName: String,

    /**
     * 是否忽略HTTPS源的SSL证书验证
     */
    val ignoreHttpsCertificate: Boolean,

    /**
     * HTTP/WEBDAV 源的默认文件大小，当服务器未报告文件大小时则假定文件为这个大小
     */
    val httpFallbackFileSize: Long,
) {
    companion object {
        /**
         * 从 Map 里创建一个 GlobalOptions
         */
        @JvmStatic
        fun CreateFromMap(map: Map<String, Any>): GlobalOptions {
            val serverAsList = getOption<List<String>>(map, "server")
            val serverAsString = getOption<String>(map, "server")
            val server = serverAsList ?: listOf(serverAsString ?: throw ConfigFieldException("server"))


            return GlobalOptions(
                server = server,
                clientUserAgent = getOption(map, "client-UserAgent") ?: "",
                httpHeaders = getOption<Map<String, String>>(map, "http-headers")?.mapValues { (it.value as Any).toString() } ?: mapOf(),
                showFinishMessage = getOption(map, "show-finish-message") ?: true,
                showChangelogs = getOption(map, "show-changelogs-message") ?: true,
                verionFile = getOption(map, "version-file") ?: "mc-patch-version.txt",
                basePath = getOption(map, "base-path") ?: "",
                noThrowing = getOption(map, "no-throwing") ?: false,
                quietMode = getOption(map, "quiet-mode") ?: false,
                httpConnectTimeout = getOption(map, "http-connect-timeout") ?: 3000,
                httpResponseTimeout = getOption(map, "http-response-timeout") ?: 2000,
                disableTheme = getOption(map, "disable-theme") ?: true,
                retryTimes = getOption(map, "retry-times") ?: 5,
                autoRestartVersion = getOption(map, "auto-restart-version") ?: true,
                autoCloseChangelogs = getOption(map, "changelogs-auto-close") ?: 0,
                concurrentThreads = getOption(map, "concurrent-threads") ?: 4,
                concurrentBlockSize = getOption(map, "concurrent-block-size") ?: 4194304,
                versionsFileName = getOption(map, "server-versions-file-name") ?: "versions.txt",
                ignoreHttpsCertificate = getOption(map, "ignore-https-certificate") ?: false,
                httpFallbackFileSize = getOption(map, "http-fallback-file-size") ?: (1024L * 1024L * 1024L),
            )
        }

        /**
         * 从配置文件里读取东西，并校验
         */
        inline fun <reified Type> getOption(config: Map<String, Any>, key: String): Type? {
            return if (key in config && config[key] != null && config[key] is Type) config[key] as Type else null
        }
    }
}
