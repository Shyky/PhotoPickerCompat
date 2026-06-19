package com.shyky.tech.photo.picker.compat

import com.shyky.tech.photo.picker.compat.contract.GridPickerContract
import com.shyky.tech.photo.picker.compat.contract.PickDefaultTab
import com.shyky.tech.photo.picker.compat.contract.PickImageAndVideoContract
import com.shyky.tech.photo.picker.compat.contract.PickImageContract
import com.shyky.tech.photo.picker.compat.contract.PickMultipleImagesContract
import com.shyky.tech.photo.picker.compat.contract.PickMultipleMediaContract
import com.shyky.tech.photo.picker.compat.contract.PickMultipleVideosContract
import com.shyky.tech.photo.picker.compat.contract.PickVideoContract

/**
 * Photo Picker 兼容库入口。
 *
 * 提供统一 API 在不同 Android 版本上选择图片和视频：
 * - Android 13+ (API 33)：使用系统 PhotoPicker
 * - Android 8-12 (API 26-32)：降级为 GetContent / GetMultipleContents
 *
 * 使用示例：
 * ```kotlin
 * // 单张图片
 * private val pickImage = registerForActivityResult(PhotoPicker.image()) { uri -> ... }
 * pickImage.launch(Unit)
 *
 * // 多张图片（最多 9 张）
 * private val pickImages = registerForActivityResult(PhotoPicker.multipleImages(9)) { uris -> ... }
 * pickImages.launch(Unit)
 *
 * // 单个视频
 * private val pickVideo = registerForActivityResult(PhotoPicker.video()) { uri -> ... }
 * pickVideo.launch(Unit)
 *
 * // 多个视频
 * private val pickVideos = registerForActivityResult(PhotoPicker.multipleVideos(5)) { uris -> ... }
 * pickVideos.launch(Unit)
 *
 * // 图片+视频混合（单张）
 * private val pickMedia = registerForActivityResult(PhotoPicker.imageAndVideo()) { uri -> ... }
 * pickMedia.launch(Unit)
 *
 * // 图片+视频混合（多张），默认打开相册视图（仅 API 35+ 生效）
 * private val pickMix = registerForActivityResult(
 *     PhotoPicker.multipleMedia(9, defaultTab = PickDefaultTab.ALBUMS)
 * ) { uris -> ... }
 * pickMix.launch(Unit)
 * ```
 */
object PhotoPickerCompat {

    /** 选择单张图片 */
    fun image(): PickImageContract = PickImageContract()

    /**
     * 选择多张图片。
     * @param maxCount 最大可选数量，null 表示不限制（仅在 API 33+ 生效，低版本需调用方自行截断）。
     */
    fun multipleImages(maxCount: Int? = null): PickMultipleImagesContract =
        PickMultipleImagesContract(maxCount)

    /** 选择单个视频 */
    fun video(): PickVideoContract = PickVideoContract()

    /**
     * 选择多个视频。
     * @param maxCount 最大可选数量，null 表示不限制（仅在 API 33+ 生效，低版本需调用方自行截断）。
     */
    fun multipleVideos(maxCount: Int? = null): PickMultipleVideosContract =
        PickMultipleVideosContract(maxCount)

    /**
     * 选择单张图片或视频（混合模式）。
     * @param defaultTab 打开选择器时的默认标签页（[PickDefaultTab.PHOTOS] 或 [PickDefaultTab.ALBUMS]），
     *   仅在 API 35+ 生效，低版本自动使用系统默认行为。
     */
    fun imageAndVideo(defaultTab: PickDefaultTab = PickDefaultTab.UNSPECIFIED): PickImageAndVideoContract =
        PickImageAndVideoContract(defaultTab)

    /**
     * 选择多张图片和视频（混合模式）。
     * @param maxCount 最大可选数量，null 表示不限制（仅在 API 33+ 生效，低版本需调用方自行截断）。
     * @param defaultTab 打开选择器时的默认标签页（[PickDefaultTab.PHOTOS] 或 [PickDefaultTab.ALBUMS]），
     *   仅在 API 35+ 生效，低版本自动使用系统默认行为。
     */
    fun multipleMedia(
        maxCount: Int? = null,
        defaultTab: PickDefaultTab = PickDefaultTab.UNSPECIFIED
    ): PickMultipleMediaContract =
        PickMultipleMediaContract(maxCount, defaultTab)

    /**
     * ★ 自定义网格选择器（支持长按滑动多选）。
     *
     * - Android 13+ (API 33)：需要 [Manifest.permission.READ_MEDIA_IMAGES] 权限
     * - Android 8-12 (API 26-32)：需要 [Manifest.permission.READ_EXTERNAL_STORAGE] 权限
     *
     * Activity 内部自动处理权限请求，被拒时取消选择。
     *
     * @param maxCount 最大可选数量，默认 9。
     *
     * 使用示例：
     * ```kotlin
     * private val pickGrid = registerForActivityResult(
     *     PhotoPicker.gridPicker(maxCount = 9)
     * ) { uris: List<Uri> -> handleResult(uris) }
     * pickGrid.launch(Unit)
     * ```
     */
    fun gridPicker(maxCount: Int = 9): GridPickerContract =
        GridPickerContract(maxCount)
}
