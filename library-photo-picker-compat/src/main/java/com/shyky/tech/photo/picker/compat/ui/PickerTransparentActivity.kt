package com.shyky.tech.photo.picker.compat.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.shyky.tech.photo.picker.compat.ui.PickerTransparentActivity.Companion.EXTRA_MAX_COUNT
import com.shyky.tech.photo.picker.compat.ui.PickerTransparentActivity.Companion.EXTRA_USE_V2_FRAGMENT

/**
 * 透明宿主 Activity — 作为 BottomSheet 选择器的窗口容器。
 *
 * **设计要点**：
 * - 使用透明主题，无 setContentView，用户看到的是下层 Activity
 * - [overridePendingTransition(0, 0)] 消除入场动画闪烁
 * - 通过 Handler.postDelayed(50ms) 确保 Window 完全 attach 后再显示 BottomSheet
 * - ★ onCreate 中自动检查和请求存储权限
 * - 选择完成后设置 RESULT_OK + 结果 URI 列表，finish 自身
 *
 * Intent 参数：
 * - [EXTRA_MAX_COUNT]：最大可选数量
 * - [EXTRA_USE_V2_FRAGMENT]：是否使用 V2 版本的 Fragment（MediaPickerContract 模式）
 */
class PickerTransparentActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MAX_COUNT = "extra_max_count"
        const val EXTRA_RESULT_URIS = "extra_result_uris"
        const val EXTRA_USE_V2_FRAGMENT = "extra_use_v2"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.all { it }
        if (granted) {
            showPicker()
        } else {
            Toast.makeText(this, "需要存储权限才能加载图片", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private var maxCount = 9
    private var pickerShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            maxCount = intent.getIntExtra(EXTRA_MAX_COUNT, 9)

            // ★ 先检查权限再显示选择器
            val neededPermissions = getNeededPermissions()
            if (neededPermissions.all {
                    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
                }) {
                showPicker()
            } else {
                permissionLauncher.launch(neededPermissions.toTypedArray())
            }
        }
    }

    /**
     * ★ 获取当前设备需要的存储权限列表。
     * API 33+ → READ_MEDIA_IMAGES、READ_MEDIA_VIDEO
     * API 23-32 → READ_EXTERNAL_STORAGE
     */
    private fun getNeededPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * ★ 显示 BottomSheet 选择器 — 在权限已授予后调用。
     * 使用 50ms 延迟等待 Window 完全 attach。
     */
    private fun showPicker() {
        if (pickerShown || isFinishing || isDestroyed) return
        pickerShown = true

        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed

            val bottomSheet = MediaPickerBottomSheetFragment(
                maxCount = maxCount,
                onComplete = { selectedUris: List<Uri> ->
                    if (!isFinishing && !isDestroyed) {
                        val resultIntent = Intent().apply {
                            putParcelableArrayListExtra(
                                EXTRA_RESULT_URIS,
                                ArrayList(selectedUris)
                            )
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
            )
            bottomSheet.show(supportFragmentManager, "MediaPickerBottomSheet")
        }, 50)
    }

    override fun onStop() {
        super.onStop()
        @Suppress("DEPRECATION")
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }
}
