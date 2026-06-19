@file:JvmName("Picker")

package com.shyky.tech.photo.picker.compat

import android.content.Context
import com.shyky.tech.photo.picker.compat.adapter.DefaultMediaAdapter
import com.shyky.tech.photo.picker.compat.adapter.SelectableAdapter
import com.shyky.tech.photo.picker.compat.config.LayoutAdaptor
import com.shyky.tech.photo.picker.compat.config.PickerAnalytics
import com.shyky.tech.photo.picker.compat.config.PickerComponentProvider
import com.shyky.tech.photo.picker.compat.config.PickerConfiguration
import com.shyky.tech.photo.picker.compat.config.PickerLogger
import com.shyky.tech.photo.picker.compat.config.PickerScope
import com.shyky.tech.photo.picker.compat.config.PickerStrings
import com.shyky.tech.photo.picker.compat.config.SelectionStrategy
import com.shyky.tech.photo.picker.compat.config.UiOverrides
import com.shyky.tech.photo.picker.compat.data.MediaStoreDataSource
import com.shyky.tech.photo.picker.compat.result.ResultMapper

/**
 * ★ 顶层 DSL 入口函数 — 一行代码构建自定义图片选择器配置。
 *
 * 完全 DSL 重载，需要调用方在 DSL 块中提供 [dataSource] 和 [scope]。
 *
 * 使用示例：
 * ```kotlin
 * val picker = pickMedia<Uri> {
 *     maxCount = 9
 *     scope = viewModelScope
 *     dataSource = MediaStoreDataSource(context)
 *     photoTab {
 *         label = "照片"
 *         adapterFactory = { DefaultMediaAdapter(it) }
 *     }
 *     onResult { uris -> handle(uris) }
 * }
 * ```
 *
 * @param T 选择结果的数据类型
 * @param block DSL 配置块，在 [PickerScope] 上下文中执行
 * @return 不可变的 [PickerConfiguration]
 * @throws IllegalStateException 如果 dataSource 或 scope 未设置
 */
@JvmSynthetic
inline fun <reified T : Any> pickMedia(
    block: PickerScope<T>.() -> Unit
): PickerConfiguration<T> {
    val scope = PickerScope<T>().apply(block)
    // ★ 注入默认值校验 — 如果调用方未设置必填项，抛出明确错误
    if (scope.dataSource == null) {
        error("dataSource must be set. Use dataSource = MediaStoreDataSource(context)")
    }
    if (scope.scope == null) {
        error("scope must be set. Use scope = viewModelScope")
    }
    return scope.build()
}

/**
 * ★ 快捷入口函数 — 使用默认配置（MediaStore + 照片 Tab + 中文）创建选择器。
 *
 * 不需要 DSL 的简单场景可直接使用此重载。
 *
 * @param context 用于查询 MediaStore 的 Context
 * @param maxCount 最大可选数量，默认 99
 * @param scope 协程作用域（必填，推荐 lifecycleScope）
 * @return [PickerConfiguration]，结果类型为 [List<Uri>]
 */
@JvmSynthetic
fun pickMedia(
    context: Context,
    maxCount: Int = 99,
    scope: kotlinx.coroutines.CoroutineScope
): PickerConfiguration<List<android.net.Uri>> {
    val ds = MediaStoreDataSource(context)
    val adapterFactory: (Context) -> SelectableAdapter<*> = { ctx ->
        DefaultMediaAdapter(ctx).apply {
            maxSelectCount = maxCount
        }
    }
    val tab = PickerConfiguration.TabSpec(
        label = PickerStrings.CHINESE.photoTab,
        adapterFactory = adapterFactory,
        showCameraEntry = true
    )
    return PickerConfiguration(
        maxCount = maxCount, tabs = listOf(tab), dataSource = ds,
        middleware = emptyList(), transforms = emptyList(), plugins = emptyList(),
        layoutAdaptor = LayoutAdaptor.DEFAULT,
        resultMapper = object : ResultMapper<List<android.net.Uri>> {
            override fun map(selected: List<android.net.Uri>, adapter: SelectableAdapter<*>?) =
                selected
        },
        strings = PickerStrings.CHINESE, analytics = PickerAnalytics.NOOP,
        uiOverrides = UiOverrides.DEFAULT, logger = PickerLogger.NOOP,
        scope = scope, componentProvider = PickerComponentProvider.DEFAULT,
        strategy = SelectionStrategy.Multi(maxCount)
    )
}
