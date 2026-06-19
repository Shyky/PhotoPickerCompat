package com.shyky.tech.photo.picker.compat.config

import android.provider.MediaStore

/**
 * 布局适配器接口 — 控制网格列数、间距和排序方式。
 *
 * 实现此接口可根据设备尺寸（如平板/手机）动态调整网格布局。
 */
interface LayoutAdaptor {
    /**
     * 计算列数。
     * @param widthPx 可用宽度（像素）
     * @param density 屏幕密度
     * @return 列数
     */
    fun columnCount(widthPx: Int, density: Float): Int

    /** 网格间的间距（dp） */
    val itemSpacingDp: Int

    /** MediaStore 查询的排序方式，默认按修改时间倒序 */
    fun sortOrder(): String = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

    companion object {
        /**
         * 默认布局适配器：
         * - 宽度 ≥ 600dp（平板）→ 4 列
         * - 宽度 < 600dp（手机）→ 3 列
         * - 间距 1dp
         */
        val DEFAULT = object : LayoutAdaptor {
            override fun columnCount(widthPx: Int, density: Float) =
                if (widthPx / density >= 600f) 4 else 3

            override val itemSpacingDp = 1
        }
    }
}
