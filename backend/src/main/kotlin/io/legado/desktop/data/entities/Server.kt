package io.legado.desktop.data.entities

import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.fromJsonObject
import org.json.JSONObject

/**
 * 服务器
 */
data class Server(
    var id: Long = System.currentTimeMillis(),
    var name: String = "",
    var type: TYPE = TYPE.WEBDAV,
    var config: String? = null,
    var sortNumber: Int = 0
)  {

    enum class TYPE {
        WEBDAV
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is Server) {
            return id == other.id
        }
        return false
    }

    fun getConfigJsonObject(): JSONObject? {
        val json = config
        json ?: return null
        return JSONObject(json)
    }

    fun getWebDavConfig(): WebDavConfig? {
        return if (type == TYPE.WEBDAV) GSON.fromJsonObject<WebDavConfig>(config).getOrNull() else null
    }

        data class WebDavConfig(
        var url: String,
        var username: String,
        var password: String
    ) 

}