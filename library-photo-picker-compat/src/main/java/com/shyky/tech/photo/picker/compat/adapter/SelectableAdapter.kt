package com.shyky.tech.photo.picker.compat.adapter

import android.net.Uri
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 可选择的适配器接口 — 定义 Tab 页内与选择相关的完整契约。
 *
 * 实现此接口的适配器可以被 [SelectionManager] 和 [MultiSelectHelper] 管理。
 * 封装了 RecyclerView 适配器、选择状态管理、数据提交等所有功能。
 *
 * @param T 适配器管理的条目数据类型
 */
interface SelectableAdapter<T : Any> {
    // ═══════════ RecyclerView 集成 ═══════════

    /** 底层的 RecyclerView 适配器实例 */
    val recyclerAdapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>

    // ═══════════ 条目查询 ═══════════

    /** 指定位置是否可选择（Header、相机入口等不可选） */
    fun isSelectable(position: Int): Boolean

    /** 指定位置是否为相机入口 */
    fun isCameraPosition(position: Int): Boolean = false

    /** 获取条目的视图类型（TYPE_HEADER / TYPE_ITEM 等） */
    fun getItemViewType(position: Int): Int

    /** 获取条目的唯一 ID（用于 stable ID） */
    fun getItemId(position: Int): Long

    /** 获取条目的 URI */
    fun getItemUri(position: Int): Uri?

    /** 获取条目的 MIME 类型 */
    fun getItemMimeType(position: Int): String? = null

    // ═══════════ 选择操作 ═══════════

    /** 切换指定位置的选中状态（选中↔未选中） */
    fun toggle(position: Int)

    /** 选中指定位置 */
    fun select(position: Int)

    /** 取消选中指定位置 */
    fun unselect(position: Int)

    /** 已选中的 URI 集合（线程安全快照） */
    val selectedUris: Set<Uri>

    /** 已选中的数量 */
    val selectedCount: Int

    /** 最大可选数量 */
    var maxSelectCount: Int

    /** 选中数量变化回调 */
    var onSelectionChanged: ((count: Int) -> Unit)?

    // ═══════════ 生命周期 ═══════════

    /** 适配器挂载到 RecyclerView 时调用 */
    fun onAttached(recyclerView: RecyclerView) {}

    /** 适配器从 RecyclerView 卸载时调用 */
    fun onDetached() {}

    /** 保存选中状态到 Bundle（用于配置变更恢复） */
    fun saveState(): Bundle

    /** 从 Bundle 恢复选中状态 */
    fun restoreState(state: Bundle?)

    // ═══════════ 数据管理 ═══════════

    /** 全量替换数据列表（首页加载） */
    fun submitList(items: List<Any>)

    /** 追加一页数据（分页加载） */
    fun appendPage(items: List<Any>)

    /** 当前条目总数 */
    val itemCount: Int

    // ═══════════ 布局 ═══════════

    /** 获取 SpanSizeLookup（控制 Header 占满宽、Item 占一格） */
    fun spanSizeLookup(): GridLayoutManager.SpanSizeLookup {
        return object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = 1
        }
    }

    /** 创建条目装饰器（如选中框），默认无 */
    fun createItemDecoration(): RecyclerView.ItemDecoration? = null

    // ═══════════ 事件 ═══════════

    /** 条目点击回调 */
    var onItemClick: ((position: Int) -> Unit)?

    /** 相机入口点击回调 */
    var onCameraClick: (() -> Unit)?
}
