package com.shyky.tech.photo.picker.compat.adapter

import androidx.recyclerview.widget.RecyclerView
import com.shyky.tech.photo.picker.compat.data.PagingController

/**
 * Tab 适配器注册表 — 管理 ViewPager2 中每个 Tab 页的 RecyclerView 和适配器。
 *
 * 使用共享的 [RecyclerView.RecycledViewPool] 来减少 ViewHolder 创建开销。
 * 在销毁时统一释放所有分页控制器。
 */
class TabAdapterRegistry {
    /** Tab 条目映射表（索引 → TabEntry） */
    private val entries = mutableMapOf<Int, TabEntry>()

    /** 所有 Tab 页共享的 ViewHolder 回收池 */
    private val sharedPool = RecyclerView.RecycledViewPool()

    /**
     * 单个 Tab 的注册条目。
     * @param rv 该 Tab 的 RecyclerView 实例
     * @param adapter 该 Tab 的选择器适配器
     * @param pagingController 该 Tab 的分页加载控制器
     * @param pageView 包装了 RecyclerView 的容器 View（用于 ViewPager2）
     */
    data class TabEntry(
        val rv: RecyclerView,
        val adapter: SelectableAdapter<*>,
        val pagingController: PagingController,
        val pageView: android.view.ViewGroup
    )

    /**
     * 注册一个 Tab 页。
     * @param index Tab 索引
     * @param rv 该 Tab 的 RecyclerView
     * @param adapter 适配器
     * @param pagingController 分页控制器
     * @param context 用于创建容器 View 的 Context
     * @return 包装后的容器 View，可直接添加到 ViewPager2
     */
    fun register(
        index: Int,
        rv: RecyclerView,
        adapter: SelectableAdapter<*>,
        pagingController: PagingController,
        context: android.content.Context
    ): android.view.ViewGroup {
        // ★ 使用共享回收池减少 ViewHolder 分配
        rv.setRecycledViewPool(sharedPool)
        val pageView = android.widget.FrameLayout(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(rv)
        }
        entries[index] = TabEntry(rv, adapter, pagingController, pageView)
        return pageView
    }

    /** 获取指定索引的条目 */
    fun getEntry(index: Int): TabEntry? = entries[index]

    /** 获取所有已注册的条目 */
    fun allEntries() = entries.values.toList()

    /** 释放所有分页控制器的资源 */
    fun dispose() {
        entries.values.forEach { it.pagingController.dispose() }
    }
}
