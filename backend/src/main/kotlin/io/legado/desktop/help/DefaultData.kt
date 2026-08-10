package io.legado.desktop.help

import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.DictRule
import io.legado.desktop.data.entities.HttpTTS
import io.legado.desktop.data.entities.KeyboardAssist
import io.legado.desktop.data.entities.RssSource
import io.legado.desktop.data.entities.TxtTocRule
import io.legado.desktop.help.config.LocalConfig
import io.legado.desktop.help.config.ReadBookConfig
import io.legado.desktop.help.coroutine.Coroutine
import io.legado.desktop.help.source.clearSharedGlobalState
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.fromJsonArray
import io.legado.desktop.utils.fromJsonObject
import io.legado.desktop.utils.printOnDebug

object DefaultData {

    fun upVersion() {
        if (LocalConfig.versionCode < 1) {
            Coroutine.async {
                if (LocalConfig.needUpHttpTTS) {
                    importDefaultHttpTTS()
                }
                if (LocalConfig.needUpTxtTocRule) {
                    importDefaultTocRules()
                }
                if (LocalConfig.needUpRssSources) {
                    importDefaultRssSources()
                }
                if (LocalConfig.needUpDictRule) {
                    importDefaultDictRules()
                }
            }.onError {
                it.printOnDebug()
            }
        }
    }

    val httpTTS: List<HttpTTS> by lazy {
        val json =
            String(
                javaClass.getResourceAsStream("/defaultData/httpTTS.json")
                    .readBytes()
            )
        HttpTTS.fromJsonArray(json).getOrElse {
            emptyList()
        }
    }

    val readConfigs: List<ReadBookConfig.Config> by lazy {
        val json = String(
            javaClass.getResourceAsStream("/defaultData/${ReadBookConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ReadBookConfig.Config>(json).getOrNull()
            ?: emptyList()
    }

    val txtTocRules: List<TxtTocRule> by lazy {
        val json = String(
            javaClass.getResourceAsStream("/defaultData/txtTocRule.json")
                .readBytes()
        )
        GSON.fromJsonArray<TxtTocRule>(json).getOrNull() ?: emptyList()
    }

    val rssSources: List<RssSource> by lazy {
        val json = String(
            javaClass.getResourceAsStream("/defaultData/rssSources.json")
                .readBytes()
        )
        GSON.fromJsonArray<RssSource>(json).getOrDefault(emptyList())
    }

    val dictRules: List<DictRule> by lazy {
        val json = String(
            javaClass.getResourceAsStream("/defaultData/dictRules.json")
                .readBytes()
        )
        GSON.fromJsonArray<DictRule>(json).getOrThrow()
    }

    val keyboardAssists: List<KeyboardAssist> by lazy {
        val json = String(
            javaClass.getResourceAsStream("/defaultData/keyboardAssists.json")
                .readBytes()
        )
        GSON.fromJsonArray<KeyboardAssist>(json).getOrThrow()
    }

    fun importDefaultHttpTTS() {
        appDb.httpTTSDao.all
            .filter { it.id < 0 }
            .forEach { it.clearSharedGlobalState() }
        appDb.httpTTSDao.deleteDefault()
        appDb.httpTTSDao.insert(*httpTTS.toTypedArray())
    }

    fun importDefaultTocRules() {
        appDb.txtTocRuleDao.deleteDefault()
        appDb.txtTocRuleDao.insert(*txtTocRules.toTypedArray())
    }

    fun importDefaultRssSources() {
        appDb.rssSourceDao.all
            .filter { it.sourceGroup == "legado" }
            .forEach { it.clearSharedGlobalState() }
        appDb.rssSourceDao.deleteDefault()
        appDb.rssSourceDao.insert(*rssSources.toTypedArray())
    }

    fun importDefaultDictRules() {
        appDb.dictRuleDao.insert(*dictRules.toTypedArray())
    }

}
