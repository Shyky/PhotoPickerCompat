package com.shyky.tech.photo.picker.compat.result

/**
 * 选择结果的密封类 — 统一表达选择的三种可能结果。
 *
 * - [Selected]：用户确认选择，包含 URI 列表和映射后的数据
 * - [Cancelled]：用户取消选择
 * - [Error]：选择过程中发生错误
 *
 * @param T 映射后的结果数据类型
 */
sealed class PickResult<out T> {
    /**
     * 选择成功 — 用户确认了选择。
     * @param uris 选中的原始 URI 列表
     * @param mapped 通过 [ResultMapper] 映射后的结果数据
     */
    data class Selected<T>(val uris: List<android.net.Uri>, val mapped: T) : PickResult<T>()

    /** 用户取消选择 */
    object Cancelled : PickResult<Nothing>()

    /** 选择过程中发生错误 */
    data class Error(val throwable: Throwable) : PickResult<Nothing>()
}
