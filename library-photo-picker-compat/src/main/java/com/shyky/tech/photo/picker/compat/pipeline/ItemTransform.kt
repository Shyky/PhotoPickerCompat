package com.shyky.tech.photo.picker.compat.pipeline

import com.shyky.tech.photo.picker.compat.data.MediaItem
import java.io.File

/**
 * 条目变换接口 — 在数据加载后、显示前对每个条目进行过滤或转换。
 *
 * fun interface 支持 SAM 转换（Lambda）：
 * ```kotlin
 * val filter = ItemTransform { item, _ -> if (someCondition) item else null }
 * ```
 *
 * 返回 null 表示过滤掉该条目（不显示）。
 */
fun interface ItemTransform {
    /**
     * 变换单个条目。
     * @param item 原始条目数据
     * @param position 条目在列表中的位置
     * @return 变换后的条目，返回 null 则过滤掉该条目
     */
    fun transform(item: Any, position: Int): Any?
}

/**
 * GIF 过滤器 — 通过文件扩展名过滤掉 GIF 图片。
 *
 * 适用于不希望用户选择动画图片的场景。
 */
class GifFilter : ItemTransform {
    override fun transform(item: Any, position: Int): Any? {
        val media = item as? MediaItem ?: return item
        // ★ 通过路径后缀 ".gif" 判断
        return if (media.path.endsWith(".gif", ignoreCase = true)) null else item
    }
}

/**
 * 文件大小过滤器 — 过滤掉超过指定大小的媒体文件。
 *
 * **重要：** 此变换每次都对文件执行 I/O（stat），
 * 必须在后台线程（如 [kotlinx.coroutines.Dispatchers.IO]）上运行。
 * 在主线程上运行会导致列表滑动时掉帧。
 *
 * @param maxBytes 最大文件字节数，超过此值将被过滤
 */
class SizeFilter(private val maxBytes: Long) : ItemTransform {
    override fun transform(item: Any, position: Int): Any? {
        val media = item as? MediaItem ?: return item
        val file = File(media.path)
        return if (file.isFile && file.length() > maxBytes) null else item
    }
}
