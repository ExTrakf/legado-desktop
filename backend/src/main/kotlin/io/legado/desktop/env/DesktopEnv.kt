package io.legado.desktop.env

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 桌面环境抽象：替代原 Legado 中的 Android Context（splitties.appctx）。
 *
 * 职责：
 * - 数据目录（书籍数据库、缓存、封面、本地书籍）
 * - 配置存储（JSON，替代 SharedPreferences）
 * - 全局单例，供引擎各处调用（对应原 appCtx 的使用点）
 */
object DesktopEnv {

    @Volatile
    lateinit var homeDir: Path
        private set

    val configDir: Path get() = homeDir.resolve("config")
    val cacheDir: Path get() = homeDir.resolve("cache")
    val coversDir: Path get() = homeDir.resolve("covers")
    val booksDir: Path get() = homeDir.resolve("books")
    val dbFile: Path get() = homeDir.resolve("books.db")

    /** 初始化数据目录，可重复调用（幂等） */
    fun init(home: String? = null) {
        if (::homeDir.isInitialized) return
        val root = home
            ?: System.getenv("LEGADO_DESKTOP_HOME")
            ?: Paths.get(System.getProperty("user.home"), ".legado-desktop").toString()
        homeDir = Paths.get(root).toAbsolutePath().normalize()
        listOf(homeDir, configDir, cacheDir, coversDir, booksDir).forEach { d ->
            Files.createDirectories(d)
        }
    }

    // ---------------- 配置存储（替代 SharedPreferences） ----------------

    private val gson = Gson()
    private val configFile: Path get() = configDir.resolve("config.json")

    private val configLock = Any()

    @Volatile
    private var cache: JsonObject? = null

    private fun load(): JsonObject {
        cache?.let { return it }
        synchronized(configLock) {
            cache?.let { return it }
            val obj = if (Files.exists(configFile)) {
                runCatching {
                    gson.fromJson(Files.readString(configFile), JsonObject::class.java)
                }.getOrNull() ?: JsonObject()
            } else {
                JsonObject()
            }
            cache = obj
            return obj
        }
    }

    private fun save() {
        synchronized(configLock) {
            val obj = cache ?: return
            Files.writeString(configFile, gson.toJson(obj))
        }
    }

    fun getPrefString(key: String, def: String = ""): String {
        val el = load().get(key)
        return if (el == null || el.isJsonNull) def else el.asString
    }

    fun getPrefInt(key: String, def: Int = 0): Int {
        val el = load().get(key)
        return if (el == null || el.isJsonNull) def else el.asInt
    }

    fun getPrefLong(key: String, def: Long = 0L): Long {
        val el = load().get(key)
        return if (el == null || el.isJsonNull) def else el.asLong
    }

    fun getPrefBoolean(key: String, def: Boolean = false): Boolean {
        val el = load().get(key)
        return if (el == null || el.isJsonNull) def else el.asBoolean
    }

    fun putPrefString(key: String, value: String?) {
        if (value == null) {
            removePref(key)
            return
        }
        load().addProperty(key, value)
        save()
    }

    fun putPrefInt(key: String, value: Int) {
        load().addProperty(key, value)
        save()
    }

    fun putPrefLong(key: String, value: Long) {
        load().addProperty(key, value)
        save()
    }

    fun putPrefBoolean(key: String, value: Boolean) {
        load().addProperty(key, value)
        save()
    }

    fun removePref(key: String) {
        load().remove(key)
        save()
    }

    /** 全部配置 key（供 SourceConfig 等清理用） */
    fun allPrefKeys(): List<String> = load().keySet().toList()

    /** 全部偏好（typed：Int/Long/Boolean/String/Float/Double），供备份导出用 */
    fun allPrefs(): Map<String, Any> {
        val obj = load()
        val map = HashMap<String, Any>()
        obj.keySet().forEach { k ->
            val el = obj.get(k)
            if (el.isJsonPrimitive) {
                val p = el.asJsonPrimitive
                when {
                    p.isBoolean -> map[k] = p.asBoolean
                    p.isNumber -> {
                        val d = p.asDouble
                        map[k] = if (d == Math.floor(d) && !d.isInfinite()) {
                            if (d >= Int.MIN_VALUE && d <= Int.MAX_VALUE) p.asInt else p.asLong
                        } else p.asDouble
                    }
                    else -> map[k] = p.asString
                }
            }
        }
        return map
    }

    /** 按值类型写入偏好，供备份恢复用 */
    fun putPrefRaw(key: String, value: Any) {
        when (value) {
            is Int -> putPrefInt(key, value)
            is Long -> putPrefLong(key, value)
            is Boolean -> putPrefBoolean(key, value)
            is String -> putPrefString(key, value)
            is Float -> {
                load().addProperty(key, value)
                save()
            }
            is Double -> {
                load().addProperty(key, value)
                save()
            }
            else -> putPrefString(key, value.toString())
        }
    }
}
