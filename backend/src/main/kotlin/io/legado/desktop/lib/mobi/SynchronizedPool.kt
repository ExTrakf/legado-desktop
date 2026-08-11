package io.legado.desktop.lib.mobi

import java.util.ArrayDeque

/**
 * 桌面版 androidx.core.util.Pools.SynchronizedPool 等价替代（mobi 库 Lz77Decompressor 用）。
 */
class SynchronizedPool<T>(private val maxPoolSize: Int) {
    private val pool = ArrayDeque<T>(maxPoolSize)

    @Synchronized
    fun acquire(): T? = pool.pollFirst()

    @Synchronized
    fun release(element: T): Boolean {
        if (pool.size < maxPoolSize) {
            pool.addLast(element)
            return true
        }
        return false
    }
}
