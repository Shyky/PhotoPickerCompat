package com.shyky.tech.photo.picker.compat.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.shyky.tech.photo.picker.compat.data.MediaDataLoader.Companion.ALBUM_SCAN_LIMIT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 媒体条目数据类 — 从 MediaStore 查询结果中提取的必要字段。
 *
 * @param id MediaStore._ID
 * @param uri content:// 格式的完整 URI
 * @param isVideo 是否为视频
 * @param durationMs 视频时长（毫秒），图片为 0
 * @param dateModified 最后修改时间（Unix 时间戳，秒）
 * @param path 文件系统路径
 * @param bucketId 媒体文件夹 ID（对应 BUCKET_ID）
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val durationMs: Long,
    val dateModified: Long,
    val path: String,
    val bucketId: Long
)

/**
 * 相册数据类 — 表示设备上的一个媒体文件夹。
 *
 * @param id 相册 ID（BUCKET_ID）
 * @param name 相册名称（BUCKET_DISPLAY_NAME）
 * @param coverUri 封面媒体文件的 URI
 * @param count 媒体文件数量
 */
data class Album(
    val id: Long,
    val name: String,
    val coverUri: Uri?,
    val count: Int
)

/**
 * MediaStore 分页数据加载器 — 从系统媒体库查询图片和视频。
 *
 * **兼容性**：API 23（Android 6.0）以上全版本支持。
 * - API 26+：使用 [QUERY_ARG_OFFSET]/[QUERY_ARG_LIMIT] Bundle 参数，O(1) 分页
 * - API 23-25：降级为经典的 `ContentResolver.query()` + `sortOrder` + 手动截取
 *
 * **分页策略**：
 * - 单类型查询（仅图片/仅视频）：直接分页
 * - 混合查询（图片+视频）：从两个源各加载窗口数据，合并排序后截取
 *
 * **相册扫描**：限制最大扫描行数为 [ALBUM_SCAN_LIMIT]（5000）。
 *
 * 所有查询操作在 [Dispatchers.IO] 上执行。
 *
 * @param context 用于查询 ContentResolver 的 Context
 */
class MediaDataLoader(private val context: Context) {

    companion object {
        /** 每页加载的条目数 */
        const val PAGE_SIZE = 40

        /**
         * ★ 相册列表的最大扫描行数 — 限制大媒体库（>5000 张照片）的 I/O 开销。
         */
        const val ALBUM_SCAN_LIMIT = 5000

        /** API 26（Android 8.0）及以上才支持 Bundle 查询参数 */
        private val supportsQueryArgs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    /** 图片媒体库的 Content URI */
    private val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    /** 视频媒体库的 Content URI */
    private val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    /** 图片查询投影列 */
    private val imageProj = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )

    /** 视频查询投影列 */
    private val videoProj = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.BUCKET_ID
    )

    /** 排序：按修改时间倒序 */
    private val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

    /**
     * 加载一页媒体数据。
     *
     * @param offset 数据偏移量
     * @param bucketId 相册过滤（null = 全部）
     * @param mimeFilter 类型过滤（ALL / IMAGES / VIDEOS）
     * @return 按 dateModified 倒序排列的媒体条目列表
     */
    suspend fun loadPage(
        offset: Int,
        bucketId: Long? = null,
        mimeFilter: MimeFilter = MimeFilter.ALL
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()

        if (mimeFilter != MimeFilter.ALL) {
            // ★ 单源查询：直接分页
            val (uri, proj) = when (mimeFilter) {
                MimeFilter.IMAGES -> imageUri to imageProj
                MimeFilter.VIDEOS -> videoUri to videoProj
                MimeFilter.ALL -> error("unreachable")
            }
            val isVideo = mimeFilter == MimeFilter.VIDEOS
            if (supportsQueryArgs) {
                // API 26+：使用 Bundle 参数实现 O(1) 分页
                loadWithBundleArgs(uri, proj, offset, PAGE_SIZE, bucketId, isVideo, list)
            } else {
                // API 23-25：降级为 sortOrder + 手动截取
                loadWithSortOrder(uri, proj, offset, PAGE_SIZE, bucketId, isVideo, list)
            }
            list.sortByDescending { it.dateModified }
            list
        } else {
            // ★ 混合模式：各源加载窗口数据，合并排序后分页截取
            val window = offset + PAGE_SIZE
            if (supportsQueryArgs) {
                loadWithBundleArgs(imageUri, imageProj, 0, window, bucketId, false, list)
                loadWithBundleArgs(videoUri, videoProj, 0, window, bucketId, true, list)
            } else {
                loadWithSortOrder(imageUri, imageProj, 0, window, bucketId, false, list)
                loadWithSortOrder(videoUri, videoProj, 0, window, bucketId, true, list)
            }
            list.sortByDescending { it.dateModified }
            list.drop(offset).take(PAGE_SIZE)
        }
    }

    // ═══════════════════ API 26+ 分页（Bundle 参数，O(1)） ═══════════════════

    /**
     * API 26+：使用 [QUERY_ARG_OFFSET]/[QUERY_ARG_LIMIT] Bundle 参数实现 O(1) 分页。
     */
    private fun loadWithBundleArgs(
        uri: Uri,
        proj: Array<String>,
        offset: Int,
        limit: Int,
        bucketId: Long?,
        isVideo: Boolean,
        dest: MutableList<MediaItem>
    ) {
        val sel = bucketFilter(bucketId)
        val selArgs = bucketFilterArgs(bucketId)

        val queryArgs = android.os.Bundle().apply {
            putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            if (sel != null) {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, sel)
                putStringArray(
                    android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    selArgs
                )
            }
        }
        val cursor = context.contentResolver.query(uri, proj, queryArgs, null)
        cursor?.use { parseCursor(it, uri, isVideo, dest) }
    }

    // ═══════════════════ API 23-25 降级分页（sortOrder + 手动截取） ═══════════════════

    /**
     * API 23-25 降级路径：使用经典的 `query(uri, proj, sel, selArgs, sortOrder)` 方法。
     *
     * 由于旧 API 不支持 OFFSET/LIMIT 参数，需要加载 offset+limit 条数据，
     * 排序后手动 drop(offset).take(limit)。
     */
    private fun loadWithSortOrder(
        uri: Uri,
        proj: Array<String>,
        offset: Int,
        limit: Int,
        bucketId: Long?,
        isVideo: Boolean,
        dest: MutableList<MediaItem>
    ) {
        val sel = bucketFilter(bucketId)
        val selArgs = bucketFilterArgs(bucketId)

        @Suppress("DEPRECATION")
        val cursor: Cursor? = context.contentResolver.query(
            uri, proj, sel, selArgs, sortOrder
        )
        cursor?.use { c ->
            val temp = mutableListOf<MediaItem>()
            parseCursor(c, uri, isVideo, temp)
            // ★ 手动分页：先排序（数据已经按 sortOrder 排），再截取
            temp.sortByDescending { it.dateModified }
            dest.addAll(temp.drop(offset).take(limit))
        }
    }

    // ═══════════════════ 相册列表加载 ═══════════════════

    /**
     * 加载相册列表 — 扫描媒体库按 BUCKET_ID 分组统计。
     *
     * **兼容性**：
     * - API 26+：使用 Bundle 参数限制扫描量
     * - API 23-25：使用经典 query + sortOrder，手动截断前 [ALBUM_SCAN_LIMIT] 条
     *
     * 返回结果前自动插入「所有照片」虚拟相册（id = -1）。
     */
    suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albumMap = LinkedHashMap<Long, AlbumBuilder>()

        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val albumSortOrder =
            "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC, ${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        val cursor: Cursor? = if (supportsQueryArgs) {
            // ★ API 26+：Bundle 参数限制扫描量
            val queryArgs = android.os.Bundle().apply {
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, ALBUM_SCAN_LIMIT)
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, albumSortOrder)
            }
            context.contentResolver.query(imageUri, proj, queryArgs, null)
        } else {
            // ★ API 23-25：经典 query + 手动截断
            @Suppress("DEPRECATION")
            context.contentResolver.query(imageUri, proj, null, null, albumSortOrder)
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol =
                c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            var scanned = 0
            while (c.moveToNext() && scanned < ALBUM_SCAN_LIMIT) {
                val bid = c.getLong(bucketIdCol)
                val entry = albumMap.getOrPut(bid) {
                    AlbumBuilder(
                        id = bid,
                        name = c.getString(bucketNameCol) ?: "Unknown",
                        coverId = c.getLong(idCol)
                    )
                }
                entry.count++
                scanned++
            }
        }

        val albums = albumMap.values.map { it.toAlbum(imageUri) }
        val totalCount = albums.sumOf { it.count }

        // ★ 在列表头部插入「所有照片」虚拟相册
        listOf(
            Album(
                id = -1,
                name = "所有照片",
                coverUri = albums.firstOrNull()?.coverUri,
                count = totalCount
            )
        ) + albums
    }

    // ═══════════════════ 工具方法 ═══════════════════

    /** 相册过滤条件（null = 不过滤） */
    private fun bucketFilter(bucketId: Long?): String? {
        if (bucketId != null && bucketId > 0) {
            return "${MediaStore.Images.Media.BUCKET_ID} = ?"
        }
        return null
    }

    private fun bucketFilterArgs(bucketId: Long?): Array<String>? {
        if (bucketId != null && bucketId > 0) {
            return arrayOf(bucketId.toString())
        }
        return null
    }

    /** 解析 Cursor 并添加到目标列表 */
    private fun parseCursor(
        c: Cursor,
        uri: Uri,
        isVideo: Boolean,
        dest: MutableList<MediaItem>
    ) {
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dataCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
        val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
        val durCol =
            if (isVideo) c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1

        while (c.moveToNext()) {
            dest.add(
                MediaItem(
                    id = c.getLong(idCol),
                    uri = ContentUris.withAppendedId(uri, c.getLong(idCol)),
                    isVideo = isVideo,
                    durationMs = if (durCol >= 0) c.getLong(durCol) else 0,
                    dateModified = c.getLong(dateCol),
                    path = c.getString(dataCol) ?: "",
                    bucketId = c.getLong(bucketCol)
                )
            )
        }
    }

    /** 相册构建器 — 在扫描过程中累积相册信息 */
    private class AlbumBuilder(
        val id: Long,
        val name: String,
        val coverId: Long
    ) {
        var count = 0

        fun toAlbum(baseUri: Uri) = Album(
            id = id,
            name = name,
            coverUri = ContentUris.withAppendedId(baseUri, coverId),
            count = count
        )
    }

    /** MIME 类型过滤枚举 */
    enum class MimeFilter { ALL, IMAGES, VIDEOS }
}
