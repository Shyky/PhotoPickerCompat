package com.shyky.tech.photo.picker.compat.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 媒体文件的数据类 — 简单的 Parcelable 封装。
 *
 * 用于在 Activity 之间传递已选媒体文件的基本信息。
 * 使用 [@Parcelize] 注解自动生成 Parcelable 实现，无需手写序列化代码。
 *
 * @property uri 图片或视频的本地路径/URI 字符串
 * @property isVideo 是否为视频文件
 * @property duration 如果是视频，记录毫秒时长
 */
@Parcelize
data class MediaFile(
    val uri: String,            // 图片或视频的本地路径/URI
    val isVideo: Boolean,       // 是否为视频
    val duration: Long = 0L     // 如果是视频，记录毫秒时长
) : Parcelable
