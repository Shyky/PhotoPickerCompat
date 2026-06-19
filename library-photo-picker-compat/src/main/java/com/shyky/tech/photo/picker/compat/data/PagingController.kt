package com.shyky.tech.photo.picker.compat.data

import com.shyky.tech.photo.picker.compat.adapter.SelectableAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 分页加载控制器 — 管理数据加载的生命周期和并发控制。
 *
 * 核心特性：
 * - **防抖控制**：两次加载至少间隔 200ms，避免快速滑动时重复请求
 * - **并发安全**：新请求自动取消前一个未完成的 Job
 * - **生命周期绑定**：支持绑定到父协程作用域，父作用域取消时自动停止
 * - **错误处理**：通过 [onLoadError] 回调统一处理加载失败
 *
 * @param dataSource 数据源，提供分页数据
 * @param adapter 选择器适配器，接收加载结果并更新 UI
 * @param parentScope 父协程作用域（推荐传入 lifecycleScope），为 null 时使用独立作用域
 */
class PagingController(
    private val dataSource: PickerDataSource,
    private val adapter: SelectableAdapter<*>,
    parentScope: CoroutineScope? = null
) {
    /** 是否正在加载中（防并发） */
    private var loading = false

    /** 上次加载完成的时间戳，用于防抖 */
    private var lastLoadTime = 0L

    /** 当前正在执行的加载协程 */
    private var currentJob: Job? = null

    /**
     * 协程作用域：绑定到父作用域以便取消传播；
     * 若无父作用域则创建独立的 Main + SupervisorJob 作用域。
     * SupervisorJob 确保子协程失败不影响其他协程。
     */
    private val scope = if (parentScope != null) {
        CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    } else {
        CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    /** 加载出错时的回调 */
    var onLoadError: ((Throwable) -> Unit)? = null

    /**
     * 加载首页数据。
     * 取消前一个未完成的加载任务，启动新的加载协程。
     */
    fun loadInitial(): Job {
        currentJob?.cancel()
        val job = scope.launch {
            if (loading) return@launch
            loading = true
            lastLoadTime = System.currentTimeMillis()
            try {
                when (val result = dataSource.loadInitialPage()) {
                    is LoadResult.Success -> adapter.submitList(result.data)
                    is LoadResult.Error -> onLoadError?.invoke(result.throwable)
                }
            } catch (e: Exception) {
                onLoadError?.invoke(e)
            } finally {
                loading = false
            }
        }
        currentJob = job
        return job
    }

    /**
     * 加载下一页数据。
     * 内置防抖逻辑：200ms 内不重复加载，快速滚动时自动跳过。
     */
    fun loadNext() {
        val now = System.currentTimeMillis()
        // ★ 防抖：正在加载 / 无更多数据 / 距上次加载不足 200ms 时跳过
        if (loading || !dataSource.hasMore || (now - lastLoadTime) < 200L) return
        currentJob?.cancel()
        val job = scope.launch {
            loading = true
            lastLoadTime = System.currentTimeMillis()
            try {
                when (val result = dataSource.loadNextPage(dataSource.currentOffset)) {
                    is LoadResult.Success -> adapter.appendPage(result.data)
                    is LoadResult.Error -> onLoadError?.invoke(result.throwable)
                }
            } catch (e: Exception) {
                onLoadError?.invoke(e)
            } finally {
                loading = false
            }
        }
        currentJob = job
    }

    /** 取消当前正在执行的加载任务 */
    fun cancelAll() {
        currentJob?.cancel()
    }

    /**
     * 释放资源：取消所有加载任务、取消协程作用域、关闭数据源。
     * 在 Fragment/Activity 销毁时调用，防止协程泄漏。
     */
    fun dispose() {
        cancelAll()
        scope.cancel()
        kotlin.runCatching { dataSource.close() }
    }
}
