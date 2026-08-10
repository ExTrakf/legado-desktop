package io.legado.desktop.help.config

import io.legado.desktop.data.appDb
import io.legado.desktop.env.DesktopEnv

object SourceConfig {
    private const val PREFIX = "SourceConfig_"
    private fun getInt(key: String, def: Int): Int = DesktopEnv.getPrefInt(PREFIX + key, def)
    private fun putInt(key: String, value: Int) = DesktopEnv.putPrefInt(PREFIX + key, value)
    private fun remove(key: String) = DesktopEnv.removePref(PREFIX + key)
    fun setBookScore(origin: String, name: String, author: String, score: Int) {
        val preScore = getBookScore(origin, name, author)
        var newScore = score
        if (preScore != 0) {
            newScore = score - preScore
        }

        putInt(origin, getSourceScore(origin) + newScore)

        putInt("${origin}_${name}_${author}", score)
    }

    fun getBookScore(origin: String, name: String, author: String): Int {
        return getInt("${origin}_${name}_${author}", 0)
    }

    fun getSourceScore(origin: String): Int {
        return getInt(origin, 0)
    }


    fun removeSource(origin: String) {
        removeSources(listOf(origin))
    }

    private fun allKeys(): List<String> = DesktopEnv.allPrefKeys().filter { it.startsWith(PREFIX) }.map { it.removePrefix(PREFIX) }

    fun removeSources(origins: Collection<String>) {
        if (origins.isEmpty()) return
        val protectedOrigins = appDb.bookSourceDao.allPart.map { it.bookSourceUrl }
        sourceConfigKeysToRemove(allKeys(), origins, protectedOrigins).forEach {
            remove(it)
        }
    }


}

internal fun belongsToSource(
    key: String,
    origin: String,
    protectedOrigins: Collection<String>,
): Boolean = key in sourceConfigKeysToRemove(
    listOf(key),
    listOf(origin),
    protectedOrigins,
)

internal fun sourceConfigKeysToRemove(
    keys: Collection<String>,
    removedOrigins: Collection<String>,
    protectedOrigins: Collection<String>,
): Set<String> {
    val removed = removedOrigins.toHashSet()
    val knownOrigins = protectedOrigins.toHashSet().apply { addAll(removed) }
    return keys.filterTo(HashSet()) { sourceOwner(it, knownOrigins) in removed }
}

private fun sourceOwner(key: String, origins: Set<String>): String? {
    if (key in origins) return key
    var separator = key.lastIndexOf('_')
    while (separator >= 0) {
        key.substring(0, separator).takeIf { it in origins }?.let { return it }
        separator = key.lastIndexOf('_', separator - 1)
    }
    return null
}
