package com.shyky.tech.photo.picker.compat.config

import com.shyky.tech.photo.picker.compat.config.PickerStrings.Companion.CHINESE


/**
 * 字符串资源接口 — 提供选择器所有 UI 文案的国际化支持。
 *
 * 实现此接口即可自定义语言，内置 [CHINESE] 默认实现。
 * 支持 Lambda 形式的动态文案（如 [maxCountExceeded] 根据数量动态生成提示）。
 */
interface PickerStrings {
    /** 照片 Tab 标签 */
    val photoTab: String

    /** 影集 Tab 标签 */
    val albumTab: String

    /** 完成按钮文字 */
    val doneButton: String

    /** 选择按钮文字 */
    val selectButton: String

    /** 取消按钮文字 */
    val cancelButton: String

    /** 隐私提示文字（告知用户应用只能访问已选照片） */
    val privacyHint: String

    /** 权限被拒时的提示文字 */
    val permissionDenied: String

    /** 超过最大选择数时的提示（Lambda 形式，参数为最大数量） */
    val maxCountExceeded: (Int) -> String

    /** 拍照入口文字 */
    val cameraEntry: String

    /** 加载中提示文字 */
    val loadingMessage: String

    /** 无媒体时的空状态提示 */
    val emptyMessage: String

    /** 出错时的重试按钮文字 */
    val errorRetry: String

    /** 编辑预览按钮文字 */
    val editPreview: String

    /** 原图预览按钮文字 */
    val originalPreview: String

    companion object {
        /** 内置中文实现 */
        val CHINESE = object : PickerStrings {
            override val photoTab = "照片"
            override val albumTab = "影集"
            override val doneButton = "添加"
            override val selectButton = "选择"
            override val cancelButton = "取消"
            override val privacyHint = "此应用只能访问您选择的照片"
            override val permissionDenied = "需要存储权限才能加载图片"
            override val maxCountExceeded: (Int) -> String = { n -> "最多选择${n}张" }
            override val cameraEntry = "拍照"
            override val loadingMessage = "加载中…"
            override val emptyMessage = "暂无媒体"
            override val errorRetry = "重试"
            override val editPreview = "编辑"
            override val originalPreview = "原图"
        }
    }
}
