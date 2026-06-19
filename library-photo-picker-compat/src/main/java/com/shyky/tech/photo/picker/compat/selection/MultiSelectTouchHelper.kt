package com.shyky.tech.photo.picker.compat.selection

import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import com.shyky.tech.photo.picker.compat.adapter.PhotoPickerAdapter

/**
 * 多选触摸处理器 — 为 [PhotoPickerAdapter] 提供长按进入多选 + 滑动连续选择。
 *
 * **安装方式**：调用 [attach] 注册到 RecyclerView 的 OnTouchListener。
 * 不拦截滚动（恒返回 false），多选在 [MotionEvent.ACTION_MOVE] 中执行。
 *
 * **工作流程**：
 * 1. 列表中已有选中项 + 手指在媒体条目上方滑动 → 进入滑动多选模式
 * 2. 滑过的每个条目依次翻转选择状态（已选中→取消，未选中→选中）
 * 3. 手指靠近屏幕边缘时自动滚动列表（边缘滚动）
 * 4. 抬起手指 → 结束滑动多选
 *
 * @param rv 目标 RecyclerView
 * @param adapter 关联的 PhotoPickerAdapter
 * @param onMaxExceeded 超出最大选择数时的回调
 */
class MultiSelectTouchHelper(
    private val rv: RecyclerView,
    private val adapter: PhotoPickerAdapter,
    private val onMaxExceeded: () -> Unit
) {
    /** 是否正在滑动多选 */
    private var sweeping = false

    /** 上一次处理的位置（防止重复翻转同一个条目） */
    private var lastPos = RecyclerView.NO_POSITION

    /** 边缘自动滚动器 */
    private val autoScroll = AutoScroller(rv)

    /** 手势检测器 — 处理单击选中/取消 */
    private val gesture =
        GestureDetector(rv.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val p = posAt(e.x, e.y)
                if (p < 0) return false
                when (adapter.getItemViewType(p)) {
                    PhotoPickerAdapter.TYPE_CAMERA -> adapter.onCameraClick?.invoke()
                    PhotoPickerAdapter.TYPE_MEDIA -> {
                        adapter.toggleSelection(p)
                        adapter.notifyCheckChanged(p) // Payload 局部刷新
                    }
                }
                return true
            }
        })

    /**
     * 安装触摸监听器到 RecyclerView。
     * 应在 Activity/Fragment 的 setup 阶段调用。
     */
    fun attach() {
        rv.setOnTouchListener { _, e ->
            gesture.onTouchEvent(e)
            when (e.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    sweeping = false
                    lastPos = RecyclerView.NO_POSITION
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!sweeping) {
                        // ★ 进入滑动多选模式的条件：已有选中项 + 手指在媒体条目上方
                        if (adapter.selectedUris.isEmpty()) return@setOnTouchListener false
                        val p = posAt(e.x, e.y)
                        if (p < 0 || adapter.getItemViewType(p) != PhotoPickerAdapter.TYPE_MEDIA)
                            return@setOnTouchListener false
                        sweeping = true
                        lastPos = p
                        toggleItemAt(p)
                        return@setOnTouchListener false
                    }
                    // ★ 滑动多选模式中
                    autoScroll.check(e.y)
                    val p = posAt(e.x, e.y)
                    if (p < 0 || p == lastPos) return@setOnTouchListener false
                    if (adapter.getItemViewType(p) != PhotoPickerAdapter.TYPE_MEDIA)
                        return@setOnTouchListener false

                    toggleItemAt(p)
                    // ★ 批量刷新范围（避免逐个 notifyItemChanged 的开销）
                    val lo = minOf(lastPos.coerceAtLeast(0), p)
                    val hi = maxOf(lastPos.coerceAtLeast(0), p)
                    adapter.notifyCheckRangeChanged(lo, hi - lo + 1)
                    lastPos = p
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (sweeping) {
                        sweeping = false
                        lastPos = RecyclerView.NO_POSITION
                        autoScroll.stop()
                    }
                }
            }
            false // ★ 永不拦截 — RecyclerView 保持完整滚动惯性
        }
    }

    /** 根据触摸坐标获取 RecyclerView 中的 adapter position */
    private fun posAt(x: Float, y: Float): Int {
        val child = rv.findChildViewUnder(x, y) ?: return RecyclerView.NO_POSITION
        return rv.getChildAdapterPosition(child)
    }

    /**
     * 翻转指定位置的选中状态：已选中→取消，未选中→选中（不超过 maxCount）。
     */
    private fun toggleItemAt(p: Int) {
        if (adapter.isSelected(p))
            adapter.unselect(p)
        else {
            if (adapter.isFull()) {
                onMaxExceeded()
                return
            }
            adapter.select(p)
        }
    }

    /**
     * 边缘自动滚动器 — 手指靠近屏幕顶部/底部时自动滚动列表。
     * 每 16ms（60fps）触发一次滚动，直到手指离开边缘区域。
     */
    private class AutoScroller(private val rv: RecyclerView) {
        private val h = Handler(Looper.getMainLooper())
        private var running = false

        /**
         * 检查手指位置是否需要启动/停止自动滚动。
         * @param touchY 手指的 Y 坐标（相对于 RecyclerView）
         */
        fun check(touchY: Float) {
            val dy = when {
                touchY < 100 && rv.canScrollVertically(-1) -> -30  // 靠近顶部 → 向上滚动
                touchY > rv.height - 100 && rv.canScrollVertically(1) -> 30  // 靠近底部 → 向下滚动
                else -> {
                    stop()
                    return
                }
            }
            if (!running) {
                running = true
                tick(dy)
            }
        }

        /** 停止自动滚动 */
        fun stop() {
            running = false
            h.removeCallbacksAndMessages(null)
        }

        /** 每帧滚动 tick（16ms 间隔 ≈ 60fps） */
        private fun tick(dy: Int) {
            if (!running) return
            rv.scrollBy(0, dy)
            h.postDelayed({ tick(dy) }, 16)
        }
    }
}
