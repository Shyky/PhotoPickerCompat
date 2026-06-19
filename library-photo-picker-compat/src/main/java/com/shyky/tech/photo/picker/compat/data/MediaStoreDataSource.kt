package com.shyky.tech.photo.picker.compat.data

import android.content.Context
import com.shyky.tech.photo.picker.compat.util.DateSectionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 Android MediaStore 的数据源实现。
 *
 * 从系统媒体库中加载图片和视频数据，自动按日期分组（今天、昨天、星期...），
 * 支持分页加载和相册列表查询。所有 I/O 操作在 [Dispatchers.IO] 上执行，
 * 不阻塞主线程。
 *
 * @param context 用于查询 ContentResolver 的 Context，建议传 ApplicationContext 避免内存泄漏
 */
class MediaStoreDataSource(
    private val context: Context
) : PickerDataSource {

    /** MediaStore 数据加载器，封装底层的 Cursor 查询逻辑 */
    private val loader = MediaDataLoader(context)

    /** 是否还有更多数据可加载 */
    override var hasMore = true
        private set

    /** 当前已加载的数据偏移量 */
    override var currentOffset = 0
        private set

    /** 每页数据量 */
    override val pageSize = MediaDataLoader.PAGE_SIZE

    /**
     * 加载首页数据。
     * 在 IO 线程上查询 MediaStore，获取第一页数据并按日期分组。
     */
    override suspend fun loadInitialPage(): LoadResult<List<Any>> = withContext(Dispatchers.IO) {
        try {
            val media = loader.loadPage(0, null)
            currentOffset = media.size
            hasMore = media.size >= pageSize
            // ★ 在原始数据中插入日期分组标题（"今天"、"昨天" 等）
            LoadResult.Success(DateSectionHelper.insertSections(media) { it.dateModified })
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /**
     * 加载下一页数据。
     * @param offset 数据偏移量，从该位置开始加载
     */
    override suspend fun loadNextPage(offset: Int): LoadResult<List<Any>> =
        withContext(Dispatchers.IO) {
            try {
                val media = loader.loadPage(offset, null)
                currentOffset = offset + media.size
                hasMore = media.size >= pageSize
                LoadResult.Success(DateSectionHelper.insertSections(media) { it.dateModified })
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

    /**
     * 加载相册列表。
     * 在 IO 线程上扫描媒体库，按 BUCKET_ID 分组统计每个相册的媒体数量。
     */
    override suspend fun loadAlbums(): LoadResult<List<PickerDataSource.AlbumEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val albums = loader.loadAlbums()
                LoadResult.Success(albums.map {
                    PickerDataSource.AlbumEntry(
                        it.id,
                        it.name,
                        it.coverUri,
                        it.count
                    )
                })
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

    /** 关闭数据源（当前无持久化资源需要释放） */
    override fun close() {}
}
