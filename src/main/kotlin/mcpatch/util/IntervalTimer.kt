package mcpatch.util

class IntervalTimer(val intervalMs: Int, initialTime: Long = 0)
{
    private var timer = initialTime

    val timeout: Boolean get() {
        val now = System.currentTimeMillis()
        return (now - timer > intervalMs).also { if (it) timer = now }
    }
}