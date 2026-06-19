package com.shyky.tech.photo.picker.compat.contract

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 指定 PhotoPicker 打开时默认展示的标签页。
 *
 * - [UNSPECIFIED]：不指定，由系统决定
 * - [PHOTOS]：默认打开全部媒体视图（图片+视频混合）
 * - [ALBUMS]：默认打开相册视图
 *
 * 仅在 API 35+ 生效，低版本自动使用系统默认行为。
 */
enum class PickDefaultTab {
    /** 不指定默认标签页 */
    UNSPECIFIED,

    /** 默认打开全部媒体视图 */
    PHOTOS,

    /** 默认打开相册视图 */
    ALBUMS
}

/**
 * 单张图片选择 Contract。
 *
 * - Android 13+ (API 33)：使用系统 PhotoPicker
 * - Android 8-12 (API 26-32)：降级为 [ActivityResultContracts.GetContent]
 *
 * 输出：[Uri]?，用户取消时返回 null。
 */
class PickImageContract : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityResultContracts.PickVisualMedia().createIntent(
                context,
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            @Suppress("DEPRECATION")
            ActivityResultContracts.GetContent().createIntent(context, "image/*")
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityResultContracts.PickVisualMedia().parseResult(resultCode, intent)
        } else {
            @Suppress("DEPRECATION")
            ActivityResultContracts.GetContent().parseResult(resultCode, intent)
        }
    }
}

/**
 * 多张图片选择 Contract。
 *
 * - Android 13+ (API 33)：使用系统 PhotoPicker，通过 [maxCount] 限制数量。
 * - Android 8-12 (API 26-32)：降级为 [ActivityResultContracts.GetMultipleContents]，
 *   [maxCount] 不生效，调用方需自行截断。
 *
 * @param maxCount 最大可选图片数量，null 表示不限制（仅在 API 33+ 生效）。
 *
 * 输出：[List]<[Uri]>，用户取消时返回空列表。
 */
class PickMultipleImagesContract(
    private val maxCount: Int? = null
) : ActivityResultContract<Unit, List<Uri>>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val contract = if (maxCount != null) {
                ActivityResultContracts.PickMultipleVisualMedia(maxCount)
            } else {
                ActivityResultContracts.PickMultipleVisualMedia()
            }
            contract.createIntent(
                context,
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            @Suppress("DEPRECATION")
            ActivityResultContracts.GetMultipleContents().createIntent(context, "image/*")
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val contract = if (maxCount != null) {
                ActivityResultContracts.PickMultipleVisualMedia(maxCount)
            } else {
                ActivityResultContracts.PickMultipleVisualMedia()
            }
            contract.parseResult(resultCode, intent)
        } else {
            @Suppress("DEPRECATION")
            ActivityResultContracts.GetMultipleContents().parseResult(resultCode, intent)
        }
    }
}

/**
 * 单张图片或视频选择 Contract（混合模式）。
 *
 * - Android 15+ (API 35)：使用 [PickVisualMediaRequest] 指定默认标签页
 * - Android 13-14 (API 33-34)：使用 [PickVisualMediaRequest] + [ImageAndVideo]
 * - Android 8-12 (API 26-32)：降级为 [Intent.ACTION_GET_CONTENT] + [Intent.EXTRA_MIME_TYPES]
 *
 * @param defaultTab 打开选择器时的默认标签页，仅在 API 35+ 生效。
 *
 * 输出：[Uri]?，用户取消时返回 null。
 */
class PickImageAndVideoContract(
    private val defaultTab: PickDefaultTab = PickDefaultTab.UNSPECIFIED
) : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val request = if (Build.VERSION.SDK_INT >= 35) {
                PickVisualMediaRequest(
                    mediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                    defaultTab = when (defaultTab) {
                        PickDefaultTab.ALBUMS ->
                            ActivityResultContracts.PickVisualMedia.DefaultTab.AlbumsTab

                        else ->
                            ActivityResultContracts.PickVisualMedia.DefaultTab.PhotosTab
                    }
                )
            } else {
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            }
            ActivityResultContracts.PickVisualMedia().createIntent(context, request)
        } else {
            @Suppress("DEPRECATION")
            Intent(Intent.ACTION_GET_CONTENT)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                .addCategory(Intent.CATEGORY_OPENABLE)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityResultContracts.PickVisualMedia().parseResult(resultCode, intent)
        } else {
            @Suppress("DEPRECATION")
            ActivityResultContracts.GetContent().parseResult(resultCode, intent)
        }
    }
}

/**
 * 多张图片和视频混合选择 Contract。
 *
 * - Android 15+ (API 35)：使用 [PickVisualMediaRequest] 指定默认标签页
 * - Android 13-14 (API 33-34)：使用 [PickVisualMediaRequest] + [ImageAndVideo]
 * - Android 8-12 (API 26-32)：降级为 [Intent.ACTION_GET_CONTENT] + [EXTRA_ALLOW_MULTIPLE]
 *
 * @param maxCount 最大可选数量，null 表示不限制（仅在 API 33+ 生效，低版本需调用方自行截断）。
 * @param defaultTab 打开选择器时的默认标签页，仅在 API 35+ 生效。
 *
 * 输出：[List]<[Uri]>，用户取消时返回空列表。
 */
class PickMultipleMediaContract(
    private val maxCount: Int? = null,
    private val defaultTab: PickDefaultTab = PickDefaultTab.UNSPECIFIED
) : ActivityResultContract<Unit, List<Uri>>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val request = if (Build.VERSION.SDK_INT >= 35) {
                PickVisualMediaRequest(
                    mediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                    defaultTab = when (defaultTab) {
                        PickDefaultTab.ALBUMS ->
                            ActivityResultContracts.PickVisualMedia.DefaultTab.AlbumsTab

                        else ->
                            ActivityResultContracts.PickVisualMedia.DefaultTab.PhotosTab
                    }
                )
            } else {
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            }
            val contract = if (maxCount != null) {
                ActivityResultContracts.PickMultipleVisualMedia(maxCount)
            } else {
                ActivityResultContracts.PickMultipleVisualMedia()
            }
            contract.createIntent(context, request)
        } else {
            @Suppress("DEPRECATION")
            Intent(Intent.ACTION_GET_CONTENT)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                .addCategory(Intent.CATEGORY_OPENABLE)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val contract = if (maxCount != null) {
                ActivityResultContracts.PickMultipleVisualMedia(maxCount)
            } else {
                ActivityResultContracts.PickMultipleVisualMedia()
            }
            contract.parseResult(resultCode, intent)
        } else {
            @Suppress("DEPRECATION")
            ActivityResultContracts.GetMultipleContents().parseResult(resultCode, intent)
        }
    }
}

/**
 * 多个视频选择 Contract。
 *
 * - Android 13+ (API 33)：使用系统 PhotoPicker，通过 [maxCount] 限制数量。
 * - Android 8-12 (API 26-32)：降级为 [ActivityResultContracts.GetMultipleContents]，
 *   [maxCount] 不生效，调用方需自行截断。
 *
 * @param maxCount 最大可选视频数量，null 表示不限制（仅在 API 33+ 生效）。
 *
 * 输出：[List]<[Uri]>，用户取消时返回空列表。
 */
class PickMultipleVideosContract(
    private val maxCount: Int? = null
) : ActivityResultContract<Unit, List<Uri>>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val contract = if (maxCount != null) {
                ActivityResultContracts.PickMultipleVisualMedia(maxCount)
            } else {
                ActivityResultContracts.PickMultipleVisualMedia()
            }
            contract.createIntent(
                context,
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        } else {
            @Suppress("DEPRECATION")
            ActivityResultContracts.GetMultipleContents().createIntent(context, "video/*")
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val contract = if (maxCount != null) {
                ActivityResultContracts.PickMultipleVisualMedia(maxCount)
            } else {
                ActivityResultContracts.PickMultipleVisualMedia()
            }
            contract.parseResult(resultCode, intent)
        } else {
            @Suppress("DEPRECATION")
            ActivityResultContracts.GetMultipleContents().parseResult(resultCode, intent)
        }
    }
}

/**
 * 单个视频选择 Contract。
 *
 * - Android 13+ (API 33)：使用系统 PhotoPicker
 * - Android 8-12 (API 26-32)：降级为 [ActivityResultContracts.GetContent]
 *
 * 输出：[Uri]?，用户取消时返回 null。
 */
class PickVideoContract : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityResultContracts.PickVisualMedia().createIntent(
                context,
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        } else {
            @Suppress("DEPRECATION")
            ActivityResultContracts.GetContent().createIntent(context, "video/*")
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityResultContracts.PickVisualMedia().parseResult(resultCode, intent)
        } else {
            @Suppress("DEPRECATION")
            ActivityResultContracts.GetContent().parseResult(resultCode, intent)
        }
    }
}
