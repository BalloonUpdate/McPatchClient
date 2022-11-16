package mcpatch.extension

object RuntimeExtension
{
    fun Runtime.usedMemory(): Long
    {
        return totalMemory() - freeMemory()
    }
}