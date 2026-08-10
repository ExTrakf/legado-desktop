package io.legado.desktop.help
import io.legado.desktop.compat.JavascriptInterface

import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.Cache
import io.legado.desktop.env.DesktopEnv
import io.legado.desktop.model.analyzeRule.QueryTTF
import io.legado.desktop.utils.MD5Utils
import io.legado.desktop.utils.memorySize
import java.io.File
import java.util.LinkedHashMap

/**
 * 桌面版 LRU（替代 androidx.collection.LruCache）。
 * 简化：按条目数淘汰（maxSize 语义与 Android 版字节上限不同，桌面内存充足可接受）。
 */
class SimpleLruCache<K, V>(maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    fun remove(key: K) {
        map.remove(key)
    }

    @Synchronized
    fun snapshot(): Map<K, V> = HashMap(map)
}

/**
 * 桌面版 ACache 替代（文件缓存，替代 Android 版 ACache/SAF）。
 */
object SimpleACache {
    private val dir: File get() = File(DesktopEnv.cacheDir.toFile(), "ACache").apply { mkdirs() }

    private fun fileOf(key: String): File = File(dir, MD5Utils.md5Encode16(key))

    fun put(key: String, value: Any, saveTime: Int = 0) {
        val file = fileOf(key)
        file.parentFile?.mkdirs()
        when (value) {
            is ByteArray -> file.writeBytes(value)
            else -> file.writeText(value.toString(), Charsets.UTF_8)
        }
    }

    fun getAsBinary(key: String): ByteArray? {
        return runCatching { fileOf(key).readBytes() }.getOrNull()
    }

    fun getAsString(key: String): String? {
        return runCatching { fileOf(key).readText(Charsets.UTF_8) }.getOrNull()
    }

    fun remove(key: String) {
        fileOf(key).delete()
    }
}

private val queryTTFMap = SimpleLruCache<String, QueryTTF>(4)

/**
 * 最多只缓存50M的数据,防止OOM（桌面版按条目数上限 5000 近似）
 */
private val memoryLruCache = SimpleLruCache<String, Any>(5000)

object AppCacheManager {

    fun put(key: String, queryTTF: QueryTTF) {
        queryTTFMap.put(key, queryTTF)
    }

    fun getQueryTTF(key: String): QueryTTF? {
        return queryTTFMap[key]
    }

    fun clearSourceVariables() {
        memoryLruCache.snapshot().keys.forEach {
            if (it.startsWith("v_")
                || it.startsWith("userInfo_")
                || it.startsWith("loginHeader_")
                || it.startsWith("sourceVariable_")
            ) {
                memoryLruCache.remove(it)
            }
        }
    }

}


@Suppress("unused")
object CacheManager {

    /**
     * saveTime 单位为秒
     */
    @JvmOverloads
    @Synchronized
    fun put(key: String, value: Any, saveTime: Int = 0) {
        val deadline =
            if (saveTime == 0) 0L else System.currentTimeMillis() + saveTime * 1000L
        when (value) {
            is ByteArray -> SimpleACache.put(key, value, saveTime)
            else -> {
                val valueStr = value.toString()
                val cache = Cache(key, valueStr, deadline)
                appDb.cacheDao.insert(cache)
                if (deadline == 0L) {
                    putMemory(key, valueStr)
                } else {
                    deleteMemory(key)
                }
            }
        }
    }

    fun putMemory(key: String, value: Any) {
        memoryLruCache.put(key, value)
    }

    //从内存中获取数据 使用lruCache
    fun getFromMemory(key: String): Any? {
        return memoryLruCache[key]
    }

    fun deleteMemory(key: String) {
        memoryLruCache.remove(key)
    }

    @Synchronized
    fun get(key: String): String? {
        getFromMemory(key)?.let {
            if (it is String) return it
        }
        val cache = appDb.cacheDao.get(key)
        if (cache != null && (cache.deadline == 0L || cache.deadline > System.currentTimeMillis())) {
            return cache.value?.also {
                if (cache.deadline == 0L) {
                    putMemory(key, it)
                }
            }
        }
        return null
    }

    @Synchronized
    fun get(key: String, onlyDisk: Boolean): String? {
        if (!onlyDisk) {
            return get(key)
        }
        val cache = appDb.cacheDao.get(key)
        if (cache != null && (cache.deadline == 0L || cache.deadline > System.currentTimeMillis())) {
            return cache.value
        }
        return null
    }

    fun getInt(key: String): Int? {
        getFromMemory(key)?.let {
            if (it is Int) return it
        }
        return get(key, true)?.toIntOrNull()
    }

    fun getLong(key: String): Long? {
        getFromMemory(key)?.let {
            if (it is Long) return it
        }
        return get(key, true)?.toLongOrNull()
    }

    fun getDouble(key: String): Double? {
        getFromMemory(key)?.let {
            if (it is Double) return it
        }
        return get(key, true)?.toDoubleOrNull()
    }

    fun getFloat(key: String): Float? {
        getFromMemory(key)?.let {
            if (it is Float) return it
        }
        return get(key, true)?.toFloatOrNull()
    }

    fun getByteArray(key: String): ByteArray? {
        return SimpleACache.getAsBinary(key)
    }

    fun putFile(key: String, value: String, saveTime: Int = 0) {
        SimpleACache.put(key, value, saveTime)
    }

    fun getFile(key: String): String? {
        return SimpleACache.getAsString(key)
    }

    @Synchronized
    fun delete(key: String) {
        appDb.cacheDao.delete(key)
        deleteMemory(key)
        SimpleACache.remove(key)
    }
}

@Suppress("unused")
object WebCacheManager {
    @JvmOverloads
    @JavascriptInterface
    fun put(key: String, value: String, saveTime: Int = 0) {
        CacheManager.put(key, value, saveTime)
    }
    @JavascriptInterface
    fun putMemory(key: String, value: String) {
        memoryLruCache.put(key, value)
    }
    @JavascriptInterface
    fun getFromMemory(key: String): String? {
        return memoryLruCache[key]?.toString()
    }
    @JavascriptInterface
    fun deleteMemory(key: String) {
        memoryLruCache.remove(key)
    }
    @JavascriptInterface
    fun get(key: String): String? {
        return CacheManager.get(key)
    }
    @JavascriptInterface
    fun get(key: String, onlyDisk: Boolean): String? {
        return CacheManager.get(key, onlyDisk)
    }
    @JavascriptInterface
    fun putFile(key: String, value: String, saveTime: Int = 0) {
        CacheManager.putFile(key, value, saveTime)
    }
    @JavascriptInterface
    fun getFile(key: String): String? {
        return CacheManager.getFile(key)
    }
    @JavascriptInterface
    fun delete(key: String) {
        CacheManager.delete(key)
    }
}
