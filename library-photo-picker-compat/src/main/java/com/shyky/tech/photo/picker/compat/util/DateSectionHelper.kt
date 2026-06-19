package com.shyky.tech.photo.picker.compat.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 日期分组辅助工具 — 生成自然的日期分组标题并插入到数据列表中。
 *
 * 标题生成规则（从近到远）：
 * - 今天内 → "今天"
 * - 昨天 → "昨天"
 * - 本周内 → "星期一"、"星期二"…"星期日"
 * - 今年内 → "6月19日"
 * - 更早 → "2025年6月"
 *
 * 支持自定义 locale（默认跟随系统）。
 */
object DateSectionHelper {

    /** 可配置的语言环境 — 修改后自动清空缓存的格式化器 */
    var locale: Locale = Locale.getDefault()
        set(value) {
            field = value
            _dateFmt = null
            _yearFmt = null
        }

    /** 缓存的 "M月d日" 格式化器 — 避免重复创建 SimpleDateFormat */
    private var _dateFmt: SimpleDateFormat? = null

    /** 缓存的 "yyyy年M月" 格式化器 */
    private var _yearFmt: SimpleDateFormat? = null

    private fun dateFmt() = _dateFmt ?: SimpleDateFormat("M月d日", locale).also { _dateFmt = it }
    private fun yearFmt() = _yearFmt ?: SimpleDateFormat("yyyy年M月", locale).also { _yearFmt = it }

    /** 星期名称数组 */
    private val dayNames =
        arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")

    /**
     * 根据时间戳生成分组标题文字。
     * @param timestamp Unix 时间戳（秒）
     * @return 自然语言日期标题
     */
    fun formatSection(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp * 1000 }

        // ★ 清零时分秒，只比较日期
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val targetDay = Calendar.getInstance().apply {
            timeInMillis = target.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((today.timeInMillis - targetDay.timeInMillis) / 86400000).toInt()

        return when {
            diffDays == 0 -> "今天"
            diffDays == 1 -> "昨天"
            diffDays in 2..6 -> dayNames[target.get(Calendar.DAY_OF_WEEK) - 1]
            target.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> dateFmt().format(target.time)
            else -> yearFmt().format(target.time)
        }
    }

    /**
     * 在已排序的数据列表中插入分组标题。
     *
     * 遍历列表，每当时间戳对应的时间分组变化时，
     * 插入 [SectionHeader] 作为分组标识。
     *
     * @param items 已按时间排序的数据列表
     * @param timestampExtractor 从数据项提取时间戳的函数
     * @return 插入了 SectionHeader 的混合列表
     */
    fun <T> insertSections(
        items: List<T>,
        timestampExtractor: (T) -> Long
    ): List<Any> {
        val result = mutableListOf<Any>()
        var lastSection = ""
        for (item in items) {
            val section = formatSection(timestampExtractor(item))
            if (section != lastSection) {
                result.add(SectionHeader(section))
                lastSection = section
            }
            result.add(item as Any)
        }
        return result
    }

    /** 分组标题数据类 */
    data class SectionHeader(val text: String)
}
