package com.shyky.tech.photo.picker.compat.contract

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.shyky.tech.photo.picker.compat.config.PickerConfiguration
import com.shyky.tech.photo.picker.compat.result.PickResult
import com.shyky.tech.photo.picker.compat.ui.PickerTransparentActivity

/**
 * 自定义媒体选择器的 [ActivityResultContract] — 启动内置网格 BottomSheet 选择器。
 *
 * 与 [GridPickerContract] 的区别：
 * - 本 Contract 接收完整的 [PickerConfiguration]，支持 DSL 自定义（中间件、插件、变换等）
 * - [GridPickerContract] 是简化版，只需 maxCount
 *
 * @param T 选择结果的数据类型（由配置的 [ResultMapper] 决定）
 * @param config 选择器的完整配置
 */
class MediaPickerContract<T>(
    private val config: PickerConfiguration<T>
) : ActivityResultContract<Unit, PickResult<T>>() {

    /**
     * 创建启动 [PickerTransparentActivity] 的 Intent。
     * 将最大选择数和 V2 Fragment 标志传递过去。
     */
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(context, PickerTransparentActivity::class.java).apply {
            putExtra(PickerTransparentActivity.EXTRA_MAX_COUNT, config.maxCount)
            putExtra(PickerTransparentActivity.EXTRA_USE_V2_FRAGMENT, true)
        }
    }

    /**
     * 解析 Activity 返回的结果。
     * @return [PickResult.Selected] 包含 URI 列表和映射后的结果数据
     *         或 [PickResult.Cancelled] 表示用户取消操作
     */
    override fun parseResult(resultCode: Int, intent: Intent?): PickResult<T> {
        if (resultCode != android.app.Activity.RESULT_OK || intent == null)
            return PickResult.Cancelled

        @Suppress("DEPRECATION")
        val uris =
            intent.getParcelableArrayListExtra<Uri>(PickerTransparentActivity.EXTRA_RESULT_URIS)
                ?: emptyList()

        // ★ 通过 ResultMapper 将 URI 列表映射为自定义结果类型
        return PickResult.Selected(uris, config.resultMapper.map(uris, null))
    }
}
