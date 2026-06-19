package com.shyky.tech.photo.picker.compat.config

/**
 * 选择策略密封类 — 控制选择器的单选/多选行为。
 *
 * 子类：
 * - [Single]：单选模式，最多选 1 张
 * - [Multi]：多选模式，可指定最大数量
 */
sealed class SelectionStrategy {
    /** 最大可选数量 */
    abstract val maxCount: Int

    /** 选择模式的描述文字（用于 UI 提示） */
    abstract val modeDescription: String

    /** 单选策略 — 最多选择 1 张 */
    object Single : SelectionStrategy() {
        override val maxCount = 1
        override val modeDescription = "单选"
    }

    /**
     * 多选策略 — 可指定最大选择数量。
     * @param maxCount 最大可选数量，默认 99
     */
    data class Multi(override val maxCount: Int = 99) : SelectionStrategy() {
        override val modeDescription = "最多${maxCount}张"
    }
}
