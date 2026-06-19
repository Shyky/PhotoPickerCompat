package com.shyky.tech.photo.picker.compat.config

import android.content.Context
import com.shyky.tech.photo.picker.compat.adapter.SelectableAdapter
import com.shyky.tech.photo.picker.compat.data.PickerDataSource
import com.shyky.tech.photo.picker.compat.pipeline.ItemTransform
import com.shyky.tech.photo.picker.compat.pipeline.PickerMiddleware
import com.shyky.tech.photo.picker.compat.pipeline.PickerPlugin
import com.shyky.tech.photo.picker.compat.result.ResultMapper
import kotlinx.coroutines.CoroutineScope

/**
 * 选择器的完整配置对象 — 不可变，由 [Builder] 构建。
 *
 * 包含所有运行参数：Tab 配置、数据源、中间件链、变换链、
 * 插件、布局适配器、字符串资源、埋点、UI 覆盖等。
 *
 * @param T 选择结果的数据类型（由 [ResultMapper] 决定）
 */
class PickerConfiguration<T> internal constructor(
    /** 最大可选数量（自动限制为 999） */
    val maxCount: Int,
    /** Tab 规格列表（至少 1 个） */
    val tabs: List<TabSpec>,
    /** 数据源 */
    val dataSource: PickerDataSource,
    /** 中间件链（按添加顺序执行） */
    val middleware: List<PickerMiddleware>,
    /** 条目变换链（用于过滤/转换数据） */
    val transforms: List<ItemTransform>,
    /** 插件列表 */
    val plugins: List<PickerPlugin>,
    /** 布局适配器（列数/间距） */
    val layoutAdaptor: LayoutAdaptor,
    /** 结果类型映射器 */
    val resultMapper: ResultMapper<T>,
    /** 字符串资源 */
    val strings: PickerStrings,
    /** 埋点接口 */
    val analytics: PickerAnalytics,
    /** UI 覆盖配置 */
    val uiOverrides: UiOverrides,
    /** 日志器 */
    val logger: PickerLogger,
    /** 协程作用域 */
    val scope: CoroutineScope,
    /** 组件工厂 */
    val componentProvider: PickerComponentProvider,
    /** 选择策略（单选/多选） */
    val strategy: SelectionStrategy
) {
    /**
     * Tab 规格 — 描述选择器中的一个标签页。
     *
     * @param label Tab 的显示文字
     * @param adapterFactory 创建适配器的工厂函数，接收 Context 返回 [SelectableAdapter]
     * @param showCameraEntry 是否显示拍照入口
     * @param showPrivacyHint 是否显示隐私提示
     */
    data class TabSpec(
        val label: String,
        val adapterFactory: (Context) -> SelectableAdapter<*>,
        val showCameraEntry: Boolean = false,
        val showPrivacyHint: Boolean = true
    )

    /**
     * [PickerConfiguration] 的构建器 — 采用 Builder + DSL 混合模式。
     *
     * 所有属性都有合理默认值，但 [dataSource] 和 [scope] 为必填项，
     * [tabs] 至少需要一个。调用 [build] 时进行校验。
     */
    class Builder<T> internal constructor(
        /** 选择策略，默认多选 99 张 */
        private var strategy: SelectionStrategy = SelectionStrategy.Multi(),
        /** Tab 列表 */
        private val tabs: MutableList<TabSpec> = mutableListOf(),
        /** 数据源（必填） */
        private var dataSource: PickerDataSource? = null,
        /** 中间件列表 */
        private val middleware: MutableList<PickerMiddleware> = mutableListOf(),
        /** 变换列表 */
        private val transforms: MutableList<ItemTransform> = mutableListOf(),
        /** 插件列表 */
        private val plugins: MutableList<PickerPlugin> = mutableListOf(),
        /** 布局适配器 */
        private var layoutAdaptor: LayoutAdaptor = LayoutAdaptor.DEFAULT,
        /** 结果映射器 */
        private var resultMapper: ResultMapper<T> = ResultMapper.URI_LIST as ResultMapper<T>,
        /** 字符串资源 */
        private var strings: PickerStrings = PickerStrings.CHINESE,
        /** 埋点接口 */
        private var analytics: PickerAnalytics = PickerAnalytics.NOOP,
        /** UI 覆盖 */
        private var uiOverrides: UiOverrides = UiOverrides.DEFAULT,
        /** 日志器 */
        private var logger: PickerLogger = PickerLogger.NOOP,
        /** 协程作用域（必填） */
        private var scope: CoroutineScope? = null,
        /** 组件工厂 */
        private var componentProvider: PickerComponentProvider = PickerComponentProvider.DEFAULT
    ) {
        /** 设置选择策略 */
        fun strategy(s: SelectionStrategy) = apply { strategy = s }

        /** 设置数据源（必填） */
        fun dataSource(ds: PickerDataSource) = apply { dataSource = ds }

        /** 设置布局适配器 */
        fun layoutAdaptor(la: LayoutAdaptor) = apply { layoutAdaptor = la }

        /** 设置字符串资源 */
        fun strings(s: PickerStrings) = apply { strings = s }

        /** 设置埋点接口 */
        fun analytics(a: PickerAnalytics) = apply { analytics = a }

        /** 设置 UI 覆盖 */
        fun uiOverrides(u: UiOverrides) = apply { uiOverrides = u }

        /** 设置日志器 */
        fun logger(l: PickerLogger) = apply { logger = l }

        /** 设置协程作用域（必填） */
        fun scope(s: CoroutineScope) = apply { scope = s }

        /** 设置组件工厂 */
        fun componentProvider(cp: PickerComponentProvider) = apply { componentProvider = cp }

        /** 添加一个 Tab */
        fun addTab(tab: TabSpec) = apply { tabs.add(tab) }

        /** 添加中间件 */
        fun addMiddleware(m: PickerMiddleware) = apply { middleware.add(m) }

        /** 添加条目变换 */
        fun addTransform(t: ItemTransform) = apply { transforms.add(t) }

        /** 添加插件 */
        fun addPlugin(p: PickerPlugin) = apply { plugins.add(p) }

        /**
         * 设置结果映射器（Lambda 形式）。
         * @param mapper 将 URI 列表映射为自定义结果类型的函数
         */
        @Suppress("UNCHECKED_CAST")
        fun <R : Any> resultMapper(mapper: (List<android.net.Uri>) -> R) = apply {
            this.resultMapper = ResultMapper.of(mapper) as ResultMapper<T>
        }

        /**
         * 构建不可变的 [PickerConfiguration]。
         * @throws IllegalStateException 如果 [dataSource] 或 [scope] 未设置
         * @throws IllegalArgumentException 如果 [tabs] 为空
         */
        fun build(): PickerConfiguration<T> {
            require(tabs.isNotEmpty()) { "At least one tab required" }
            return PickerConfiguration(
                strategy.maxCount.coerceAtMost(999), // ★ 最大数量硬限制为 999
                tabs.toList(),
                dataSource ?: throw IllegalStateException("dataSource must be set"),
                middleware.toList(),
                transforms.toList(),
                plugins.toList(),
                layoutAdaptor,
                resultMapper,
                strings,
                analytics,
                uiOverrides,
                logger,
                scope ?: throw IllegalStateException("scope must be set"),
                componentProvider,
                strategy
            )
        }
    }
}
