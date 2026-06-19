package com.shyky.tech.photo.picker.compat.result

import android.net.Uri
import com.shyky.tech.photo.picker.compat.adapter.SelectableAdapter

/**
 * 结果类型映射器 — 将选中的 URI 列表转换为任意类型 T。
 *
 * 典型用法：
 * ```kotlin
 * // 直接返回 URI 列表
 * val mapper = ResultMapper.URI_LIST
 *
 * // 自定义转换
 * val mapper = ResultMapper.of { uris -> MyResult(uris, uris.size) }
 * ```
 *
 * 使用 fun interface，支持 SAM 转换（Lambda）。
 */
fun interface ResultMapper<T> {
    /**
     * 将选中的 URI 映射为结果类型。
     * @param selected 选中的 URI 列表
     * @param adapter 当前适配器，可为 null
     * @return 映射后的结果
     */
    fun map(selected: List<Uri>, adapter: SelectableAdapter<*>?): T

    companion object {
        /** 默认映射器：原样返回 URI 列表（无转换） */
        val URI_LIST: ResultMapper<List<Uri>> = object : ResultMapper<List<Uri>> {
            override fun map(selected: List<Uri>, adapter: SelectableAdapter<*>?) = selected
        }

        /**
         * 从 Lambda 创建映射器。
         * @param block 转换函数，接收 URI 列表返回任意类型
         */
        fun <T> of(block: (List<android.net.Uri>) -> T): ResultMapper<T> =
            ResultMapper { uris, _ -> block(uris) as Any } as ResultMapper<T>
    }
}
