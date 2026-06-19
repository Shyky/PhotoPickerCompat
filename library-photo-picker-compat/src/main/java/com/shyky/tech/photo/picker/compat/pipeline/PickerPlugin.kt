package com.shyky.tech.photo.picker.compat.pipeline

import android.net.Uri
import androidx.recyclerview.widget.RecyclerView

/**
 * 插件接口 — 在选择器上安装/卸载自定义行为。
 *
 * 插件在 Fragment 创建时通过 [onInstall] 安装，销毁时通过 [onUninstall] 卸载。
 * 与中间件的区别：插件可以注册 UI 组件（装饰器、触摸拦截器、覆盖层），
 * 而中间件是纯数据/逻辑拦截链。
 *
 * 每个插件必须有唯一的 [id]。
 */
interface PickerPlugin {
    /** 插件唯一标识符 */
    val id: String

    /**
     * 插件安装时调用 — 在此注册自定义组件。
     * @param host 选择器宿主，提供注册装饰器、触摸拦截器等方法
     */
    fun onInstall(host: PickerHost) {}

    /** 插件卸载时调用 — 在此清理资源 */
    fun onUninstall() {}
}

/**
 * 选择器宿主接口 — 插件通过它来扩展选择器。
 *
 * 提供注册自定义 RecyclerView 装饰器（如选中框、水印）、
 * 触摸拦截器（如自定义手势）、覆盖层 View（如 Loading/Empty/Error 状态）的方法。
 */
interface PickerHost {
    /** 注册条目装饰器（类似 [RecyclerView.ItemDecoration]） */
    fun registerItemDecoration(decoration: RecyclerView.ItemDecoration)

    /** 注册触摸事件拦截器 */
    fun registerTouchInterceptor(listener: RecyclerView.OnItemTouchListener)

    /** 注册选中状态变化回调 */
    fun onSelectionChanged(handler: (Set<Uri>) -> Unit)

    /** 添加覆盖层 View（如 Loading 动画） */
    fun addOverlay(view: android.view.View)

    /** 移除覆盖层 View */
    fun removeOverlay(view: android.view.View)
}
