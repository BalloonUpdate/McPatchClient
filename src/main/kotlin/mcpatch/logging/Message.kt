package mcpatch.logging

data class Message(
    val time: Long,
    val level: Log.LogLevel,
    val tag: String,
    val message: String,
    val newLineIndent: Boolean,
    val rangedTags: List<String>,
    val newLine: Boolean,
)