package com.shyky.tech.photo.picker.compat.config

import com.shyky.tech.photo.picker.compat.config.PickerAnalytics.Companion.NOOP


/**
 * 埋点接口 — 采集选择器的关键用户行为事件。
 *
 * 实现此接口可对接 Firebase、自定义统计等埋点系统。
 * 内置 [NOOP] 空实现，不使用时零开销。
 */
interface PickerAnalytics {
    /** 选择器打开 */
    fun onPickerOpened(maxCount: Int)

    /** 选择器关闭（含取消） */
    fun onPickerClosed(resultCount: Int, cancelled: Boolean)

    /** 选中数量变化 */
    fun onSelectionChanged(count: Int)

    /** 切换相册 */
    fun onAlbumSwitched(albumName: String)

    /** 点击拍照入口 */
    fun onCameraUsed()

    /** 打开预览 */
    fun onPreviewOpened()

    /** 发生错误 */
    fun onError(throwable: Throwable, context: String)

    companion object {
        /** 空实现 — 所有回调为空操作 */
        val NOOP = object : PickerAnalytics {
            override fun onPickerOpened(maxCount: Int) {}
            override fun onPickerClosed(resultCount: Int, cancelled: Boolean) {}
            override fun onSelectionChanged(count: Int) {}
            override fun onAlbumSwitched(albumName: String) {}
            override fun onCameraUsed() {}
            override fun onPreviewOpened() {}
            override fun onError(throwable: Throwable, context: String) {}
        }
    }
}
