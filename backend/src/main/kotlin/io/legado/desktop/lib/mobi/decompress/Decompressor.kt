package io.legado.desktop.lib.mobi.decompress

interface Decompressor {

    fun decompress(data: ByteArray): ByteArray

}
