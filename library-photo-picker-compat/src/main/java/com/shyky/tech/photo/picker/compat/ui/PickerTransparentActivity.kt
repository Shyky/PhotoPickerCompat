package com.shyky.tech.photo.picker.compat.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.shyky.tech.photo.picker.compat.ui.PickerTransparentActivity.Companion.EXTRA_MAX_COUNT
import com.shyky.tech.photo.picker.compat.ui.PickerTransparentActivity.Companion.EXTRA_USE_V2_FRAGMENT

/**
 * 透明宿主 Activity — 作为 BottomSheet 选择器的窗口容器。
 *
 * **设计要点**：
 * - 使用透明主题，无 setContentView，用户看到的是下层 Activity
 * - [overridePendingTransition(0, 0)] 消除入场动画闪烁
 * - 通过 Handler.postDelayed(50ms) 确保 Window 完全 attach 后再显示 BottomSheet
 * - 选择完成后设置 RESULT_OK + 结果 URI 列表，finish 自身
 *
 * Intent 参数：
 * - [EXTRA_MAX_COUNT]：最大可选数量
 * - [EXTRA_USE_V2_FRAGMENT]：是否使用 V2 版本的 Fragment（MediaPickerContract 模式）
 */
class PickerTransparentActivity : AppCompatActivity() {

    companion object {
        /** Intent Extra: 最大可选数量 */
        const val EXTRA_MAX_COUNT = "extra_max_count"

        /** Intent Extra: 返回的 URI 列表 */
        const val EXTRA_RESULT_URIS = "extra_result_uris"

        /** Intent Extra: 是否使用 V2 版本 Fragment */
        const val EXTRA_USE_V2_FRAGMENT = "extra_use_v2"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ★ 在 Window 创建前覆盖过渡动画 — 防止闪烁
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)
        // ★ 透明主题已处理背景 — 不需要 setContentView

        if (savedInstanceState == null) {
            val maxCount = intent.getIntExtra(EXTRA_MAX_COUNT, 9)

            // ★ 使用 Handler.postDelayed 而非 decorView.post — 保证 Window 完全 attach
            Handler(Looper.getMainLooper()).postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed

                val bottomSheet = MediaPickerBottomSheetFragment(
                    maxCount = maxCount,
                    onComplete = { selectedUris ->
                        if (!isFinishing && !isDestroyed) {
                            val resultIntent = Intent().apply {
                                putParcelableArrayListExtra(
                                    EXTRA_RESULT_URIS,
                                    ArrayList(selectedUris)
                                )
                            }
                            setResult(Activity.RESULT_OK, resultIntent)
                            finish()
                            // ★ 关闭动画同样无过渡
                            @Suppress("DEPRECATION")
                            overridePendingTransition(0, 0)
                        }
                    }
                )
                bottomSheet.show(supportFragmentManager, "MediaPickerBottomSheet")
            }, 50) // ★ 50ms 延迟让 Window 完全 attach
        }
    }

    override fun onStop() {
        super.onStop()
        // ★ 确保停止时背景保持透明
        @Suppress("DEPRECATION")
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }
}
