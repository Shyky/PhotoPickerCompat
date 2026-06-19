package com.shyky.tech.photo.picker.compat.config

import android.net.Uri
import com.shyky.tech.photo.picker.compat.data.PickerDataSource
import com.shyky.tech.photo.picker.compat.pipeline.ItemTransform
import com.shyky.tech.photo.picker.compat.pipeline.PickerMiddleware
import com.shyky.tech.photo.picker.compat.pipeline.PickerPlugin
import com.shyky.tech.photo.picker.compat.result.ResultMapper
import kotlinx.coroutines.CoroutineScope

/**
 * DSL 作用域 — 选择器配置的声明式构建入口。
 *
 * 通过 `pickMedia<T> { ... }` 块使用，所有配置属性均可在此作用域中设置。
 * 内部状态通过 [build] 方法转换为不可变的 [PickerConfiguration]。
 *
 * @param T 选择结果的数据类型
 */
@PickerDsl
class PickerScope<T> {
    // ═══════════ 公开配置属性 ═══════════

    /** 最大可选数量，默认 99 */
    var maxCount: Int = 99

    /** 数据源（必填） */
    var dataSource: PickerDataSource? = null

    /** 布局适配器 */
    var layoutAdaptor: LayoutAdaptor = LayoutAdaptor.DEFAULT

    /** 字符串资源，默认中文 */
    var strings: PickerStrings = PickerStrings.CHINESE

    /** 埋点接口，默认无操作 */
    var analytics: PickerAnalytics = PickerAnalytics.NOOP

    /** UI 覆盖配置 */
    var uiOverrides: UiOverrides = UiOverrides.DEFAULT

    /** 日志器 */
    var logger: PickerLogger = PickerLogger.NOOP

    /** 协程作用域（必填） */
    var scope: CoroutineScope? = null

    /** 组件工厂 */
    var componentProvider: PickerComponentProvider = PickerComponentProvider.DEFAULT

    // ═══════════ 内部累积器 ═══════════

    internal val _middleware = mutableListOf<PickerMiddleware>()
    internal val _transforms = mutableListOf<ItemTransform>()
    internal val _plugins = mutableListOf<PickerPlugin>()
    internal val _tabs = mutableListOf<PickerConfiguration.TabSpec>()
    internal var _resultMapper: ResultMapper<T> = ResultMapper.URI_LIST as ResultMapper<T>
    internal var _selectionStrategy: SelectionStrategy = SelectionStrategy.Multi()

    // ═══════════ DSL 块 ═══════════

    /** DSL 块：添加中间件。使用 `+middleware` 语法 */
    fun middleware(block: MiddlewareScope.() -> Unit) {
        _middleware.addAll(MiddlewareScope().apply(block).list)
    }

    /** DSL 块：添加条目变换。使用 `+transform` 语法 */
    fun transforms(block: TransformScope.() -> Unit) {
        _transforms.addAll(TransformScope().apply(block).list)
    }

    /** DSL 块：添加插件。使用 `+plugin` 语法 */
    fun plugins(block: PluginScope.() -> Unit) {
        _plugins.addAll(PluginScope().apply(block).list)
    }

    /** DSL 块：配置照片 Tab */
    fun photoTab(block: TabScope.() -> Unit) {
        _tabs.add(TabScope().apply(block).build())
    }

    /** DSL 块：配置影集 Tab */
    fun albumTab(block: TabScope.() -> Unit) {
        _tabs.add(TabScope().apply(block).build())
    }

    /** DSL 块：设置结果映射 Lambda */
    @Suppress("UNCHECKED_CAST")
    fun <R : Any> onResult(block: (List<Uri>) -> R) {
        _resultMapper = ResultMapper.of(block) as ResultMapper<T>
    }

    /** 切换为单选模式 */
    fun singleMode() {
        _selectionStrategy = SelectionStrategy.Single
    }

    /** 切换为多选模式 */
    fun multiMode(max: Int = 99) {
        _selectionStrategy = SelectionStrategy.Multi(max)
    }

    /**
     * 构建不可变的 [PickerConfiguration]。
     * @throws IllegalStateException 如果 [dataSource] 或 [scope] 未设置
     * @throws IllegalArgumentException 如果未配置 Tab
     */
    fun build(): PickerConfiguration<T> {
        require(_tabs.isNotEmpty()) { "At least one tab required (photoTab { } or albumTab { })" }
        val ds = dataSource ?: throw IllegalStateException("dataSource must be set")
        return PickerConfiguration(
            _selectionStrategy.maxCount.coerceAtMost(999),
            _tabs.toList(),
            ds,
            _middleware.toList(),
            _transforms.toList(),
            _plugins.toList(),
            layoutAdaptor,
            _resultMapper,
            strings,
            analytics,
            uiOverrides,
            logger,
            scope ?: throw IllegalStateException("scope must be set"),
            componentProvider,
            _selectionStrategy
        )
    }
}
