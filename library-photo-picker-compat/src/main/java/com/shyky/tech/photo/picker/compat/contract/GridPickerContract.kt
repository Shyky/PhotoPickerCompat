package com.shyky.tech.photo.picker.compat.contract

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.shyky.tech.photo.picker.compat.ui.PickerTransparentActivity

/**
 * 简化版网格选择器的 [ActivityResultContract] — 快速启动内置 BottomSheet 多选器。
 *
 * 相比 [MediaPickerContract]，本 Contract 只需指定 [maxCount]，
 * 内部使用默认配置，适用于不需要深度自定义的场景。
 *
 * @param maxCount 最大可选数量，默认 9
 */
class GridPickerContract(
    private val maxCount: Int = 9
) : ActivityResultContract<Unit, List<Uri>>() {

    /**
     * 创建启动 [PickerTransparentActivity] 的 Intent。
     * 仅传递最大选择数，Activity 内部使用默认配置。
     */
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(context, PickerTransparentActivity::class.java).apply {
            putExtra(PickerTransparentActivity.EXTRA_MAX_COUNT, maxCount)
        }
    }

    /**
     * 解析 Activity 返回的结果。
     * @return 用户选中的 URI 列表，取消时返回空列表
     */
    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != android.app.Activity.RESULT_OK || intent == null)
            return emptyList()

        @Suppress("DEPRECATION")
        return intent.getParcelableArrayListExtra(PickerTransparentActivity.EXTRA_RESULT_URIS)
            ?: emptyList()
    }
}
