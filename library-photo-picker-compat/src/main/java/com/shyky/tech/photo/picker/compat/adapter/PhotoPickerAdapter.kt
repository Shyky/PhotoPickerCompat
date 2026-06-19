package com.shyky.tech.photo.picker.compat.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.shyky.tech.photo.picker.compat.R
import com.shyky.tech.photo.picker.compat.data.MediaItem
import com.shyky.tech.photo.picker.compat.databinding.ItemMediaGridBinding
import com.shyky.tech.photo.picker.compat.databinding.ItemSectionHeaderBinding
import com.shyky.tech.photo.picker.compat.util.DateSectionHelper

/**
 * 独立的图片选择器适配器 — 支持相机入口、日期分组和多选状态管理。
 *
 * 与 [GroupedAdapter] + [DefaultMediaAdapter] 组合的区别：
 * - 本适配器是一个完整的、自包含的实现，包含多选状态管理
 * - 内置 [LinkedHashSet] 保存选中 URI 和选择顺序
 * - 支持 Payload 局部刷新（多选时跳过图片重载）
 * - 包含相机入口条目（id == -1L）
 *
 * @param context 用于创建 LayoutInflater 的 Context
 */
class PhotoPickerAdapter(
    private val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        /** 相机入口类型 */
        const val TYPE_CAMERA = 0

        /** 媒体条目类型 */
        const val TYPE_MEDIA = 1

        /** 日期分组标题类型 */
        const val TYPE_SECTION = 2

        /** 格式化视频时长为 "MM:SS" */
        fun formatDuration(ms: Long): String {
            val s = ms / 1000
            val min = s / 60
            val sec = s % 60
            return "%02d:%02d".format(min, sec)
        }
    }

    /** 数据列表 */
    private val data = mutableListOf<Any>()

    /** 已选 URI 集合 — LinkedHashSet 保持插入顺序 */
    val selectedUris = LinkedHashSet<Uri>()

    /** 选择顺序映射 — URI → 序号（用于显示选中编号） */
    private val selectionOrder = LinkedHashMap<Uri, Int>()

    /** 下一个选中序号 */
    private var nextOrder = 0

    /** 最大可选数量 */
    private var maxCount = 99

    var onSelectionChanged: ((Int) -> Unit)? = null
    var onMaxExceeded: (() -> Unit)? = null
    var onCameraClick: (() -> Unit)? = null

    init {
        // ★ 启用固定尺寸和稳定 ID，大幅减少 rebind
        setHasStableIds(true)
    }

    override fun getItemId(pos: Int): Long = when (val obj = data[pos]) {
        is DateSectionHelper.SectionHeader -> obj.text.hashCode().toLong() + 10_000_000L
        is MediaItem -> obj.id
        else -> pos.toLong()
    }

    fun setMaxCount(n: Int) {
        maxCount = n
    }

    /** 全量替换数据列表（首页加载） — 智能 diff 减少 notify 范围 */
    fun submitList(items: List<Any>) {
        val old = snapshot()
        data.clear()
        data.addAll(items)
        val os = old.size
        val ns = data.size
        if (os == 0) {
            notifyItemRangeInserted(0, ns)
            return
        }
        if (os == ns && old == data) return // ★ 无变化，跳过
        // ★ 找到首个不同的位置，只 notify 变化部分
        var same = 0
        while (same < os.coerceAtMost(ns)) {
            if (!equiv(old[same], data[same])) break
            same++
        }
        if (os > same) notifyItemRangeRemoved(same, os - same)
        if (ns > same) notifyItemRangeInserted(same, ns - same)
    }

    /** 快照当前数据列表 */
    private fun snapshot() = data.toMutableList()

    /** 判断两个条目是否等价（用于 diff） */
    private fun equiv(a: Any, b: Any): Boolean = when (a) {
        is DateSectionHelper.SectionHeader ->
            b is DateSectionHelper.SectionHeader && a.text == b.text

        is MediaItem ->
            b is MediaItem && a.id == b.id

        else -> a === b
    }

    /** 追加一页数据 */
    fun addPage(items: List<Any>) {
        val s = data.size
        data.addAll(items)
        notifyItemRangeInserted(s, items.size)
    }

    @Synchronized
    fun isSelected(pos: Int): Boolean {
        val obj = data.getOrNull(pos) ?: return false
        return obj is MediaItem && obj.uri in selectedUris
    }

    @Synchronized
    fun isFull() = selectedUris.size >= maxCount

    /** 翻转指定位置的选中状态 */
    @Synchronized
    fun toggleSelection(pos: Int) {
        val obj = data.getOrNull(pos) ?: return
        if (obj !is MediaItem) return
        val uri = obj.uri
        if (uri in selectedUris) {
            selectedUris.remove(uri)
            selectionOrder.remove(uri)
        } else if (selectedUris.size < maxCount) {
            selectedUris.add(uri)
            selectionOrder[uri] = nextOrder++
        }
        onSelectionChanged?.invoke(selectedUris.size)
    }

    @Synchronized
    fun select(pos: Int) {
        val obj = data.getOrNull(pos) ?: return
        if (obj !is MediaItem) return
        val uri = obj.uri
        if (uri !in selectedUris && selectedUris.size < maxCount) {
            selectedUris.add(uri)
            selectionOrder[uri] = nextOrder++
            onSelectionChanged?.invoke(selectedUris.size)
        }
    }

    @Synchronized
    fun unselect(pos: Int) {
        val obj = data.getOrNull(pos) ?: return
        if (obj !is MediaItem) return
        val uri = obj.uri
        if (uri in selectedUris) {
            selectedUris.remove(uri)
            selectionOrder.remove(uri)
            onSelectionChanged?.invoke(selectedUris.size)
        }
    }

    /** 通过 URI 取消选中（并刷新对应位置） */
    @Synchronized
    fun unselectByUri(uri: Uri) {
        selectedUris.remove(uri)
        selectionOrder.remove(uri)
        onSelectionChanged?.invoke(selectedUris.size)
        for (i in data.indices) {
            val obj = data[i]
            if (obj is MediaItem && obj.uri == uri) {
                notifyItemChanged(i)
                break
            }
        }
    }

    /** 获取 URI 的选择序号 */
    @Synchronized
    fun selectedOrder(uri: Uri): Int = selectionOrder[uri] ?: -1

    override fun getItemViewType(pos: Int): Int = when (val obj = data[pos]) {
        is DateSectionHelper.SectionHeader -> TYPE_SECTION
        is MediaItem -> if (obj.id == -1L) TYPE_CAMERA else TYPE_MEDIA
        else -> TYPE_MEDIA
    }

    override fun getItemCount() = data.size

    /** 预计算的条目正方形边长（px）— RecyclerView 宽度 / 3 */
    private var itemSize = 0

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        // ★ 用 RecyclerView 的实际宽度计算正方形边长（不含任意 padding）
        if (itemSize == 0 && parent.width > 0) {
            itemSize = parent.width / 3
        }
        return when (vt) {
            TYPE_SECTION -> SectionVH(
                ItemSectionHeaderBinding.inflate(inflater, parent, false)
            )

            else -> {
                val binding = ItemMediaGridBinding.inflate(inflater, parent, false)
                if (itemSize > 0) {
                    binding.root.layoutParams.height = itemSize
                }
                MediaVH(binding)
            }
        }
    }

    // ═══════════ Payload 局部刷新 ═══════════

    /** Payload 标记 — 表示仅刷新选中状态，跳过图片重载 */
    private val PAYLOAD_CHECK = Any()

    override fun onBindViewHolder(
        h: RecyclerView.ViewHolder,
        pos: Int,
        payloads: MutableList<Any>
    ) {
        if (h is MediaVH && payloads.contains(PAYLOAD_CHECK)) {
            val obj = data[pos] as? MediaItem ?: return
            h.bindCheckOnly(obj) // ★ 仅更新选中状态，不触发 Coil
            return
        }
        onBindViewHolder(h, pos)
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
        val obj = data[pos]
        when (obj) {
            is DateSectionHelper.SectionHeader -> (h as SectionVH).bind(obj.text)
            is MediaItem -> (h as MediaVH).bind(obj)
        }
    }

    /** 通知单个位置的选中状态变化（Payload 局部刷新） */
    fun notifyCheckChanged(pos: Int) {
        notifyItemChanged(pos, PAYLOAD_CHECK)
    }

    /** 通知范围内的选中状态变化（滑动多选时减少 notify 次数） */
    fun notifyCheckRangeChanged(start: Int, count: Int) {
        notifyItemRangeChanged(start, count, PAYLOAD_CHECK)
    }

    /** 缓存的 SpanSizeLookup — by lazy 避免每次 layout pass 分配新对象 */
    val spanSizeLookup by lazy {
        object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(pos: Int) =
                if (getItemViewType(pos) == TYPE_SECTION) 3 else 1
        }
    }

    // ═══════════ ViewHolder ═══════════

    /** 日期分组标题 ViewHolder */
    inner class SectionVH(
        private val binding: ItemSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(t: String) {
            binding.tvSectionLabel.text = t
        }
    }

    /** 媒体条目 ViewHolder — 包含缩略图、选中框、视频标识等 */
    inner class MediaVH(
        private val b: ItemMediaGridBinding
    ) : RecyclerView.ViewHolder(b.root) {
        val image: ImageView = b.ivThumb
        private val mask: View = b.viewMask
        private val checkRing: View = b.viewCheckSelected
        private val checkNum: TextView = b.tvCheckNum
        private val playIcon: ImageView = b.ivPlayIcon
        private val duration: TextView = b.tvDuration
        private val camera: ImageView = b.ivCamera

        /** 完整绑定（首次显示 + 数据变化时） */
        fun bind(item: MediaItem) {
            if (item.id == -1L) {
                // ★ 相机入口：隐藏图片，显示相机图标
                image.visibility = View.GONE; mask.visibility = View.GONE
                checkRing.visibility = View.GONE; checkNum.visibility = View.GONE
                playIcon.visibility = View.GONE; duration.visibility = View.GONE
                camera.visibility = View.VISIBLE
            } else {
                camera.visibility = View.GONE; image.visibility = View.VISIBLE
                // ★ Coil 异步加载缩略图 — 200px + RGB_565 减少 GPU 纹理内存
                image.load(item.uri) {
                    size(300)
                    crossfade(false)            // 无淡入动画，减少合成层
                    allowRgb565(true)           // RGB_565 纹理内存为 ARGB_8888 的一半
                    diskCachePolicy(CachePolicy.ENABLED)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    if (android.os.Build.VERSION.SDK_INT < 28)
                        allowHardware(false)    // API < 28 不支持硬件位图
                }

                val sel = item.uri in selectedUris
                mask.visibility = if (sel) View.VISIBLE else View.GONE
                // ★ setBackgroundResource 每次创建新的 Drawable — 不会跨 VH 共享状态
                checkRing.setBackgroundResource(if (sel) filledRingRes else hollowRingRes)
                checkNum.visibility = if (sel) View.VISIBLE else View.GONE
                if (sel) {
                    checkNum.scaleX = 1f; checkNum.scaleY = 1f
                    val idx = selectedOrder(item.uri)
                    if (idx >= 0) checkNum.text = "${idx + 1}"
                }
                if (item.isVideo) {
                    playIcon.visibility = View.VISIBLE
                    duration.visibility = View.VISIBLE
                    duration.text = formatDuration(item.durationMs)
                } else {
                    playIcon.visibility = View.GONE
                    duration.visibility = View.GONE
                }
            }
        }

        /** 快速局部刷新 — 仅更新选中状态，跳过 Coil 图片重载 */
        fun bindCheckOnly(item: MediaItem) {
            if (item.id == -1L) return
            val sel = item.uri in selectedUris
            mask.visibility = if (sel) View.VISIBLE else View.GONE
            checkRing.setBackgroundResource(if (sel) filledRingRes else hollowRingRes)
            checkNum.visibility = if (sel) View.VISIBLE else View.GONE
            if (sel) {
                checkNum.scaleX = 1f; checkNum.scaleY = 1f
                val idx = selectedOrder(item.uri)
                if (idx >= 0) checkNum.text = "${idx + 1}"
            }
        }
    }

    /** 缓存的 Drawable 资源 ID — 避免每次 bind 都调用 resources.getIdentifier */
    private val hollowRingRes = R.drawable.bg_check_hollow
    private val filledRingRes = R.drawable.bg_check_filled
}
