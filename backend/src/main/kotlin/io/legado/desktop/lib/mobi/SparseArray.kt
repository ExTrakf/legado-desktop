package io.legado.desktop.lib.mobi

/**
 * 桌面版 android.util.SparseArray 等价替代（mobi 库仅用 put/get/size/keyAt/valueAt）。
 * 逻辑等价：int 键 → 值 映射，按插入序维护。
 * get 声明为非空（对齐 Android SparseArray.get 的平台类型语义），键缺失时运行时返回 null。
 */
class SparseArray<T> {
    private val keys = ArrayList<Int>()
    private val values = ArrayList<T>()

    fun put(key: Int, value: T) {
        val idx = keys.indexOf(key)
        if (idx >= 0) {
            values[idx] = value
        } else {
            keys.add(key)
            values.add(value)
        }
    }

    operator fun get(key: Int): T {
        val idx = keys.indexOf(key)
        if (idx >= 0) return values[idx]
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    operator fun set(key: Int, value: T) = put(key, value)

    fun size(): Int = keys.size

    fun keyAt(index: Int): Int = keys[index]

    fun valueAt(index: Int): T = values[index]

    fun indexOfKey(key: Int): Int = keys.indexOf(key)

    fun remove(key: Int) {
        val idx = keys.indexOf(key)
        if (idx >= 0) {
            keys.removeAt(idx)
            values.removeAt(idx)
        }
    }

    fun clear() {
        keys.clear()
        values.clear()
    }
}
