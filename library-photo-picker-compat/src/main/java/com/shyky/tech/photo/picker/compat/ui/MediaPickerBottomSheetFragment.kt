package com.shyky.tech.photo.picker.compat.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator
import com.shyky.tech.photo.picker.compat.adapter.GroupedAdapter
import com.shyky.tech.photo.picker.compat.config.PickerStrings
import com.shyky.tech.photo.picker.compat.data.MediaDataLoader
import com.shyky.tech.photo.picker.compat.data.MediaItem
import com.shyky.tech.photo.picker.compat.databinding.LayoutBottomSheetUiBinding
import com.shyky.tech.photo.picker.compat.selection.MultiSelectHelper
import com.shyky.tech.photo.picker.compat.util.DateSectionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BottomSheet 媒体选择器 Fragment — 选择器的核心 UI 组件。
 *
 * **布局结构**：
 * - CoordinatorLayout 内嵌 CardView（圆角 24dp）
 * - 顶部：拖动条 + 隐私提示 + 关闭按钮
 * - 中间：胶囊 TabLayout（照片 | 影集）
 * - 底部：ViewPager2（照片页面 3 列网格 / 影集页面 2 列列表）
 *
 * **交互行为**：
 * - 半屏展开（60%）→ 上滑全屏 → 下滑回半屏 → 列表顶部下拉关闭
 * - 长按进入多选模式，滑动连续多选
 * - 自动分页加载（滚动到底部前 5 个触发下一页）
 *
 * **性能优化亮点**：
 * - 热路径缓存（cachedSheetBehavior / cachedGridLayoutManager）
 * - Payload 局部刷新（多选时不重载 Coil 图片）
 * - 预取 + 深回收池 + 无条目动画
 *
 * @param maxCount 最大可选数量
 * @param onComplete 选择完成回调，返回选中的 URI 列表
 */
class MediaPickerBottomSheetFragment(
    private val maxCount: Int,
    private val onComplete: (List<Uri>) -> Unit
) : BottomSheetDialogFragment() {

    // ═══════════ ViewBinding ═══════════

    private var _binding: LayoutBottomSheetUiBinding? = null
    private val binding get() = _binding!!

    // ═══════════ 数据与适配器 ═══════════

    /** 照片 Tab 的数据列表（包含分组标题和媒体条目） */
    private val photosData = mutableListOf<Any>()

    /** 核心分组适配器 */
    private var photosAdapter: GroupedAdapter? = null

    /** 多选触摸处理器 */
    private var photosHelper: MultiSelectHelper? = null

    /** MediaStore 数据加载器 */
    private var dataLoader: MediaDataLoader? = null

    /** 当前数据偏移量 */
    private var mediaOffset = 0

    /** 是否还有更多数据 */
    private var mediaHasMore = true

    /** 是否正在加载中（防并发） */
    private var mediaLoading = false

    // ═══════════ 热路径缓存 — 避免每帧 findViewById ═══════════

    /** 缓存的 BottomSheetBehavior — 供滚动监听器使用 */
    private var cachedSheetBehavior: BottomSheetBehavior<*>? = null

    /** 缓存的 GridLayoutManager — 供 onScrolled 使用 */
    private var cachedGridLayoutManager: GridLayoutManager? = null

    /** Toast 防抖时间戳 */
    private var toastLastShowTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutBottomSheetUiBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 是否已经初始化过（防止 onViewCreated 重复回调） */
    private var setupDone = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (setupDone) return
        setupDone = true

        // 1. ★ 先初始化照片 Tab（设置 adapter + helper → ViewPager2）
        setupPhotoTab()

        // 2. 绑定胶囊 Tab（ViewPager2 已有 adapter）
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text =
                if (position == 0) PickerStrings.CHINESE.photoTab else PickerStrings.CHINESE.albumTab
        }.attach()

        // 3. 关闭/完成按钮 — 收集选中的 URI 并返回
        binding.ivClose.setOnClickListener {
            val result = photosHelper?.let { helper ->
                val ids = helper.getSelectedItemIds()
                photosData.filterIsInstance<GroupedAdapter.Item>()
                    .filter { it.id in ids }
                    .map { it.uri }
            } ?: emptyList()
            onComplete(result)
            dismiss()
        }

        // 4. 加载首页数据
        loadInitialMediaPage()
    }

    // ═══════════════════ 照片 Tab 初始化 ═══════════════════

    private fun setupPhotoTab() {
        val ctx = requireContext()
        dataLoader = MediaDataLoader(ctx)

        // ── 构造优化后的 RecyclerView ──
        cachedGridLayoutManager = GridLayoutManager(ctx, 3).apply {
            initialPrefetchItemCount = 20 // ★ 预取 6+ 行，快速 fling 无白屏
        }
        val rvPhotos = RecyclerView(ctx).apply {
            layoutManager = cachedGridLayoutManager
            setHasFixedSize(true)                     // 跳过 requestLayout 级联
            setItemViewCacheSize(45)                  // 保持 45 个 VH attach（15 可见 + 30 缓冲）
            recycledViewPool.setMaxRecycledViews(GroupedAdapter.TYPE_HEADER, 10)
            recycledViewPool.setMaxRecycledViews(GroupedAdapter.TYPE_ITEM, 90)  // ★ 深回收池
            itemAnimator = null                       // 无默认动画 — 消除 layout 重排
            clipChildren = false
            clipToPadding = false

            // ★ 滚动监听 — 热路径上不创建对象，使用缓存的引用
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {}

                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    // ★ 列表顶部继续下拉 → 关闭 BottomSheet
                    if (dy < 0 && !rv.canScrollVertically(-1)) {
                        cachedSheetBehavior?.isHideable = true
                        cachedSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
                        return
                    }
                    if (dy <= 0) return
                    // ★ 滚动到底部前 5 个 → 触发分页加载
                    val lm = cachedGridLayoutManager ?: return
                    if (lm.findLastVisibleItemPosition() >= (photosAdapter?.itemCount ?: 0) - 5
                        && mediaHasMore && !mediaLoading
                    ) {
                        loadNextMediaPage()
                    }
                }
            })
        }

        // ── 多选处理器（长按进入，滑动连续多选） ──
        photosHelper = MultiSelectHelper(
            rvPhotos,
            GroupedAdapter.TYPE_HEADER,
            idProvider = { pos -> photosAdapter?.getItemIdAt(pos) ?: -1L }
        ).apply {
            onPositionToggled = { pos -> photosAdapter?.notifySelectionChanged(pos) }
            onSelectionChanged = { count ->
                if (count > maxCount) {
                    val now = System.currentTimeMillis()
                    // ★ Toast 防抖 2 秒 — 防止快速多选时堆积 Toast 队列
                    if (now - toastLastShowTime > 2000L) {
                        toastLastShowTime = now
                        Toast.makeText(ctx, "最多选择 ${maxCount} 张", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // ── 核心适配器 ──
        photosAdapter = GroupedAdapter(photosData, photosHelper!!)
        rvPhotos.adapter = photosAdapter
        cachedGridLayoutManager!!.spanSizeLookup = photosAdapter!!.spanSizeLookup

        // ★ 首次 layout 完成后强制计算正方形尺寸
        // 解决 ViewPager2 内 RecyclerView 首次 layout 时 width==0 导致图片条目无高度
        rvPhotos.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            private var done = false
            override fun onLayoutChange(
                v: View?, l: Int, t: Int, r: Int, b: Int,
                ol: Int, ot: Int, or: Int, ob: Int
            ) {
                if (!done && r - l > 0) {
                    done = true
                    photosAdapter?.recalcSquareSize(rvPhotos)
                }
            }
        })

        // ── 影集 Tab（占位） ──
        val rvAlbums = RecyclerView(ctx).apply {
            layoutManager = GridLayoutManager(ctx, 2)
        }

        // ── ViewPager2 页面 ──
        val page0 = android.widget.FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(
                rvPhotos, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        val page1 = android.widget.FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(
                rvAlbums, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        // ★ ViewPager2 使用简单的占位 Adapter（2 个静态页面）
        binding.viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 2
            override fun getItemViewType(p: Int) = p
            override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
                object : RecyclerView.ViewHolder(if (vt == 0) page0 else page1) {}

            override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {}
        }
    }

    // ═══════════════════ 分页加载 ═══════════════════

    /** 加载首页数据 — IO 线程查询 + 主线程更新 UI */
    private fun loadInitialMediaPage() {
        mediaLoading = true
        lifecycleScope.launch {
            try {
                val media = withContext(Dispatchers.IO) {
                    dataLoader?.loadPage(0, null) ?: emptyList()
                }
                val grouped = DateSectionHelper.insertSections(media) { it.dateModified }
                photosData.clear()
                photosData.addAll(convertToAdapterItems(grouped))
                mediaHasMore = media.size >= MediaDataLoader.PAGE_SIZE
                mediaOffset = media.size
                photosAdapter?.notifyDataSetChanged()
                photosHelper?.validatePositions()
            } finally {
                mediaLoading = false
            }
        }
    }

    /** 加载下一页数据 */
    private fun loadNextMediaPage() {
        if (mediaLoading || !mediaHasMore) return
        mediaLoading = true
        lifecycleScope.launch {
            try {
                val media = withContext(Dispatchers.IO) {
                    dataLoader?.loadPage(mediaOffset, null) ?: emptyList()
                }
                if (media.isEmpty()) {
                    mediaHasMore = false
                    return@launch
                }
                val grouped = DateSectionHelper.insertSections(media) { it.dateModified }
                val oldSize = photosData.size
                photosData.addAll(convertToAdapterItems(grouped))
                mediaOffset += media.size
                mediaHasMore = media.size >= MediaDataLoader.PAGE_SIZE
                photosAdapter?.notifyItemRangeInserted(oldSize, photosData.size - oldSize)
            } finally {
                mediaLoading = false
            }
        }
    }

    /**
     * ★ 将 [DateSectionHelper] 的输出转换为 [GroupedAdapter] 的数据类型。
     * DateSectionHelper 使用自己的 SectionHeader + MediaItem，
     * GroupedAdapter 使用自己的 Header + Item。
     */
    private fun convertToAdapterItems(items: List<Any>): List<Any> = items.map {
        when (it) {
            is DateSectionHelper.SectionHeader -> GroupedAdapter.Header(it.text)
            is MediaItem -> GroupedAdapter.Item(
                id = it.id,
                uri = it.uri,
                isVideo = it.isVideo,
                durationMs = it.durationMs
            )

            else -> it
        }
    }

    // ═══════════════════ BottomSheet 行为配置 ═══════════════════

    override fun onStart() {
        super.onStart()
        dialog?.let { d ->
            val sheet =
                d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (sheet != null) {
                val behavior = BottomSheetBehavior.from(sheet)
                cachedSheetBehavior = behavior // ★ 缓存引用 — 给滚动热路径使用

                // ★ 半屏模式：60% 高度展开，上滑全屏，下滑回半屏，顶部下拉关闭
                behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                behavior.isFitToContents = false
                behavior.halfExpandedRatio = 0.6f
                behavior.skipCollapsed = true     // 跳过折叠态，直接从半屏到隐藏
                behavior.isHideable = true

                behavior.addBottomSheetCallback(object :
                    BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                            dismiss()
                            activity?.finish()
                        }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {}
                })
            }
        }
    }

    // ═══════════════════ 清理 ═══════════════════

    override fun onDestroyView() {
        super.onDestroyView()
        // ★ 重置初始化标志 — onDestroyView → onCreateView 后可重新初始化
        setupDone = false
        // ★ 清空所有引用，防止内存泄漏
        cachedSheetBehavior = null
        cachedGridLayoutManager = null
        photosHelper?.detach()
        dataLoader = null
        photosData.clear()
        photosAdapter = null
        _binding = null
    }
}
