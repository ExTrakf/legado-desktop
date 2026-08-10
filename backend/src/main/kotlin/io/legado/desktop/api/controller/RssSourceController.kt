package io.legado.desktop.api.controller


import io.legado.desktop.api.ReturnData
import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.RssSource
import io.legado.desktop.help.source.SourceHelp
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.fromJsonArray
import io.legado.desktop.utils.fromJsonObject

object RssSourceController {

    val sources: ReturnData
        get() {
            val source = appDb.rssSourceDao.all
            val returnData = ReturnData()
            return if (source.isEmpty()) {
                returnData.setErrorMsg("源列表为空")
            } else returnData.setData(source)
        }

    fun saveSource(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData ?: return returnData.setErrorMsg("数据不能为空")
        GSON.fromJsonObject<RssSource>(postData).onFailure {
            returnData.setErrorMsg("转换源失败${it.localizedMessage}")
        }.onSuccess { source ->
            if (source.sourceName.isNullOrEmpty() || source.sourceUrl.isNullOrEmpty()) {
                returnData.setErrorMsg("源名称和URL不能为空")
            } else {
                appDb.rssSourceDao.insert(source)
                returnData.setData("")
            }
        }
        return returnData
    }

    fun saveSources(postData: String?): ReturnData {
        postData ?: return ReturnData().setErrorMsg("数据不能为空")
        val okSources = arrayListOf<RssSource>()
        val source = GSON.fromJsonArray<RssSource>(postData).getOrNull()
        if (source.isNullOrEmpty()) {
            return ReturnData().setErrorMsg("转换源失败")
        }
        for (rssSource in source) {
            if (rssSource.sourceName.isBlank() || rssSource.sourceUrl.isBlank()) {
                continue
            }
            appDb.rssSourceDao.insert(rssSource)
            okSources.add(rssSource)
        }
        return ReturnData().setData(okSources)
    }

    fun getSource(parameters: Map<String, List<String>>): ReturnData {
        val url = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (url.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定书源地址")
        }
        val source = appDb.rssSourceDao.getByKey(url)
            ?: return returnData.setErrorMsg("未找到源，请检查源地址")
        return returnData.setData(source)
    }

    fun deleteSources(postData: String?): ReturnData {
        postData ?: return ReturnData().setErrorMsg("没有传递数据")
        GSON.fromJsonArray<RssSource>(postData).onFailure {
            return ReturnData().setErrorMsg("格式不对")
        }.onSuccess {
            SourceHelp.deleteRssSources(it)
        }
        return ReturnData().setData("已执行"/*okSources*/)
    }
}
