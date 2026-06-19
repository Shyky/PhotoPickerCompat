package com.shyky.tech.photo.picker.compat.config

import android.util.Log

/**
 * 可插拔的日志器 — 根据构建类型自动调整日志级别。
 *
 * 使用方式：
 * - Debug 构建：`PickerLogger.debug("MyPicker")` 输出 DEBUG 及以上级别
 * - Release 构建：`PickerLogger.release("MyPicker")` 仅输出 WARN 及以上级别
 * - 关闭日志：`PickerLogger.NOOP` 不输出任何日志
 *
 * @property level 日志级别阈值（高于此级别的日志不输出）
 * @property tag Logcat 中的 TAG 标识
 */
class PickerLogger private constructor(
    private val level: Int,
    private val tag: String = "PhotoPicker"
) {
    /** 输出 DEBUG 级别日志 */
    fun d(msg: String) {
        if (level <= Log.DEBUG) Log.d(tag, msg)
    }

    /** 输出 WARN 级别日志 */
    fun w(msg: String) {
        if (level <= Log.WARN) Log.w(tag, msg)
    }

    /** 输出 ERROR 级别日志 */
    fun e(msg: String, tr: Throwable? = null) {
        if (level <= Log.ERROR) Log.e(tag, msg, tr)
    }

    companion object {
        /** 空日志器 — 不输出任何日志 */
        val NOOP = PickerLogger(Log.ASSERT + 1)

        /** 创建 Debug 级别日志器（输出所有日志） */
        fun debug(tag: String = "PhotoPicker") = PickerLogger(Log.DEBUG, tag)

        /** 创建 Release 级别日志器（仅输出 WARN/ERROR） */
        fun release(tag: String = "PhotoPicker") = PickerLogger(Log.WARN, tag)
    }
}
