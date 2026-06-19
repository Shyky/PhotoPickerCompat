package com.shyky.tech.photo.picker.compat.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 基于 Kotlin Flow 的数据源 — 适用于单次发射的数据场景。
 *
 * 适合网络响应、预计算列表等一次性数据源。**不支持多页分页**：
 * 首次 [loadInitialPage] 后 [hasMore] 即置为 false。
 * 对于大量本地媒体库，请使用 [MediaStoreDataSource]。
 *
 * @param dataFlow 发射加载结果的 Flow，通常只发射一次
 * @param pageSize 每页数据量，默认 40
 */
class FlowPickerDataSource(
    private val dataFlow: Flow<LoadResult<List<Any>>>,
    override val pageSize: Int = 40
) : PickerDataSource {

    /** 是否还有更多数据 — 单次发射后即置为 false */
    override var hasMore = true
        private set

    /** 当前偏移量 */
    override var currentOffset = 0
        private set

    /** 是否已经加载过数据 */
    private var loaded = false

    /** 加载首页：从 Flow 中取出第一条数据 */
    override suspend fun loadInitialPage(): LoadResult<List<Any>> {
        loaded = true
        currentOffset = 0
        val result = dataFlow.first()
        hasMore = false // ★ 单次发射的 Flow — 无更多分页
        return result
    }

    /** 尝试加载下一页 — 若已加载则返回空列表 */
    override suspend fun loadNextPage(offset: Int): LoadResult<List<Any>> {
        if (loaded) {
            hasMore = false
            return LoadResult.Success(emptyList())
        }
        currentOffset = offset
        val result = dataFlow.first()
        hasMore = false
        loaded = true
        return result
    }

    /** Flow 数据源不支持相册列表，返回空 */
    override suspend fun loadAlbums(): LoadResult<List<PickerDataSource.AlbumEntry>> =
        LoadResult.Success(emptyList())

    override fun close() {}
}
