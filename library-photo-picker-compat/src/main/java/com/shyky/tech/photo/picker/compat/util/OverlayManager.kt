package com.shyky.tech.photo.picker.compat.util

import android.view.View
import android.view.ViewGroup

/**
 * 覆盖层管理器 — 控制 Loading / Empty / Error 状态的显示与隐藏。
 *
 * 管理容器中 3 种覆盖层 View 的可见性，
 * 调用 [show] 时自动切换。
 *
 * @param container 覆盖层的容器 ViewGroup
 * @param loadingView 加载中覆盖层（可选）
 * @param emptyView 空状态覆盖层（可选）
 * @param errorView 错误状态覆盖层（可选）
 */
class OverlayManager(
    private val container: ViewGroup,
    private val loadingView: View? = null,
    private val emptyView: View? = null,
    private val errorView: View? = null
) {
    /** 覆盖层状态枚举 */
    enum class State {
        /** 加载中 */
        LOADING,

        /** 内容为空 */
        EMPTY,

        /** 加载出错 */
        ERROR,

        /** 正常内容 */
        CONTENT
    }

    /**
     * 显示指定状态。
     * 对应的覆盖层设为 VISIBLE，其他设为 GONE。
     * [State.CONTENT] 时隐藏所有覆盖层。
     */
    fun show(state: State) {
        loadingView?.visibility = if (state == State.LOADING) View.VISIBLE else View.GONE
        emptyView?.visibility = if (state == State.EMPTY) View.VISIBLE else View.GONE
        errorView?.visibility = if (state == State.ERROR) View.VISIBLE else View.GONE
    }
}
