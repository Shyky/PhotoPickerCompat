package com.shyky.tech.photo.picker.compat.selection

import android.os.Handler
import android.os.Looper
import android.util.SparseBooleanArray
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

/**
 * 多选触摸处理器 — 为 [GroupedAdapter] 提供长按多选 + 滑动连续选择 + 边缘自动滚动。
 *
 * **使用方式**：
 * 1. 构造时自动注册到 RecyclerView（添加 ItemDecoration + OnItemTouchListener）
 * 2. 无需外部调用，手势自动触发
 * 3. 销毁时调用 [detach] 移除监听器
 *
 * **手势行为**：
 * - **长按**：进入多选模式 + 翻转当前条目选中状态 + 震动反馈
 * - **点击**（多选模式下）：翻转选中状态
 * - **滑动**（多选模式下）：连续翻转滑过的条目
 * - **边缘滚动**：手指靠近顶部/底部时自动滚动列表
 *
 * @param recyclerView 目标 RecyclerView
 * @param headerViewType Header 类型的 viewType（跳过 Header，不参与多选）
 * @param idProvider 位置 → 条目 ID 的映射函数（用于状态恢复）
 */
class MultiSelectHelper(
    private val recyclerView: RecyclerView,
    private val headerViewType: Int,
    private val idProvider: (position: Int) -> Long
) {
    /** 是否处于多选模式 */
    var isMultiSelectMode = false
        private set

    /** 选中位置映射 — SparseBooleanArray 避免装箱开销 */
    val selectedPositions = SparseBooleanArray()

    /** 选中数量变化回调 */
    var onSelectionChanged: ((count: Int) -> Unit)? = null

    /** 多选模式切换回调 */
    var onModeChanged: ((inMode: Boolean) -> Unit)? = null

    /**
     * 单个位置翻转时的回调 — 用于触发 Payload 局部刷新。
     * 调用方可直接调用 `adapter.notifySelectionChanged(position)`。
     */
    var onPositionToggled: ((position: Int) -> Unit)? = null

    // ═══════════ 内部状态 ═══════════

    /** 是否正在滑动多选 */
    private var isSwipeSelecting = false

    /** 上一次滑动选中的位置（防止重复翻转） */
    private var lastSelectedPosition = RecyclerView.NO_POSITION

    /** 本次滑动操作的初始选择方向（true=选中, false=取消） */
    private var initialSelectionState = false

    /** 边缘自动滚动 Handler */
    private val scrollHandler = Handler(Looper.getMainLooper())

    /** 边缘自动滚动 Runnable */
    private val autoScrollRunnable = AutoScrollRunnable()

    /** 手势检测器：处理长按和点击 */
    private var gestureDetector: GestureDetector

    /** 选中装饰器：绘制选中框 */
    private val decoration = SelectionItemDecoration(selectedPositions, headerViewType)

    /** 触摸监听器：拦截滑动事件 */
    private val touchListener: RecyclerView.OnItemTouchListener

    init {
        // ★ 构造手势检测器
        gestureDetector = GestureDetector(
            recyclerView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    val pos = getPositionUnder(e.x, e.y) ?: return
                    // ★ 跳过 Header 条目
                    if (getItemViewType(pos) == headerViewType) return
                    if (!isMultiSelectMode) {
                        isMultiSelectMode = true
                        onModeChanged?.invoke(true)
                    }
                    toggleSelection(pos)
                    recyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (!isMultiSelectMode) return false
                    val pos = getPositionUnder(e.x, e.y) ?: return false
                    if (getItemViewType(pos) == headerViewType) return false
                    toggleSelection(pos)
                    recyclerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    return true
                }
            })

        // ★ 构造触摸监听器
        touchListener = object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (isMultiSelectMode) {
                            // ★ 多选模式下禁止父 View 拦截（防止与 BottomSheet 手势冲突）
                            rv.parent.requestDisallowInterceptTouchEvent(true)
                            val pos = getPositionUnder(e.x, e.y)
                            if (pos != null && getItemViewType(pos) != headerViewType) {
                                isSwipeSelecting = true
                                lastSelectedPosition = pos
                                // ★ 记录初始选择方向，滑动过程中保持一致
                                initialSelectionState = !selectedPositions[pos]
                                toggleSelection(pos)
                                return true
                            }
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (isSwipeSelecting) {
                            handleMove(e)
                            return true
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isMultiSelectMode)
                            rv.parent.requestDisallowInterceptTouchEvent(false)
                        if (isSwipeSelecting) {
                            stopAutoScroll()
                            isSwipeSelecting = false
                            lastSelectedPosition = RecyclerView.NO_POSITION
                        }
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) {}
        }

        // ★ 注册装饰器和触摸监听器
        recyclerView.addItemDecoration(decoration)
        recyclerView.addOnItemTouchListener(touchListener)
    }

    // ═══════════ 核心选择逻辑 ═══════════

    /** 处理滑动多选中的手指移动 */
    private fun handleMove(e: MotionEvent) {
        val child = recyclerView.findChildViewUnder(e.x, e.y) ?: return
        val position = recyclerView.getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION || getItemViewType(position) == headerViewType)
            return
        if (position == lastSelectedPosition) return // ★ 跳过重复位置

        val shouldSelect = initialSelectionState
        if (selectedPositions[position] != shouldSelect) {
            selectedPositions.put(position, shouldSelect)
            recyclerView.invalidateItemDecorations()
            onPositionToggled?.invoke(position) // ★ Payload 局部刷新
            onSelectionChanged?.invoke(selectedPositions.size())
        }
        lastSelectedPosition = position
        startAutoScrollIfNeeded(e)
    }

    /** 翻转指定位置的选中状态 */
    fun toggleSelection(position: Int) {
        if (getItemViewType(position) == headerViewType) return
        val newState = !selectedPositions[position]
        selectedPositions.put(position, newState)
        recyclerView.invalidateItemDecorations()
        onPositionToggled?.invoke(position)
        onSelectionChanged?.invoke(selectedPositions.size())
    }

    /** 检查手指是否靠近边缘，按需启动自动滚动 */
    private fun startAutoScrollIfNeeded(e: MotionEvent) {
        val topEdge = recyclerView.height * 0.15f   // 上 15% 区域
        val bottomEdge = recyclerView.height * 0.85f // 下 15% 区域
        val scrollDistance = when {
            e.y < topEdge -> -12      // 靠近顶部 → 向上滚动
            e.y > bottomEdge -> 12    // 靠近底部 → 向下滚动
            else -> {
                stopAutoScroll()
                return
            }
        }
        if (!autoScrollRunnable.isRunning) {
            autoScrollRunnable.scrollDistance = scrollDistance
            scrollHandler.post(autoScrollRunnable)
        }
    }

    /** 停止边缘自动滚动 */
    private fun stopAutoScroll() {
        scrollHandler.removeCallbacks(autoScrollRunnable)
        autoScrollRunnable.isRunning = false
    }

    /** 边缘自动滚动 Runnable — 每 16ms 触发一次滚动 */
    private inner class AutoScrollRunnable : Runnable {
        var scrollDistance: Int = 0
        var isRunning = false
        override fun run() {
            isRunning = true
            recyclerView.scrollBy(0, scrollDistance)
            if (isSwipeSelecting)
                scrollHandler.postDelayed(this, 16L) // ~60fps
            else
                isRunning = false
        }
    }

    // ═══════════ 状态管理 ═══════════

    /** 从条目 ID 集合恢复选中状态（用于配置变更重建） */
    fun restoreSelection(itemIds: Set<Long>) {
        selectedPositions.clear()
        val adapter = recyclerView.adapter ?: return
        for (pos in 0 until adapter.itemCount) {
            val id = idProvider(pos)
            if (id in itemIds) selectedPositions.put(pos, true)
        }
        recyclerView.invalidateItemDecorations()
        onSelectionChanged?.invoke(selectedPositions.size())
    }

    /** 获取已选中条目的 ID 集合 */
    fun getSelectedItemIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        for (i in 0 until selectedPositions.size()) {
            val pos = selectedPositions.keyAt(i)
            if (selectedPositions.valueAt(i))
                ids.add(idProvider(pos))
        }
        return ids
    }

    /** 进入多选模式 */
    fun enterMultiSelectMode() {
        if (!isMultiSelectMode) {
            isMultiSelectMode = true
            onModeChanged?.invoke(true)
        }
    }

    /** 退出多选模式并清除选中状态 */
    fun exitMultiSelectMode() {
        if (isMultiSelectMode) {
            isMultiSelectMode = false
            clearSelection()
            stopAutoScroll()
            isSwipeSelecting = false
            onModeChanged?.invoke(false)
        }
    }

    /** 清除所有选中状态 */
    fun clearSelection() {
        val hadItems = selectedPositions.size() > 0
        selectedPositions.clear()
        if (hadItems) recyclerView.invalidateItemDecorations()
        onSelectionChanged?.invoke(0)
    }

    /** 校验选中位置的有效性（清理越界的位置） */
    fun validatePositions() {
        for (i in selectedPositions.size() - 1 downTo 0) {
            val pos = selectedPositions.keyAt(i)
            if (pos < 0 || pos >= (recyclerView.adapter?.itemCount ?: 0))
                selectedPositions.removeAt(i)
        }
        recyclerView.invalidateItemDecorations()
    }

    /**
     * ★ 从头部裁剪数据后重建位置映射。
     * 因为裁剪后全部数据向前移动，需要根据条目 ID 重新映射位置。
     */
    fun rebuildAfterTrim() {
        val ids = getSelectedItemIds()
        selectedPositions.clear()
        val adapter = recyclerView.adapter ?: return
        for (pos in 0 until adapter.itemCount) {
            val id = idProvider(pos)
            if (id in ids) selectedPositions.put(pos, true)
        }
        recyclerView.invalidateItemDecorations()
        onSelectionChanged?.invoke(selectedPositions.size())
    }

    /** 移除所有监听器和装饰器 — 在销毁时调用防止泄漏 */
    fun detach() {
        stopAutoScroll()
        recyclerView.removeOnItemTouchListener(touchListener)
        recyclerView.removeItemDecoration(decoration)
    }

    /** 获取指定位置的 viewType */
    private fun getItemViewType(position: Int): Int =
        recyclerView.adapter?.getItemViewType(position) ?: 0

    /** 根据触摸坐标获取 adapter position */
    private fun getPositionUnder(x: Float, y: Float): Int? {
        val child = recyclerView.findChildViewUnder(x, y) ?: return null
        val pos = recyclerView.getChildAdapterPosition(child)
        return if (pos != RecyclerView.NO_POSITION) pos else null
    }
}
