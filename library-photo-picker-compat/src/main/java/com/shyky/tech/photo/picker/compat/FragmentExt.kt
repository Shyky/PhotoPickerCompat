@file:JvmName("FragmentPickerExtensions")

package com.shyky.tech.photo.picker.compat

import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.shyky.tech.photo.picker.compat.config.PickerScope
import com.shyky.tech.photo.picker.compat.contract.MediaPickerContract
import com.shyky.tech.photo.picker.compat.result.PickResult

/**
 * Fragment 扩展 — 一行代码注册并启动类型安全的自定义选择器。
 *
 * 自动绑定到 Fragment 的 lifecycleScope，无需手动管理协程生命周期。
 *
 * 使用示例：
 * ```kotlin
 * class MyFragment : Fragment() {
 *     // DSL 自定义注册
 *     private val pickMedia = registerForMediaPicker<Uri> {
 *         maxCount = 9
 *         photoTab { label="照片"; adapterFactory={ DefaultMediaAdapter(it) } }
 *         onResult { uris -> handle(uris) }
 *     }
 *
 *     override fun onViewCreated(...) {
 *         binding.btnSelect.setOnClickListener { pickMedia() }  // 启动
 *     }
 * }
 * ```
 *
 * @param T 选择结果的数据类型
 * @param configBlock DSL 配置块，[scope] 自动注入为 Fragment 的 lifecycleScope
 * @return 启动函数，调用即弹出选择器
 */
@JvmSynthetic
inline fun <reified T : Any> Fragment.registerForMediaPicker(
    crossinline configBlock: PickerScope<T>.() -> Unit
): () -> Unit {
    val scope = PickerScope<T>().apply {
        scope = this@registerForMediaPicker.lifecycleScope
        configBlock()
    }
    val config = scope.build()
    val contract = MediaPickerContract(config)
    val launcher = registerForActivityResult(contract) { result ->
        when (result) {
            is PickResult.Selected -> { /* 结果已由 onResult DSL 块处理 */
            }

            is PickResult.Cancelled -> {}
            is PickResult.Error -> {}
        }
    }
    return { launcher.launch(Unit) }
}

/**
 * Fragment 扩展 — 快速注册选择器，直接返回 URI 列表。
 *
 * 使用默认配置（MediaStore + 照片 Tab + 中文），适用于简单场景。
 *
 * ```kotlin
 * private val pickPhotos = registerForPhotoPicker(maxCount = 9) { uris ->
 *     adapter.submitList(uris)
 * }
 * ```
 *
 * @param maxCount 最大可选数量，默认 9
 * @param onResult 结果回调，仅在用户确认选择时触发
 * @return 启动函数，调用即弹出选择器
 */
@JvmSynthetic
fun Fragment.registerForPhotoPicker(
    maxCount: Int = 9,
    onResult: (List<Uri>) -> Unit
): () -> Unit {
    val ctx = requireContext()
    // ★ 使用快捷入口函数创建配置
    val config = pickMedia(ctx, maxCount, lifecycleScope)
    val contract = MediaPickerContract(config)
    val launcher = registerForActivityResult(contract) { result ->
        when (result) {
            is PickResult.Selected -> onResult(result.uris)
            else -> {} // 取消或错误不触发回调
        }
    }
    return { launcher.launch(Unit) }
}
