package io.legado.desktop.help.config


@Suppress("ConstPropertyName")
object ReadTipConfig {

    const val none = 0
    const val chapterTitle = 1
    const val time = 2
    const val battery = 3
    const val batteryPercentage = 10
    const val page = 4
    const val totalProgress = 5
    const val pageAndTotal = 6
    const val bookName = 7
    const val timeBattery = 8
    const val timeBatteryPercentage = 9
    const val totalProgress1 = 11

    val tipValues = arrayOf(
        none, bookName, chapterTitle, time, battery, batteryPercentage, page,
        totalProgress, totalProgress1, pageAndTotal, timeBattery, timeBatteryPercentage
    )
    // 资源数组：对齐原版 values-zh/arrays.xml（read_tip/tip_color/tip_divider_color）
    val tipNames get() = listOf("无", "书名", "标题", "时间", "电量", "电量%", "页数", "进度(%)")

    val tipColorNames get() = listOf("跟随内容", "自定义")
    val tipDividerColorNames
        get() = listOf("默认", "跟随内容", "自定义")

    var tipHeaderLeft: Int
        get() = ReadBookConfig.config.tipHeaderLeft
        set(value) {
            ReadBookConfig.config.tipHeaderLeft = value
        }

    var tipHeaderMiddle: Int
        get() = ReadBookConfig.config.tipHeaderMiddle
        set(value) {
            ReadBookConfig.config.tipHeaderMiddle = value
        }

    var tipHeaderRight: Int
        get() = ReadBookConfig.config.tipHeaderRight
        set(value) {
            ReadBookConfig.config.tipHeaderRight = value
        }

    var tipFooterLeft: Int
        get() = ReadBookConfig.config.tipFooterLeft
        set(value) {
            ReadBookConfig.config.tipFooterLeft = value
        }

    var tipFooterMiddle: Int
        get() = ReadBookConfig.config.tipFooterMiddle
        set(value) {
            ReadBookConfig.config.tipFooterMiddle = value
        }

    var tipFooterRight: Int
        get() = ReadBookConfig.config.tipFooterRight
        set(value) {
            ReadBookConfig.config.tipFooterRight = value
        }

    var tipHeaderLeftTemplate: String?
        get() = ReadBookConfig.config.tipHeaderLeftTemplate
        set(value) {
            ReadBookConfig.config.tipHeaderLeftTemplate = value
        }

    var tipHeaderMiddleTemplate: String?
        get() = ReadBookConfig.config.tipHeaderMiddleTemplate
        set(value) {
            ReadBookConfig.config.tipHeaderMiddleTemplate = value
        }

    var tipHeaderRightTemplate: String?
        get() = ReadBookConfig.config.tipHeaderRightTemplate
        set(value) {
            ReadBookConfig.config.tipHeaderRightTemplate = value
        }

    var tipFooterLeftTemplate: String?
        get() = ReadBookConfig.config.tipFooterLeftTemplate
        set(value) {
            ReadBookConfig.config.tipFooterLeftTemplate = value
        }

    var tipFooterMiddleTemplate: String?
        get() = ReadBookConfig.config.tipFooterMiddleTemplate
        set(value) {
            ReadBookConfig.config.tipFooterMiddleTemplate = value
        }

    var tipFooterRightTemplate: String?
        get() = ReadBookConfig.config.tipFooterRightTemplate
        set(value) {
            ReadBookConfig.config.tipFooterRightTemplate = value
        }

    fun effectiveTemplate(template: String?, legacyTip: Int): String =
        template ?: ReaderInfoTemplate.fromLegacy(legacyTip)

    var headerMode: Int
        get() = ReadBookConfig.config.headerMode
        set(value) {
            ReadBookConfig.config.headerMode = value
        }

    var footerMode: Int
        get() = ReadBookConfig.config.footerMode
        set(value) {
            ReadBookConfig.config.footerMode = value
        }

    var tipColor: Int
        get() = ReadBookConfig.config.tipColor
        set(value) {
            ReadBookConfig.config.tipColor = value
        }

    var tipDividerColor: Int
        get() = ReadBookConfig.config.tipDividerColor
        set(value) {
            ReadBookConfig.config.tipDividerColor = value
        }

    fun getHeaderModes(): LinkedHashMap<Int, String> {
        return linkedMapOf(
            Pair(0, "状态栏显示时隐藏"),
            Pair(1, "显示"),
            Pair(2, "隐藏")
        )
    }

    fun getFooterModes(): LinkedHashMap<Int, String> {
        return linkedMapOf(
            Pair(0, "显示"),
            Pair(1, "隐藏")
        )
    }
}
