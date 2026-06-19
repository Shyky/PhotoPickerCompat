package com.shyky.tech.photo.picker.compat.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.SparseBooleanArray
import androidx.recyclerview.widget.RecyclerView

/**
 * 选中状态装饰器 — 在已选中的条目上绘制蓝色半透明覆盖和描边。
 *
 * 关键优化：
 * - **Early-exit**：无选中项时 [onDraw] 直接返回，零绘制开销
 * - 跳过 Header 类型的条目（不绘制选择框）
 * - 使用预创建的 Paint 对象避免 onDraw 中分配
 *
 * @param selectedPositions 选中位置映射（position → Boolean）
 * @param headerViewType Header 类型的 viewType 值
 */
class SelectionItemDecoration(
    private val selectedPositions: SparseBooleanArray,
    private val headerViewType: Int
) : RecyclerView.ItemDecoration() {

    /** 选中填充画笔 — 半透明蓝色 */
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1A0066FF
        style = Paint.Style.FILL
    }

    /** 选中边框画笔 — 实心蓝色 3px */
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0066FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    /** 复用 Rect 对象，避免 onDraw 中分配 */
    private val bounds = Rect()

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        // ★ Early-exit：无选中项时直接返回，零绘制开销
        if (selectedPositions.size() == 0) return
        val childCount = parent.childCount
        if (childCount == 0) return

        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            // ★ 跳过 Header 类型 — 不对日期标题绘制选择框
            if (parent.adapter?.getItemViewType(position) == headerViewType) continue

            if (selectedPositions[position]) {
                parent.getDecoratedBoundsWithMargins(child, bounds)
                c.drawRect(bounds, selectedPaint) // 填充层
                c.drawRect(bounds, borderPaint)    // 描边层
            }
        }
    }

    /** 是否有需要重绘的装饰项（用于判断是否需要 invalidate） */
    fun needsRedraw() = selectedPositions.size() > 0
}
