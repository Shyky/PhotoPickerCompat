package com.shyky.tech.photo.picker.compat.adapter

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import com.shyky.tech.photo.picker.compat.selection.SelectionManager

/**
 * 默认的媒体适配器 — 实现 [SelectableAdapter] 接口的完整实现。
 *
 * 内部使用 [GroupedAdapter] 处理分组显示（日期标题 + 媒体条目），
 * 使用 [SelectionManager] 管理线程安全的选择状态。
 * ★ 所有选中状态只通过 [SelectionManager] 读写，由 [SelectionManager._selected] 作为唯一数据源。
 *
 * 适配器可独立保存/恢复选中状态到 [Bundle]（用于配置变更如屏幕旋转）。
 *
 * @param context 用于创建 LayoutInflater 的 Context
 */
class DefaultMediaAdapter(
    context: Context
) : SelectableAdapter<GroupedAdapter.Item> {

    /** 核心适配器 — 处理 Header + Item 分组显示和 Payload 局部刷新 */
    private val groupedAdapter = GroupedAdapter(
        mutableListOf(),
        selectedChecker = this::isSelectedImpl
    )

    /** 选择状态管理器 — 线程安全，唯一数据源 */
    private var selectionManager: SelectionManager? = null

    /** 在 [onAttached] 之前调用的 restoreState 暂存于此 */
    private var pendingRestoreIds: LongArray? = null

    // ═══════════ SelectableAdapter 实现 ═══════════

    override val recyclerAdapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>
        get() = groupedAdapter

    override val selectedUris: Set<Uri>
        get() = selectionManager?.selectedUris ?: emptySet()

    override val selectedCount: Int
        get() = selectionManager?.count ?: 0

    override var maxSelectCount: Int = 99
    override var onSelectionChanged: ((Int) -> Unit)? = null
    override var onItemClick: ((Int) -> Unit)? = null
    override var onCameraClick: (() -> Unit)? = null

    override fun isSelectable(position: Int) =
        groupedAdapter.getItemViewType(position) != GroupedAdapter.TYPE_HEADER

    override fun isCameraPosition(position: Int) = false
    override fun getItemViewType(position: Int) = groupedAdapter.getItemViewType(position)
    override fun getItemId(position: Int) = groupedAdapter.getItemIdAt(position)

    override fun getItemUri(position: Int): Uri? {
        val obj = groupedAdapter.items.getOrNull(position)
        return (obj as? GroupedAdapter.Item)?.uri
    }

    override fun getItemMimeType(position: Int): String? = null

    override fun toggle(position: Int) {
        val uri = getItemUri(position) ?: return
        selectionManager?.toggle(uri, position)
        groupedAdapter.notifySelectionChanged(position)
    }

    override fun select(position: Int) {
        val uri = getItemUri(position) ?: return
        selectionManager?.select(uri)
        groupedAdapter.notifySelectionChanged(position)
    }

    override fun unselect(position: Int) {
        val uri = getItemUri(position) ?: return
        selectionManager?.unselect(uri)
        groupedAdapter.notifySelectionChanged(position)
    }

    /** ★ [selectedChecker] 实现 — 统一查询 [SelectionManager] */
    private fun isSelectedImpl(position: Int): Boolean {
        val uri = getItemUri(position)
        return uri != null && (selectionManager?.isSelected(uri) ?: false)
    }

    override fun onAttached(recyclerView: RecyclerView) {
        selectionManager = SelectionManager(this) {}
        // ★ 恢复 pending restoreState 的数据
        pendingRestoreIds?.let { ids ->
            val items = groupedAdapter.items
            val uris = items.filterIsInstance<GroupedAdapter.Item>()
                .filter { it.id in ids.toSet() }
                .map { it.uri }
                .toSet()
            selectionManager?.restore(uris)
            pendingRestoreIds = null
        }
    }

    override fun onDetached() {
        selectionManager?.clear()
        selectionManager = null
    }

    /** 保存选中状态到 Bundle — 使用条目 ID（非位置），避免位置变化后丢失 */
    @Synchronized
    override fun saveState(): Bundle {
        val uris = selectionManager?.selectedUris ?: emptySet()
        val ids = groupedAdapter.items.filterIsInstance<GroupedAdapter.Item>()
            .filter { it.uri in uris }
            .map { it.id }
        return Bundle().apply { putLongArray("ids", ids.toLongArray()) }
    }

    /** 从 Bundle 恢复选中状态 */
    @Synchronized
    override fun restoreState(state: Bundle?) {
        val idArray = state?.getLongArray("ids") ?: return
        if (selectionManager != null) {
            val idSet = idArray.toSet()
            val uris = groupedAdapter.items.filterIsInstance<GroupedAdapter.Item>()
                .filter { it.id in idSet }
                .map { it.uri }
                .toSet()
            selectionManager!!.restore(uris)
        } else {
            // ★ onAttached 之前调用 → 暂存，等 onAttached 时恢复
            pendingRestoreIds = idArray
        }
    }

    override fun submitList(items: List<Any>) {
        groupedAdapter.items.clear()
        groupedAdapter.items.addAll(items)
        groupedAdapter.notifyDataSetChanged()
    }

    override fun appendPage(items: List<Any>) {
        val s = groupedAdapter.itemCount
        groupedAdapter.items.addAll(items)
        groupedAdapter.notifyItemRangeInserted(s, items.size)
    }

    override val itemCount: Int get() = groupedAdapter.itemCount
    override fun spanSizeLookup() = groupedAdapter.spanSizeLookup

    /** 内部数据列表 — 供外部高级场景访问 */
    val items: MutableList<Any> get() = groupedAdapter.items
}

/**
 * 扩展函数：从 [DefaultMediaAdapter] 获取内部的 [GroupedAdapter]。
 * 方便需要直接操作分组适配器的高级场景。
 */
fun DefaultMediaAdapter.adapter(): GroupedAdapter = this.let {
    it.recyclerAdapter as GroupedAdapter
}
