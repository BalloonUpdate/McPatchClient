package mcpatch.localization

object Localization
{
    var lang: Map<LangNodes, String>? = null

    fun init(content: Map<String, String>)
    {
        lang = LangNodes.values()
            .associateWith {
                val key = it.name.replace("_", "-")
                content[key] ?: key
            }
    }

    operator fun get(key: LangNodes, vararg argsPairs: String): String
    {
        if (lang == null)
            throw RuntimeException("Localization class has not been initialized yet.")

        var result = lang!![key]!!

        for (i in 0 until argsPairs.size / 2)
        {
            val from = "{${argsPairs[i * 2]}}"
            val to = argsPairs[i * 2 + 1]
            result = result.replace(from, to)
        }

        return result
    }

//    fun LangNodes.apply(vararg argsPairs: String): String
//    {
//        Localization[this, **argsPairs]
//    }
}