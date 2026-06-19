package com.shyky.tech.photo.picker.compat.selection

import android.net.Uri
import com.shyky.tech.photo.picker.compat.adapter.SelectableAdapter

/**
 * 线程安全的选择状态管理器 — 使用 [LinkedHashSet] 保存选中 URI 以保持插入顺序。
 *
 * 所有公开方法均为 [Synchronized]，保证多线程环境下的状态一致性。
 * 通过 [selectable] 引用通知适配器状态变更。
 *
 * @param selectable 关联的选择器适配器
 * @param onMaxExceeded 超出最大选择数时的回调（如弹出 Toast）
 */
class SelectionManager(
    private val selectable: SelectableAdapter<*>,
    private val onMaxExceeded: () -> Unit = {}
) {
    /** 内部选中集合 — LinkedHashSet 保持插入顺序 */
    private val _selected = LinkedHashSet<Uri>()

    /** 已选 URI 的只读快照（线程安全） */
    val selectedUris: Set<Uri> get() = synchronized(_selected) { _selected.toSet() }

    /**
     * 切换指定 URI 的选中状态：未选中→选中，已选中→取消。
     * 选中时若已达上限则调用 [onMaxExceeded]。
     */
    @Synchronized
    fun toggle(uri: Uri, position: Int) {
        if (uri in _selected)
            _selected.remove(uri)
        else if (_selected.size < selectable.maxSelectCount)
            _selected.add(uri)
        else
            onMaxExceeded()
        selectable.onSelectionChanged?.invoke(_selected.size)
    }

    /** 选中指定 URI（不切换，仅添加） */
    @Synchronized
    fun select(uri: Uri) {
        if (uri !in _selected && _selected.size < selectable.maxSelectCount) {
            _selected.add(uri)
            selectable.onSelectionChanged?.invoke(_selected.size)
        }
    }

    /** 取消选中指定 URI */
    @Synchronized
    fun unselect(uri: Uri) {
        _selected.remove(uri)
        selectable.onSelectionChanged?.invoke(_selected.size)
    }

    /** 检查 URI 是否已选中 */
    @Synchronized
    fun isSelected(uri: Uri) = uri in _selected

    /** 当前选中数量 */
    val count get() = synchronized(_selected) { _selected.size }

    /** 清空所有选中状态 */
    @Synchronized
    fun clear() {
        _selected.clear()
        selectable.onSelectionChanged?.invoke(0)
    }

    /** 从 URI 集合恢复选中状态（用于配置变更重建） */
    @Synchronized
    fun restore(uris: Set<Uri>) {
        _selected.clear()
        _selected.addAll(uris)
        selectable.onSelectionChanged?.invoke(_selected.size)
    }
}
