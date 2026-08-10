package io.legado.desktop.data

import io.legado.desktop.utils.GSON
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.sql.Connection
import java.sql.ResultSet

/**
 * SQLite 通用执行层（替代 Room）：
 * - 命名参数 :name → ?（支持 IN (:list) 展开）
 * - 反射映射 ResultSet → 实体（字段名 = 列名）
 * - 全部同步执行，上层负责调度（Dispatchers.IO）
 */
object SqlExecutor {

    /** 执行写操作（insert/update/delete），返回影响行数 */
    fun Connection.execute(sql: String, args: List<Any?> = emptyList()): Int {
        val (prepared, bindArgs) = bind(sql, args)
        prepareStatement(prepared).use { st ->
            st.bindAll(bindArgs)
            return st.executeUpdate()
        }
    }

    /** 查询单条 */
    fun <T> Connection.queryOne(sql: String, args: List<Any?> = emptyList(), cls: Class<T>): T? {
        return queryList(sql, args, cls).firstOrNull()
    }

    /** 查询多条 */
    fun <T> Connection.queryList(sql: String, args: List<Any?> = emptyList(), cls: Class<T>): List<T> {
        val (prepared, bindArgs) = bind(sql, args)
        prepareStatement(prepared).use { st ->
            st.bindAll(bindArgs)
            st.executeQuery().use { rs ->
                return rs.toList(cls)
            }
        }
    }

    /** 查询标量（COUNT/EXISTS 等） */
    fun <T> Connection.queryValue(sql: String, args: List<Any?> = emptyList(), cls: Class<T>): T? {
        val (prepared, bindArgs) = bind(sql, args)
        prepareStatement(prepared).use { st ->
            st.bindAll(bindArgs)
            st.executeQuery().use { rs ->
                if (rs.next()) {
                    return convertScalar(rs.getObject(1), cls)
                }
                return null
            }
        }
    }

    /**
     * 标量类型归一化：sqlite-jdbc 对 INT 类型小值返回 Integer、大值返回 Long，
     * 按目标类型统一转换（等价 Room 返回的 Java 类型）。
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> convertScalar(value: Any?, cls: Class<T>): T? {
        if (value == null) return null
        return when (cls) {
            Int::class.java -> (value as Number).toInt() as T
            Long::class.java -> (value as Number).toLong() as T
            Float::class.java -> (value as Number).toFloat() as T
            Double::class.java -> (value as Number).toDouble() as T
            Boolean::class.java -> ((value as Number).toInt() != 0) as T
            String::class.java -> value.toString() as T
            else -> value as? T
        }
    }

    /** 批量执行（事务内调用） */
    fun Connection.executeBatch(sql: String, batchArgs: List<List<Any?>>) {
        val (prepared, _) = bind(sql, batchArgs.firstOrNull() ?: emptyList())
        prepareStatement(prepared).use { st ->
            batchArgs.forEach { args ->
                st.bindAll(args)
                st.addBatch()
            }
            st.executeBatch()
        }
    }

    // ---------- 内部 ----------

    private fun java.sql.PreparedStatement.bindAll(args: List<Any?>) {
        args.forEachIndexed { i, v ->
            when (v) {
                null -> setNull(i + 1, java.sql.Types.NULL)
                is Boolean -> setInt(i + 1, if (v) 1 else 0)
                is Int -> setInt(i + 1, v)
                is Long -> setLong(i + 1, v)
                is Float -> setFloat(i + 1, v)
                is Double -> setDouble(i + 1, v)
                is String -> setString(i + 1, v)
                // 复杂类型（Room @TypeConverters 语义）：实体规则类等以 JSON 字符串存取
                else -> setString(i + 1, GSON.toJson(v))
            }
        }
    }

    /**
     * Room 风格参数绑定（位置绑定，按 SQL 中占位符出现顺序消耗 args）：
     * - `:name` → `?`；`:list`（Collection）展开为多个 `?`（IN 子句）
     * - 字面 `?` 同样消耗一个位置参数（BookDaoImpl 等 `?` 风格 SQL 依赖此行为）
     * - SQL 字符串字面量中的冒号/问号不处理（已确认 legado 的 SQL 无此情况）
     */
    private fun bind(sql: String, args: List<Any?>): Pair<String, List<Any?>> {
        val sb = StringBuilder()
        val bindArgs = arrayListOf<Any?>()
        var argIdx = 0
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            if (c == ':') {
                // 收集参数名（仅用于跳过，实际按位置绑定）
                val m = Regex("^:([A-Za-z_][A-Za-z0-9_]*)").find(sql.substring(i))
                if (m != null) {
                    val value = if (argIdx < args.size) args[argIdx] else null
                    argIdx++
                    if (value is Collection<*>) {
                        // IN (:list) 展开
                        if (value.isEmpty()) {
                            sb.append("NULL")
                        } else {
                            sb.append(value.joinToString(", ") { "?" })
                            bindArgs.addAll(value)
                        }
                    } else {
                        sb.append("?")
                        bindArgs.add(value)
                    }
                    i += m.groupValues[0].length
                    continue
                }
            } else if (c == '?') {
                // 字面 ? 占位符：消耗一个位置参数（与 :name 共用顺序计数器）
                val value = if (argIdx < args.size) args[argIdx] else null
                argIdx++
                sb.append("?")
                bindArgs.add(value)
                i++
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString() to bindArgs
    }

    // ---------- ResultSet → 实体（反射） ----------

    fun <T> ResultSet.toList(cls: Class<T>): List<T> {
        val result = arrayListOf<T>()
        // 基础类型（String/Int/Long/Boolean…）查询：取第一列标量（等价 Room List<String> 等）
        if (isScalarType(cls)) {
            while (next()) {
                result.add(convertScalar(getObject(1), cls) ?: continue)
            }
            return result
        }
        val constructor = findConstructor(cls)
        while (next()) {
            result.add(mapRow(this, cls, constructor))
        }
        return result
    }

    private fun isScalarType(cls: Class<*>): Boolean = when (cls) {
        String::class.java, Int::class.java, Long::class.java, Float::class.java,
        Double::class.java, Boolean::class.java, java.lang.Integer::class.java,
        java.lang.Long::class.java, java.lang.Float::class.java, java.lang.Double::class.java,
        java.lang.Boolean::class.java -> true
        else -> false
    }

    private fun <T> findConstructor(cls: Class<T>): Constructor<T> {
        @Suppress("UNCHECKED_CAST")
        return cls.declaredConstructors.firstOrNull { it.parameterCount == 0 } as? Constructor<T>
            ?: throw IllegalStateException("实体 ${cls.name} 需要无参构造（可给属性默认值）")
    }

    private fun <T> mapRow(rs: ResultSet, cls: Class<T>, ctor: Constructor<T>): T {
        val obj = ctor.newInstance()
        val meta = rs.metaData
        for (i in 1..meta.columnCount) {
            val col = meta.getColumnLabel(i)
            val value = rs.getObject(i)
            if (value == null) continue
            // 实体属性（含继承的 BaseBook/BaseSource 等）
            var field = findField(cls, col)
            if (field == null) {
                // 列名到字段名可能因保留字/别名不同，尝试忽略大小写
                field = cls.fields.firstOrNull { it.name.equals(col, ignoreCase = true) }
            }
            if (field == null) continue
            try {
                field.isAccessible = true
                val converted = convert(value, field.type)
                if (converted != null) field.set(obj, converted)
            } catch (_: Throwable) {
            }
        }
        return obj
    }

    private fun findField(cls: Class<*>, name: String): java.lang.reflect.Field? {
        var c: Class<*>? = cls
        while (c != null) {
            c.declaredFields.firstOrNull { it.name == name }?.let { return it }
            c = c.superclass
        }
        return null
    }

    private fun convert(value: Any, type: Class<*>): Any? {
        return when (type) {
            String::class.java -> value.toString()
            Int::class.java, java.lang.Integer::class.java -> (value as Number).toInt()
            Long::class.java, java.lang.Long::class.java -> (value as Number).toLong()
            Float::class.java, java.lang.Float::class.java -> (value as Number).toFloat()
            Double::class.java, java.lang.Double::class.java -> (value as Number).toDouble()
            Boolean::class.java, java.lang.Boolean::class.java -> (value as Number).toInt() != 0
            else -> {
                // 复杂类型字段（如 BookSource 的 rule* 子类）以 JSON 存/取：
                // Room @TypeConverters 等价 —— GSON 已注册全部规则类的 jsonDeserializer
                runCatching { GSON.fromJson(value.toString(), type) }.getOrNull()
            }
        }
    }
}
