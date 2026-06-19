package com.shyky.tech.photo.picker.compat.config

import com.shyky.tech.photo.picker.compat.adapter.SelectableAdapter
import com.shyky.tech.photo.picker.compat.data.PagingController
import com.shyky.tech.photo.picker.compat.data.PickerDataSource

/**
 * 组件工厂接口 — 创建选择器内部组件实例。
 *
 * 实现此接口可替换默认的分页控制器等组件，
 * 方便测试时注入 Mock 或自定义行为。
 */
interface PickerComponentProvider {
    /**
     * 创建分页控制器。
     * @param dataSource 数据源
     * @param adapter 选择器适配器
     * @param scope 父协程作用域（可选）
     * @return [PagingController] 实例
     */
    fun createPagingController(
        dataSource: PickerDataSource,
        adapter: SelectableAdapter<*>,
        scope: kotlinx.coroutines.CoroutineScope? = null
    ): PagingController

    companion object {
        /** 默认实现 — 直接创建 [PagingController] */
        val DEFAULT = object : PickerComponentProvider {
            override fun createPagingController(
                dataSource: PickerDataSource,
                adapter: SelectableAdapter<*>,
                scope: kotlinx.coroutines.CoroutineScope?
            ) = PagingController(dataSource, adapter, scope)
        }
    }
}
