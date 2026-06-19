package com.shyky.tech.photo.picker.compat.data

import java.io.Closeable

/**
 * 数据加载结果的密封类。
 *
 * @param T 加载的数据类型
 */
sealed class LoadResult<out T> {
    /** 加载成功，包含数据 */
    data class Success<T>(val data: T) : LoadResult<T>()

    /** 加载失败，包含异常信息 */
    data class Error(val throwable: Throwable) : LoadResult<Nothing>()
}

/**
 * 选择器数据源接口 — 定义分页加载和相册查询的契约。
 *
 * 实现类需提供：
 * - [loadInitialPage]：加载首页数据
 * - [loadNextPage]：加载后续分页数据
 * - [loadAlbums]：加载相册列表
 *
 * 支持 [Closeable]，使用完毕后可释放资源（如关闭 Cursor）。
 */
interface PickerDataSource : Closeable {
    /** 加载首页数据，返回混合了分组标题的数据列表 */
    suspend fun loadInitialPage(): LoadResult<List<Any>>

    /** 加载指定偏移量的下一页数据 */
    suspend fun loadNextPage(offset: Int): LoadResult<List<Any>>

    /** 加载相册列表 */
    suspend fun loadAlbums(): LoadResult<List<AlbumEntry>>

    /** 是否还有更多数据可加载 */
    val hasMore: Boolean

    /** 当前已加载的数据偏移量 */
    val currentOffset: Int

    /** 每页数据量 */
    val pageSize: Int

    /**
     * 相册条目 — 表示设备上的一个媒体文件夹（相册/影集）。
     *
     * @param id 相册 ID（对应 MediaStore 的 BUCKET_ID）
     * @param name 相册显示名称
     * @param coverUri 相册封面媒体文件的 URI
     * @param count 相册内媒体文件数量
     */
    data class AlbumEntry(
        val id: Long,
        val name: String,
        val coverUri: android.net.Uri?,
        val count: Int
    )
}
