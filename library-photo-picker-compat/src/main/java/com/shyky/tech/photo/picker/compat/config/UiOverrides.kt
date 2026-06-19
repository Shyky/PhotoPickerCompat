package com.shyky.tech.photo.picker.compat.config

import android.view.View

/**
 * UI 覆盖配置 — 允许微调颜色、可见性等 UI 细节而不需要替换布局文件。
 *
 * 所有属性为可选，null 表示使用默认值。
 *
 * @property grabHandleColor 顶部拖动条颜色
 * @property closeIconTint 关闭/完成按钮的着色
 * @property accentColor 强调色（选中框、按钮等）
 * @property privacyHintVisibility 隐私提示的可见性，默认 [View.VISIBLE]
 */
data class UiOverrides(
    val grabHandleColor: Int? = null,
    val closeIconTint: Int? = null,
    val accentColor: Int? = null,
    val privacyHintVisibility: Int = View.VISIBLE
) {
    companion object {
        /** 默认 UI 配置 — 全部使用默认值 */
        val DEFAULT = UiOverrides()
    }
}
