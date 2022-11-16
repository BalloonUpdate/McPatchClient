package mcpatch.data

enum class ModificationMode(val flag: String)
{
    Empty("e"),
    Fill("f"),
    Modify("m");

    override fun toString() = flag

    companion object {
        @JvmStatic
        fun FromString(flag: String): ModificationMode
        {
            return when (flag) {
                "e" -> Empty
                "f" -> Fill
                "m" -> Modify
                else -> throw ClassCastException("The value '$flag' is not one of members of ModificationMode")
            }
        }
    }
}