package io.legado.desktop.data

import org.sqlite.Collation
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite 连接管理（桌面版替代 Room AppDatabase）。
 * 单用户桌面场景：单连接 + 全局锁。
 */
object SqliteDatabase {

    @Volatile
    private var conn: Connection? = null

    /** 全局数据库锁（所有 DAO 操作串行化，桌面单用户足够） */
    val dbLock = Any()

    @Volatile
    var initialized: Boolean = false
        private set

    fun init(dbFile: Path) {
        synchronized(dbLock) {
            if (initialized) return
            Class.forName("org.sqlite.JDBC")
            // Windows 兼容：Path.toString() 含反斜杠，JDBC URL 需统一为正斜杠
            val url = "jdbc:sqlite:" + dbFile.toAbsolutePath().toString().replace('\\', '/')
            val connection = DriverManager.getConnection(url)
            connection.createStatement().use { st ->
                st.executeUpdate("PRAGMA journal_mode=WAL")
                st.executeUpdate("PRAGMA foreign_keys=ON")
                st.executeUpdate("PRAGMA busy_timeout=5000")
            }
            // Android 专属 collation：localized（Room schema 中 bookmarks/readRecord/highlights 排序用到）。
            // 桌面等价：大小写不敏感比较（保留原 SQL 不动，避免改查询）。
            Collation.create(connection, "localized", object : Collation() {
                override fun xCompare(a: String, b: String): Int =
                    a.compareTo(b, ignoreCase = true)
            })
            conn = connection
            // 执行 schema v99（sqlite-jdbc 不支持多语句，按 ; 拆分执行）
            val schemaSql = SqliteDatabase::class.java
                .getResourceAsStream("/schema.sql")
                ?.readBytes()?.toString(Charsets.UTF_8)
                ?: throw IllegalStateException("schema.sql 缺失")
            val statements = schemaSql.split(';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            connection.createStatement().use { st ->
                statements.forEach { st.execute(it) }
            }
            initialized = true
        }
    }

    fun get(): Connection =
        conn ?: throw IllegalStateException("数据库未初始化，请先调用 SqliteDatabase.init()")

    fun close() {
        synchronized(dbLock) {
            runCatching { conn?.close() }
            conn = null
            initialized = false
        }
    }
}
