package com.shyky.tech.photo.picker.compat.config

import com.shyky.tech.photo.picker.compat.adapter.SelectableAdapter

/**
 * 单个 Tab 的 DSL 作用域 — 在 `photoTab { }` 或 `albumTab { }` 块中使用。
 *
 * 配置标签页的文字、适配器工厂、拍照入口和隐私提示可见性。
 * [label] 和 [adapterFactory] 为必填项。
 */
@PickerDsl
class TabScope {
    /** Tab 标签文字（必填） */
    var label: String = ""

    /** 适配器工厂函数（必填），接收 Context 返回 [SelectableAdapter] */
    var adapterFactory: ((android.content.Context) -> SelectableAdapter<*>)? = null

    /** 是否显示拍照入口 */
    var showCamera: Boolean = false

    /** 是否显示隐私提示 */
    var showPrivacyHint: Boolean = true

    /**
     * 构建不可变的 TabSpec。
     * @throws IllegalArgumentException 如果 label 为空或 adapterFactory 未设置
     */
    fun build(): PickerConfiguration.TabSpec {
        require(label.isNotEmpty()) { "Tab label must be set" }
        val factory = requireNotNull(adapterFactory) { "adapterFactory must be set" }
        return PickerConfiguration.TabSpec(label, factory, showCamera, showPrivacyHint)
    }
}
