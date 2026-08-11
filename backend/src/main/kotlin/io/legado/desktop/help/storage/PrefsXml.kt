package io.legado.desktop.help.storage

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Android SharedPreferences XML 读写（备份 config.xml 兼容）。
 * 格式：<map><string name="k">v</string><int name="k">1</int>...</map>
 */
object PrefsXml {

    fun write(file: File, prefs: Map<String, Any>) {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val map = doc.createElement("map")
        doc.appendChild(map)
        prefs.forEach { (key, value) ->
            val el = doc.createElement(when (value) {
                is Int -> "int"
                is Long -> "long"
                is Boolean -> "boolean"
                is Float -> "float"
                is Double -> "double"
                else -> "string"
            })
            el.setAttribute("name", key)
            el.textContent = value.toString()
            map.appendChild(el)
        }
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8")
        transformer.transform(DOMSource(doc), StreamResult(file))
    }

    fun read(file: File): Map<String, Any> {
        if (!file.exists()) return emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = LinkedHashMap<String, Any>()
        val nodes = doc.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.tagName != "map") {
                val name = node.getAttribute("name")
                val text = node.textContent ?: ""
                result[name] = when (node.tagName) {
                    "int" -> text.toIntOrNull() ?: 0
                    "long" -> text.toLongOrNull() ?: 0L
                    "boolean" -> text.toBoolean()
                    "float" -> text.toFloatOrNull() ?: 0f
                    "double" -> text.toDoubleOrNull() ?: 0.0
                    else -> text
                }
            }
        }
        return result
    }
}
