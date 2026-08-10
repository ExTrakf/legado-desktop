package io.legado.desktop.help.config

import io.legado.desktop.env.DesktopEnv

@Suppress("ConstPropertyName")
object LocalConfig {
    private const val PREFIX = "local_"

    private fun getString(key: String, def: String?): String? =
        DesktopEnv.getPrefString(PREFIX + key, def ?: "").takeUnless { it.isEmpty() && def == null } ?: def

    private fun getLong(key: String, def: Long): Long = DesktopEnv.getPrefLong(PREFIX + key, def)
    private fun getBoolean(key: String, def: Boolean = false): Boolean = DesktopEnv.getPrefBoolean(PREFIX + key, def)
    private fun getInt(key: String, def: Int): Int = DesktopEnv.getPrefInt(PREFIX + key, def)

    private fun putString(key: String, value: String) = DesktopEnv.putPrefString(PREFIX + key, value)
    private fun putLong(key: String, value: Long) = DesktopEnv.putPrefLong(PREFIX + key, value)
    private fun putBoolean(key: String, value: Boolean) = DesktopEnv.putPrefBoolean(PREFIX + key, value)
    private fun putInt(key: String, value: Int) = DesktopEnv.putPrefInt(PREFIX + key, value)
    private fun remove(key: String) = DesktopEnv.removePref(PREFIX + key)

    private inline fun edit(block: () -> Unit) = block()

    private const val versionCodeKey = "appVersionCode"

    /**
     * 本地密码,用来对需要备份的敏感信息加密,如 webdav 配置等
     */
    var password: String?
        get() = getString("password", null)
        set(value) {
            if (value != null) {
                putString("password", value)
            } else {
                remove("password")
            }
        }

    var lastBackup: Long
        get() = getLong("lastBackup", 0)
        set(value) {
            putLong("lastBackup", value)
        }

    var privacyPolicyOk: Boolean
        get() = getBoolean("privacyPolicyOk")
        set(value) {
            putBoolean("privacyPolicyOk", value)
        }

    val readHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "readHelpVersion", "firstRead")

    val backupHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "backupHelpVersion", "firstBackup")

    val readMenuHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "readMenuHelpVersion", "firstReadMenu")

    val bookSourcesHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "bookSourceHelpVersion", "firstOpenBookSources")

    val webDavBookHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "webDavBookHelpVersion", "firstOpenWebDavBook")

    val ruleHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "ruleHelpVersion")

    val needUpHttpTTS: Boolean
        get() = !isLastVersion(6, "httpTtsVersion")

    val needUpTxtTocRule: Boolean
        get() = !isLastVersion(3, "txtTocRuleVersion")

    val needUpRssSources: Boolean
        get() = !isLastVersion(6, "rssSourceVersion")

    val needUpDictRule: Boolean
        get() = !isLastVersion(2, "needUpDictRule")

    var versionCode
        get() = getLong(versionCodeKey, 0)
        set(value) {
            edit { putLong(versionCodeKey, value) }
        }
    var lastCheckUpdate: Long
        get() = getLong("lastCheckUpdate", 0)
        set(value) {
            putLong("lastCheckUpdate", value)
        }

    var ignoreUpdateVersion: String?
        get() = getString("ignoreUpdateVersion", null)
        set(value) {
            if (value == null) {
                remove("ignoreUpdateVersion")
            } else {
                putString("ignoreUpdateVersion", value)
            }
        }

    val isFirstOpenApp: Boolean
        get() {
            val value = getBoolean("firstOpen", true)
            if (value) {
                edit { putBoolean("firstOpen", false) }
            }
            return value
        }

    @Suppress("SameParameterValue")
    private fun isLastVersion(
        lastVersion: Int,
        versionKey: String,
        firstOpenKey: String? = null
    ): Boolean {
        var version = getInt(versionKey, 0)
        if (version == 0 && firstOpenKey != null) {
            if (!getBoolean(firstOpenKey, true)) {
                version = 1
            }
        }
        if (version < lastVersion) {
            edit { putInt(versionKey, lastVersion) }
            return false
        }
        return true
    }

    var bookInfoDeleteAlert: Boolean
        get() = getBoolean("bookInfoDeleteAlert", true)
        set(value) {
            putBoolean("bookInfoDeleteAlert", value)
        }

    var deleteBookOriginal: Boolean
        get() = getBoolean("deleteBookOriginal")
        set(value) {
            putBoolean("deleteBookOriginal", value)
        }

    var uploadImportedBookToWebDav: Boolean
        get() = getBoolean("uploadImportedBookToWebDav")
        set(value) {
            putBoolean("uploadImportedBookToWebDav", value)
        }

    var appCrash: Boolean
        get() = getBoolean("appCrash")
        set(value) {
            putBoolean("appCrash", value)
        }

}
