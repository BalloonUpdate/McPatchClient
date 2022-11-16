package mcpatch.event

class Event<T> : Iterable<Event.Listener<T>>
{
    val listeners = mutableListOf<Listener<T>>()

    fun always(cb: (arg: T) -> Unit): Listener<T>
    {
        return addListener(cb, ListenerType.ALWAYS)
    }

    fun once(cb: (arg: T) -> Unit): Listener<T>
    {
        return addListener(cb, ListenerType.ONCE)
    }

    private fun addListener(callback: (arg: T) -> Unit, type: ListenerType): Listener<T>
    {
        val listener = Listener(callback, type)
        this += listener
        return listener
    }

    operator fun plusAssign(listener: Listener<T>)
    {
        if(listener in this)
            throw RuntimeException("The listener has already been added")

        listeners += listener
    }

    operator fun minusAssign(listener: Listener<T>)
    {
        if(listener !in this)
            throw RuntimeException("The Listener not found")

        listeners -= listener
    }

    operator fun invoke(arg: T)
    {
        val validListeners = listeners.filter { it.type != ListenerType.NEVER }

        for (listener in validListeners)
        {
            listener.callback(arg)

            if(listener.type == ListenerType.ONCE)
                listener.type = ListenerType.NEVER
        }

        listeners.removeIf { it.type == ListenerType.NEVER }
    }

    operator fun contains(listener: Listener<T>): Boolean
    {
        return listener in listeners
    }

    override fun iterator(): MutableIterator<Listener<T>>
    {
        return listeners.iterator()
    }

    class Listener<T>(
        var callback: (arg: T) -> Unit,
        var type: ListenerType,
    )

    enum class ListenerType
    {
        NEVER, ONCE, ALWAYS
    }
}

