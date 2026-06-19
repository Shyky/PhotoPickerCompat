package com.shyky.tech.photo.picker.compat.pipeline

import android.net.Uri
import com.shyky.tech.photo.picker.compat.adapter.SelectableAdapter
import com.shyky.tech.photo.picker.compat.data.LoadResult
import com.shyky.tech.photo.picker.compat.data.PickerDataSource

/**
 * 中间件接口 — 在选择器的关键生命周期节点插入自定义逻辑。
 *
 * 所有方法都有默认空实现，只需重写需要的钩子。
 * 中间件按添加顺序组成责任链，每个钩子可拦截/修改数据流。
 *
 * 使用场景：
 * - [onBeforeSelect]/[onBeforeDeselect]：权限校验、文件大小检查
 * - [onBeforeReturn]：结果压缩、格式转换
 * - [onBeforeLoad]/[onAfterLoad]：加载日志、缓存
 * - [onOpened]/[onClosed]：埋点上报
 */
interface PickerMiddleware {
    /**
     * 加载数据之前调用。
     * @param page 页码
     * @param source 数据源
     * @return false 则阻止本次加载
     */
    suspend fun onBeforeLoad(page: Int, source: PickerDataSource): Boolean = true

    /**
     * 加载数据之后调用。
     * @param page 页码
     * @param result 加载结果，可替换或修改
     * @return 处理后的加载结果
     */
    suspend fun onAfterLoad(page: Int, result: LoadResult<List<Any>>): LoadResult<List<Any>> =
        result

    /**
     * 选择条目之前调用。
     * @return false 则阻止本次选择
     */
    suspend fun onBeforeSelect(uri: Uri, position: Int): Boolean = true

    /**
     * 取消选择条目之前调用。
     * @return false 则阻止本次取消
     */
    suspend fun onBeforeDeselect(uri: Uri, position: Int): Boolean = true

    /** 选中状态变化时调用 */
    suspend fun onSelectionChanged(selected: Set<Uri>) {}

    /**
     * 返回结果之前调用。
     * @param result 当前待返回的 URI 列表
     * @param adapter 当前适配器
     * @return 修改后的 URI 列表（可过滤、排序、转换）
     */
    suspend fun onBeforeReturn(result: List<Uri>, adapter: SelectableAdapter<*>): List<Uri> = result

    /** 选择器打开时调用 */
    fun onOpened(maxCount: Int) {}

    /** 选择器关闭时调用 */
    fun onClosed(resultCount: Int, cancelled: Boolean) {}

    /** 切换相册时调用 */
    fun onAlbumSwitched(albumId: Long, albumName: String) {}
}
