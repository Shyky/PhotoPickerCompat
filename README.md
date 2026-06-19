# photo-picker-compat

> Android 图片/视频选择器兼容库 — 生产级 · 极致流畅 · 线程安全 · 零内存泄漏

---

## 目录

- [1. 概述](#1-概述)
- [2. 系统架构](#2-系统架构)
- [3. 包结构](#3-包结构)
- [4. 快速开始](#4-快速开始)
- [5. 核心API详解](#5-核心api详解)
- [6. DSL完整参考](#6-dsl完整参考)
- [7. 数据流与时序](#7-数据流与时序)
- [8. 线程安全模型](#8-线程安全模型)
- [9. 极致滚动流畅度](#9-极致滚动流畅度)
- [10. 内存管理](#10-内存管理)
- [11. 性能基准](#11-性能基准)
- [12. 性能调优实战](#12-性能调优实战)
- [13. 扩展点](#13-扩展点)
- [14. 权限详解](#14-权限详解)
- [15. 兼容性矩阵](#15-兼容性矩阵)
- [16. 构建配置](#16-构建配置)
- [17. 故障排查](#17-故障排查)
- [18. 变更日志](#18-变更日志)

---

## 1. 概述

### 1.1 是什么

`photo-picker-compat` 是 Android 图片/视频选择器的统一抽象层。它在不同 Android 版本上提供一致的API：

| 路径        | 适用API | 底层实现                                          | 体验 |
|-----------|-------|-----------------------------------------------|----|
| **系统选择器** | 33+   | `PickVisualMedia` / `PickMultipleVisualMedia` | 原生 |
| **系统降级**  | 26–32 | `ACTION_GET_CONTENT` / `GetMultipleContents`  | 原生 |
| **内置网格**  | 26+   | BottomSheet + RecyclerView + Coil             | 统一 |

### 1.2 核心能力

```
✅ 单张/多张图片选择        ✅ 单张/多段视频选择
✅ 图片+视频混合选择        ✅ API 35+ 默认相册标签页
✅ 内置网格多选器          ✅ 长按+滑动连续多选
✅ MediaStore 分页加载      ✅ 按日期自动分组（今天/昨天/星期…）
✅ 相册列表浏览            ✅ 拍照入口
✅ DSL 构建器              ✅ Fragment 一行注册
✅ 类型安全结果映射         ✅ 中间件拦截链
✅ 条目过滤器（GIF/大小）   ✅ 插件扩展系统
✅ 国际化的字符串           ✅ 暗色模式支持
✅ 埋点接口                ✅ 可定制 UI
```

### 1.3 设计目标

- **零崩溃**：无 `!!` 运算符，无 `null!!`，全部可空参数有 fallback
- **极致流畅**：60fps 滚动，多选零帧丢失
- **内存可控**：分页窗口裁剪，生命周期绑定
- **线程安全**：全部共享可变状态同步保护
- **类型安全**：泛型 `ResultMapper<T>` 消除运行时 cast

---

## 2. 系统架构

### 2.1 完整架构图

```
┌──────────────────────────────────────────────────────────────────────┐
│                          APPLICATION LAYER                           │
│                                                                      │
│  val pick = registerForPhotoPicker(9) { uris -> handle(uris) }      │
│  pick()  // 一行启动                                                │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    ▼                         ▼
┌──────────────────────────────┐ ┌────────────────────────────────┐
│     PHOTOPICKERCOMPAT        │ │       PICKMEDIA<T>() DSL       │
│  (系统PhotoPicker路由)       │ │  (内置网格DSL)                 │
│                              │ │                                │
│  image() → PickImageContract │ │ pickMedia<Uri> {               │
│  multipleImages(n) → ...     │ │   maxCount = 9                 │
│  video() → PickVideoContract │ │   photoTab { … }               │
│  multipleVideos(n) → ...     │ │   onResult { uris -> … }       │
│  imageAndVideo(tab) → ...    │ │ }                               │
│  multipleMedia(n, tab) → ... │ │                                │
│  gridPicker(n) → ...         │ │ 返回 PickerConfiguration<T>    │
└──────────────┬───────────────┘ └───────────────┬────────────────┘
               │                                 │
               ▼                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     ACTIVITYRESULTCONTRACT LAYER                      │
│                                                                      │
│  ┌─────────────────────┐  ┌──────────────────────────────────────┐  │
│  │ PickImageContract   │  │  MediaPickerContract<T>              │  │
│  │ PickMultipleImages  │  │    → createIntent()                  │  │
│  │ PickVideoContract   │  │    → parseResult()                   │  │
│  │ PickMultipleVideos  │  │       PickResult<T>                  │  │
│  │ PickImageAndVideo   │  │       ├─ Selected(uris, mapped)      │  │
│  │ PickMultipleMedia   │  │       ├─ Cancelled                  │  │
│  │ GridPickerContract  │  │       └─ Error(throwable)            │  │
│  └─────────────────────┘  └──────────────────┬───────────────────┘  │
└──────────────────────────────────────────────┼────────────────────────┘
                                               │ Intent
                                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                  PickerTransparentActivity                            │
│  - 透明窗口主题                                                       │
│  - overridePendingTransition(0,0) ← 无闪烁                            │
│  - Handler.postDelayed(50ms) 显示 BottomSheet                        │
│  - onComplete → setResult(RESULT_OK) → finish()                      │
└──────────────────────────────────┬───────────────────────────────────┘
                                   │ supportFragmentManager
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│              MediaPickerBottomSheetFragment                           │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  layout_bottom_sheet_ui.xml                                    │ │
│  │  ┌──────────────────────────────────────────────────────────┐ │ │
│  │  │ CoordinatorLayout                                         │ │ │
│  │  │  ├─ maskOverlay (半透明遮罩)                               │ │ │
│  │  │  └─ CardView (圆角24dp)                                   │ │ │
│  │  │      └─ LinearLayout                                      │ │ │
│  │  │          ├─ grabHandle (40×4dp 拖动条)                    │ │ │
│  │  │          ├─ privacyHint ("此应用只能访问您选择的照片")      │ │ │
│  │  │          ├─ ivClose (关闭/完成按钮)                        │ │ │
│  │  │          ├─ TabLayout (胶囊式 照片|影集)                   │ │ │
│  │  │          └─ ViewPager2                                    │ │ │
│  │  │              ├─ page0: FrameLayout → RecyclerView (3列)   │ │ │
│  │  │              └─ page1: FrameLayout → RecyclerView (2列)   │ │ │
│  │  └──────────────────────────────────────────────────────────┘ │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  BottomSheetBehavior:                                                 │
│    STATE_HALF_EXPANDED → 上滑全屏 → 顶部下滑关闭                     │
│    halfExpandedRatio = 0.6f                                          │
│    skipCollapsed = true                                              │
└──────────────────────────────────┬───────────────────────────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
┌──────────────────────┐ ┌────────────────┐ ┌──────────────────────┐
│   RecyclerView       │ │ MultiSelect    │ │  PagingController    │
│   (Photo Tab)        │ │ Helper         │ │                      │
│                      │ │                │ │  loadInitial()       │
│ GridLayoutManager(3) │ │ 长按→进入多选  │ │  loadNext()          │
│ setHasFixedSize=true │ │ 滑动→连续多选  │ │  dispose()           │
│ setHasStableIds=true │ │ 自动边缘滚动   │ │  debounce 200ms      │
│ itemAnimator=null    │ │ [PAYLOAD机制]  │ │                      │
│ itemViewCacheSize=30 │ │ onPosition     │ └──────────┬───────────┘
│ initialPrefetch=12   │ │ Toggled回调    │            │
│ recycledViewPool=60  │ │                │            ▼
└──────────┬───────────┘ └────────────────┘ ┌──────────────────────┐
           │                                │  PickerDataSource     │
           ▼                                │  (接口)               │
┌──────────────────────┐                    │                      │
│  GroupedAdapter      │                    │  loadInitialPage()    │
│  extends RV.Adapter  │                    │  loadNextPage(offset) │
│                      │                    │  loadAlbums()         │
│ items: MutableList   │                    │  hasMore              │
│  ├─ Header("今天")   │                    │  currentOffset        │
│  ├─ Item(id,uri,...) │                    │  pageSize             │
│  ├─ Item(...)        │                    └──────────┬───────────┘
│  ├─ Header("昨天")   │                               │
│  └─ ...              │                    ┌──────────▼───────────┐
│                      │                    │ MediaStoreDataSource  │
│ getItemViewType():   │                    │  (实现)               │
│  Header→TYPE_HEADER  │                    │                      │
│  Item  →TYPE_ITEM    │                    │ loader: MediaData     │
│                      │                    │        Loader         │
│ spanSizeLookup:      │                    └──────────┬───────────┘
│  Header→span 3       │                               │
│  Item  →span 1       │                    ┌──────────▼───────────┐
│                      │                    │  MediaDataLoader      │
│ PAYLOAD_SELECTION    │                    │                      │
│ →bindSelectionOnly() │                    │ loadPage(offset,      │
│   (跳过Coil重载)     │                    │   bucketId, filter)   │
│                      │                    │   → List<MediaItem>   │
│ trimOldest(count)    │                    │                      │
│ →窗口裁剪老数据      │                    │ loadAlbums()          │
└──────────┬───────────┘                    │   → List<Album>       │
           │                                │                      │
           ▼                                │ ALBUM_SCAN_LIMIT=5000 │
┌──────────────────────┐                    │ PAGE_SIZE=40          │
│  SelectionManager    │                    │ QUERY_ARG_OFFSET/     │
│                      │                    │ LIMIT (API 26+)       │
│ _selected: Set<Uri>  │                    └──────────┬───────────┘
│ @Synchronized        │                               │
│ toggle/select/unsel  │                    ┌──────────▼───────────┐
│ maxCount 拦截        │                    │  MediaStore           │
│ onSelectionChanged   │                    │  EXTERNAL_CONTENT_URI │
└──────────────────────┘                    └──────────────────────┘

═══════════════ 辅助设施层 ═══════════════

┌──────────────────┐ ┌──────────────────┐ ┌───────────────────────┐
│SelectionItem     │ │DateSectionHelper │ │ItemTransform          │
│Decoration        │ │                  │ │                       │
│                  │ │formatSection(ts) │ │GifFilter: 过滤.gif    │
│onDraw(canvas):   │ │→ "今天"/"昨天"/  │ │SizeFilter: 过滤>maxB  │
│ 只画选中item     │ │  "星期三"/       │ │                       │
│ selectedPaint    │ │  "6月19日"/      │ │transform(item,pos)→?  │
│ borderPaint      │ │  "2025年6月"     │ │返回null=过滤掉        │
│ early-exit if    │ │                  │ │                       │
│ 无选中项         │ │insertSections()  │ │                       │
└──────────────────┘ └──────────────────┘ └───────────────────────┘

┌──────────────────┐ ┌──────────────────┐ ┌───────────────────────┐
│PickerMiddleware  │ │PickerPlugin      │ │ResultMapper<T>        │
│                  │ │                  │ │                       │
│onBeforeLoad()    │ │id: String        │ │map(uris, adapter)→T   │
│onAfterLoad()     │ │onInstall(host)   │ │                       │
│onBeforeSelect()  │ │onUninstall()     │ │URI_LIST: 返回uris     │
│onBeforeDeselect()│ │                  │ │of(block)→ResultMapper │
│onBeforeReturn()  │ │PickerHost:       │ │                       │
│onOpened/onClosed │ │ registerDecor    │ │                       │
└──────────────────┘ │ registerTouch    │ └───────────────────────┘
                     │ onSelectionChg   │
                     │ addOverlay       │
                     │ removeOverlay    │
                     └──────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                     配置/字符串/主题                             │
│                                                                  │
│ PickerConfiguration<T>   PickerStrings        PickerAnalytics    │
│ PickerScope<T>           UiOverrides          PickerLogger       │
│ LayoutAdaptor            SelectionStrategy     OverlayManager     │
│ PickerComponentProvider  TabAdapterRegistry   OverlayManager     │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 关键设计决策

| 决策                                        | 原因                         |
|-------------------------------------------|----------------------------|
| ViewBinding 替代 `findViewById`             | 编译期类型安全，零 NPE 风险           |
| `SparseBooleanArray` 存选择状态                | 比 `HashSet<Int>` 更省内存（无装箱） |
| `LinkedHashSet<Uri>` 存选择顺序                | O(1) 查重 + 保持插入顺序           |
| `notifyDataSetChanged()` 在 trim 时         | 跨页删除无法精确算偏移，NDC 安全可靠       |
| 独立 `CoroutineScope` + `SupervisorJob`     | 子协程失败不影响其他协程               |
| BottomSheet 而非 Activity                   | 半屏体验、不打断当前页面               |
| `GridLayoutManager(3)` 而非 `StaggeredGrid` | 确定性能，无回流                   |

---

## 3. 包结构

```
com.shyky.tech.photo.picker.compat/          # 公开 API 入口 (3)
├── picker.kt                       顶层 DSL 入口函数（2个重载）
├── PhotoPickerCompat.kt            系统选择器静态工厂
└── FragmentExt.kt                  Fragment 扩展函数

compat/contract/                     # ActivityResultContract (3)
├── PhotoPickerContract.kt          系统 PhotoPicker Contract 集合
├── MediaPickerContract.kt          ActivityResultContract<T>
└── GridPickerContract.kt           List<Uri> Contract

compat/config/                       # DSL 配置 (13)
├── PickerConfiguration.kt          配置不可变对象 + Builder
├── PickerScope.kt                  DSL 作用域类
├── TabScope.kt / MiddlewareScope.kt / TransformScope.kt / PluginScope.kt  DSL scope
├── SelectionStrategy.kt            选择策略（单选/多选）
├── LayoutAdaptor.kt                布局适配器
├── PickerStrings.kt                ★ 字符串接口
├── PickerAnalytics.kt              埋点接口
├── PickerLogger.kt                 日志器
├── UiOverrides.kt                  UI 覆盖配置
└── PickerComponentProvider.kt      组件工厂

compat/ui/                           # UI 容器 (2)
├── PickerTransparentActivity.kt    透明宿主 Activity
└── MediaPickerBottomSheetFragment.kt  ★ BottomSheet 选择器

compat/adapter/                      # RecyclerView 适配器 (5)
├── SelectableAdapter.kt            Tab 适配器接口
├── GroupedAdapter.kt               ★ 核心适配器（Header+Item+Payload）
├── DefaultMediaAdapter.kt          默认实现
├── PhotoPickerAdapter.kt           独立备选适配器
└── TabAdapterRegistry.kt           Tab 适配器注册表

compat/selection/                    # 选择管理 (4)
├── SelectionManager.kt             线程安全状态管理
├── MultiSelectHelper.kt            多选触摸（长按+滑动+边缘滚动）
├── MultiSelectTouchHelper.kt       备选多选触摸实现
└── SelectionItemDecoration.kt      选中装饰绘制

compat/data/                         # 数据层 (6)
├── PickerDataSource.kt             数据源接口 + LoadResult
├── MediaStoreDataSource.kt         ★ MediaStore 实现
├── FlowPickerDataSource.kt         Flow 数据源
├── MediaDataLoader.kt              ★ MediaStore 分页加载器
├── PagingController.kt             分页加载控制器
└── MediaFile.kt                    Parcelable 媒体文件

compat/pipeline/                     # 扩展链 (3)
├── PickerMiddleware.kt             中间件接口
├── ItemTransform.kt                条目变换（GifFilter, SizeFilter）
└── PickerPlugin.kt                 插件接口 + PickerHost

compat/result/                       # 结果映射 (2)
├── ResultMapper.kt                 结果映射器 fun interface
└── PickResult.kt                   选择结果密封类

compat/util/                         # 工具 (2)
├── DateSectionHelper.kt            日期分组
└── OverlayManager.kt               覆盖层管理
```

---

## 4. 快速开始

### 4.1 添加依赖

```kotlin
// 主工程 build.gradle.kts
dependencies {
    implementation(project(":library-photo-picker-compat"))
}
```

### 4.2 最简用法：系统选择器

```kotlin
class MainActivity : AppCompatActivity() {

    // 单张图片（Android 13+ 原生 PhotoPicker，低版本自动降级）
    private val pickImage = registerForActivityResult(
        PhotoPickerCompat.image()
    ) { uri: Uri? ->
        uri?.let { imageView.setImageURI(it) }
    }

    // 多张图片（最多 9 张）
    private val pickImages = registerForActivityResult(
        PhotoPickerCompat.multipleImages(maxCount = 9)
    ) { uris: List<Uri> ->
        adapter.submitList(uris)
    }

    // 图片+视频混合
    private val pickMedia = registerForActivityResult(
        PhotoPickerCompat.multipleMedia(
            maxCount = 9,
            defaultTab = PickDefaultTab.ALBUMS  // API 35+ 生效
        )
    ) { uris: List<Uri> -> handleMedia(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<Button>(R.id.btnPick).setOnClickListener {
            pickImages.launch(Unit)
        }
    }
}
```

### 4.3 内置网格选择器（统一体验，所有API 26+）

```kotlin
class MyFragment : Fragment() {

    // 方式1：一行注册（最简）
    private val pickGrid = registerForPhotoPicker(maxCount = 9) { uris: List<Uri> ->
        viewModel.handleSelectedUris(uris)
    }

    // 方式2：完全自定义 DSL
    private val pickCustom = registerForMediaPicker<MyResult> {
        maxCount = 9
        scope = lifecycleScope
        dataSource = MediaStoreDataSource(requireContext())

        photoTab {
            label = "照片"
            adapterFactory = { DefaultMediaAdapter(it).apply { maxSelectCount = 9 } }
            showCameraEntry = true
        }

        onResult { uris ->
            MyResult(uris, uris.size)
        }

        transforms { +GifFilter() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSelect.setOnClickListener { pickGrid() }
        binding.btnCustom.setOnClickListener { pickCustom() }
    }
}
```

### 4.4 典型结果处理

```kotlin
private val pickPhotos = registerForPhotoPicker(maxCount = 9) { uris ->
    lifecycleScope.launch {
        // 1. 复制到应用私有目录
        val localPaths = uris.map { uri ->
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val localFile = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            inputStream?.use { input ->
                localFile.outputStream().use { output -> input.copyTo(output) }
            }
            localFile.absolutePath
        }

        // 2. 上传
        viewModel.uploadPhotos(localPaths)

        // 3. 更新 UI
        adapter.addAll(uris)
    }

    // 或直接显示
    previewAdapter.submitList(uris)
}
```

---

## 5. 核心API详解

### 5.1 PhotoPickerCompat — 系统选择器工厂

```kotlin
object PhotoPickerCompat {

    // 单张图片 → Uri?
    fun image(): PickImageContract

    // 多张图片 → List<Uri>
    // maxCount: null=不限制(API 33+)，低版本调用方自行截断
    fun multipleImages(maxCount: Int? = null): PickMultipleImagesContract

    // 单个视频 → Uri?
    fun video(): PickVideoContract

    // 多个视频 → List<Uri>
    fun multipleVideos(maxCount: Int? = null): PickMultipleVideosContract

    // 图片/视频混合 单张 → Uri?
    // defaultTab: API 35+ 指定 PHOTOS | ALBUMS | UNSPECIFIED
    fun imageAndVideo(
        defaultTab: PickDefaultTab = PickDefaultTab.UNSPECIFIED
    ): PickImageAndVideoContract

    // 图片/视频混合 多张 → List<Uri>
    fun multipleMedia(
        maxCount: Int? = null,
        defaultTab: PickDefaultTab = PickDefaultTab.UNSPECIFIED
    ): PickMultipleMediaContract

    // 内置网格选择器 → List<Uri>
    // 长按进入多选模式，滑动连续多选
    fun gridPicker(maxCount: Int = 9): GridPickerContract
}
```

**各 Contract 的回退策略：**

| Contract                     | API 33+                             | API 26–32                                     |
|------------------------------|-------------------------------------|-----------------------------------------------|
| `PickImageContract`          | `PickVisualMedia(ImageOnly)`        | `GetContent("image/*")`                       |
| `PickMultipleImagesContract` | `PickMultipleVisualMedia(maxCount)` | `GetMultipleContents("image/*")`              |
| `PickVideoContract`          | `PickVisualMedia(VideoOnly)`        | `GetContent("video/*")`                       |
| `PickMultipleVideosContract` | `PickMultipleVisualMedia(maxCount)` | `GetMultipleContents("video/*")`              |
| `PickImageAndVideoContract`  | `PickVisualMedia(ImageAndVideo)`    | `ACTION_GET_CONTENT` + MIME array             |
| `PickMultipleMediaContract`  | `PickMultipleVisualMedia(maxCount)` | `ACTION_GET_CONTENT` + `EXTRA_ALLOW_MULTIPLE` |

### 5.2 PickerConfiguration — 配置对象

```kotlin
class PickerConfiguration<T> internal constructor(
    val maxCount: Int,                     // 最大选择数（自动 coerceAtMost(999)）
    val tabs: List<TabSpec>,               // Tab 列表（至少1个）
    val dataSource: PickerDataSource,      // 数据源
    val middleware: List<PickerMiddleware>, // 中间件链
    val transforms: List<ItemTransform>,   // 条目变换链
    val plugins: List<PickerPlugin>,       // 插件列表
    val layoutAdaptor: LayoutAdaptor,      // 布局配置（列数/间距）
    val resultMapper: ResultMapper<T>,      // 结果类型映射
    val strings: PickerStrings,            // 字符串资源
    val analytics: PickerAnalytics,         // 埋点
    val uiOverrides: UiOverrides,           // UI 微调
    val logger: PickerLogger,              // 日志
    val scope: CoroutineScope,             // 协程作用域
    val componentProvider: PickerComponentProvider, // 组件工厂
    val strategy: SelectionStrategy         // 单选/多选
) {

    // Tab 规格
    data class TabSpec(
        val label: String,
        val adapterFactory: (Context) -> SelectableAdapter<*>,
        val showCameraEntry: Boolean = false,
        val showPrivacyHint: Boolean = true
    )

    // 构建器
    class Builder<T> internal constructor(...) {
        fun strategy(s: SelectionStrategy)       // 单选/多选
        fun dataSource(ds: PickerDataSource)     // 数据源（必填）
        fun layoutAdaptor(la: LayoutAdaptor)     // 布局
        fun strings(s: PickerStrings)            // 字符串
        fun analytics(a: PickerAnalytics)        // 埋点
        fun uiOverrides(u: UiOverrides)          // UI
        fun logger(l: PickerLogger)              // 日志
        fun scope(s: CoroutineScope)             // 协程作用域（必填）
        fun componentProvider(cp: PickerComponentProvider)
        fun addTab(tab: TabSpec)                 // 添加 Tab
        fun addMiddleware(m: PickerMiddleware)   // 添加中间件
        fun addTransform(t: ItemTransform)       // 添加变换
        fun addPlugin(p: PickerPlugin)           // 添加插件
        fun <R : Any> resultMapper(mapper: (List<Uri>) -> R)  // 结果类型映射
        fun build(): PickerConfiguration<T>       // 构建（校验必填项）
    }
}
```

### 5.3 SelectableAdapter — Tab 适配器接口

```kotlin
interface SelectableAdapter<T : Any> {

    // ── RecyclerView 集成 ──
    val recyclerAdapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>

    // ── 条目查询 ──
    fun isSelectable(position: Int): Boolean
    fun isCameraPosition(position: Int): Boolean = false
    fun getItemViewType(position: Int): Int
    fun getItemId(position: Int): Long
    fun getItemUri(position: Int): Uri?
    fun getItemMimeType(position: Int): String? = null

    // ── 选择操作 ──
    fun toggle(position: Int)
    fun select(position: Int)
    fun unselect(position: Int)
    val selectedUris: Set<Uri>
    val selectedCount: Int
    var maxSelectCount: Int
    var onSelectionChanged: ((count: Int) -> Unit)?

    // ── 生命周期 ──
    fun onAttached(recyclerView: RecyclerView) {}
    fun onDetached() {}
    fun saveState(): Bundle
    fun restoreState(state: Bundle?)

    // ── 数据管理 ──
    fun submitList(items: List<Any>)
    fun appendPage(items: List<Any>)
    val itemCount: Int

    // ── 布局 ──
    fun spanSizeLookup(): GridLayoutManager.SpanSizeLookup
    fun createItemDecoration(): RecyclerView.ItemDecoration? = null

    // ── 事件 ──
    var onItemClick: ((position: Int) -> Unit)?
    var onCameraClick: (() -> Unit)?
}
```

### 5.4 GroupedAdapter — 核心适配器

```kotlin
class GroupedAdapter(
    internal val items: MutableList<Any>,    // ★ 数据源（Header | Item）
    val helper: MultiSelectHelper? = null,    // 多选处理器（nullable）
    private val selectedChecker: ((Int) -> Boolean)? = null  // 选择状态查询
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // ── 数据模型 ──
    data class Header(val title: String)     // 日期分组标题
    data class Item(                         // 媒体条目
        val id: Long,                        // MediaStore._ID
        val uri: Uri,                        // content:// URI
        val isVideo: Boolean = false,        // 是否为视频
        val durationMs: Long = 0             // 视频时长(毫秒)
    )

    // ── 类型常量 ──
    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    // ── 核心方法 ──
    // Payload 局部刷新（多选时跳过图片重载）
    fun notifySelectionChanged(position: Int)

    // 从头部裁切旧数据（控制内存）
    fun trimOldest(count: Int)

    // 缓存的 SpanSizeLookup（零 GC 分配）
    val spanSizeLookup: GridLayoutManager.SpanSizeLookup

    // 按位置获取媒体 ID
    fun getItemIdAt(position: Int): Long

    // ViewHolder 类
    class HeaderViewHolder(binding: ItemSectionHeaderBinding)
    class ItemViewHolder(binding: ItemMediaGridBinding) {
        fun bind(item: Item, isSelected: Boolean)      // 完整绑定（含 Coil 加载）
        fun bindSelectionOnly(isSelected: Boolean)      // ★ 快速局部刷新
    }
}
```

### 5.5 SelectionManager — 线程安全选择管理

```kotlin
class SelectionManager(
    private val selectable: SelectableAdapter<*>,
    private val onMaxExceeded: () -> Unit = {}
) {
    val selectedUris: Set<Uri>       // ★ 线程安全快照 (.toSet())
    val count: Int                   // ★ 同步读取

    @Synchronized
    fun toggle(uri: Uri, position: Int)

    @Synchronized
    fun select(uri: Uri)

    @Synchronized
    fun unselect(uri: Uri)

    @Synchronized
    fun isSelected(uri: Uri): Boolean

    @Synchronized
    fun clear()

    @Synchronized
    fun restore(uris: Set<Uri>)
}
```

### 5.6 MultiSelectHelper — 多选触摸处理

```kotlin
class MultiSelectHelper(
    private val recyclerView: RecyclerView,
    private val headerViewType: Int,
    private val idProvider: (position: Int) -> Long
) {
    var isMultiSelectMode: Boolean        // 是否多选模式
    val selectedPositions: SparseBooleanArray  // 位置→选中状态

    // 状态回调
    var onSelectionChanged: ((count: Int) -> Unit)?
    var onModeChanged: ((inMode: Boolean) -> Unit)?
    var onPositionToggled: ((position: Int) -> Unit)?   // ★ 用于 Payload 更新

    fun toggleSelection(position: Int)
    fun clearSelection()
    fun enterMultiSelectMode()
    fun exitMultiSelectMode()
    fun getSelectedItemIds(): Set<Long>
    fun restoreSelection(itemIds: Set<Long>)
    fun rebuildAfterTrim()                // ★ trim 后重建位置映射
    fun validatePositions()
    fun detach()                          // 移除监听器和装饰
}
```

### 5.7 SelectionItemDecoration — 选中装饰

```kotlin
class SelectionItemDecoration(
    private val selectedPositions: SparseBooleanArray,
    private val headerViewType: Int
) : RecyclerView.ItemDecoration() {

    // onDraw: 遍历可见 child，对选中的画蓝色填充+描边
    // ★ 无选中项时 early exit（零绘制开销）
    override fun onDraw(c: Canvas, parent: RecyclerView, state: State)

    fun needsRedraw(): Boolean  // 是否有需要重绘的项
}
```

---

## 6. DSL完整参考

### 6.1 `pickMedia<T>()` — 顶层入口

```kotlin
// 重载1：完全 DSL
@JvmSynthetic
inline fun <reified T : Any> pickMedia(
    block: PickerScope<T>.() -> Unit
): PickerConfiguration<T>

// 重载2：一键默认配置
@JvmSynthetic
fun pickMedia(
    context: Context,
    maxCount: Int = 99,
    scope: CoroutineScope
): PickerConfiguration<List<Uri>>
```

### 6.2 `PickerScope<T>` — 所有 DSL 属性的容器

```kotlin
@PickerDsl
class PickerScope<T> {
    // ── 基础配置 ──
    var maxCount: Int = 99
    var dataSource: PickerDataSource? = null      // ★ 必填
    var scope: CoroutineScope? = null              // ★ 必填

    // ── UI 配置 ──
    var layoutAdaptor: LayoutAdaptor = LayoutAdaptor.DEFAULT
    var strings: PickerStrings = PickerStrings.CHINESE
    var analytics: PickerAnalytics = PickerAnalytics.NOOP
    var uiOverrides: UiOverrides = UiOverrides.DEFAULT
    var logger: PickerLogger = PickerLogger.NOOP
    var componentProvider: PickerComponentProvider = PickerComponentProvider.DEFAULT

    // ── DSL 块 ──
    fun middleware(block: MiddlewareScope.() -> Unit)
    fun transforms(block: TransformScope.() -> Unit)
    fun plugins(block: PluginScope.() -> Unit)
    fun photoTab(block: TabScope.() -> Unit)
    fun albumTab(block: TabScope.() -> Unit)

    // ── 结果映射 ──
    fun <R : Any> onResult(block: (List<Uri>) -> R)

    // ── 选择模式 ──
    fun singleMode()
    fun multiMode(max: Int = 99)

    // ── 构建 ──
    fun build(): PickerConfiguration<T>
}
```

### 6.3 DSL 完整示例

```kotlin
val config = pickMedia<MySelectionResult> {

    // ═══════════ 基础配置 ═══════════
    maxCount = 9
    scope = viewModelScope
    dataSource = MediaStoreDataSource(requireContext())

    // ═══════════ UI 主题 ═══════════
    strings = object : PickerStrings {
        override val photoTab = "图片"
        override val albumTab = "相册"
        override val doneButton = "完成"
        override val selectButton = "选择"
        override val cancelButton = "取消"
        override val privacyHint = "仅你选择的照片会被分享"
        override val permissionDenied = "请授权存储权限"
        override val maxCountExceeded = { n -> "最多选择 $n 张" }
        override val cameraEntry = "拍照"
        override val loadingMessage = "加载中…"
        override val emptyMessage = "没有找到媒体文件"
        override val errorRetry = "重试"
        override val editPreview = "编辑"
        override val originalPreview = "原图"
    }

    uiOverrides = UiOverrides(
        accentColor = Color.parseColor("#FF6B35"),
        closeIconTint = Color.WHITE,
        privacyHintVisibility = View.VISIBLE
    )

    layoutAdaptor = object : LayoutAdaptor {
        override fun columnCount(widthPx: Int, density: Float) =
            if (widthPx / density >= 600f) 4 else 3
        override val itemSpacingDp = 2
    }

    // ═══════════ Tab 配置 ═══════════
    photoTab {
        label = "照片"
        adapterFactory = { ctx ->
            DefaultMediaAdapter(ctx).apply {
                maxSelectCount = 9
                onSelectionChanged = { count ->
                    Timber.d("已选择 $count 张")
                }
                onCameraClick = {
                    // 处理拍照
                }
            }
        }
        showCameraEntry = true
        showPrivacyHint = true
    }

    albumTab {
        label = "相册"
        adapterFactory = { AlbumAdapter(it) }
    }

    // ═══════════ 选择策略 ═══════════
    multiMode(max = 9)
    // singleMode()  // 或单选

    // ═══════════ 结果映射 ═══════════
    onResult { uris ->
        MySelectionResult(
            uris = uris,
            count = uris.size,
            hasVideo = uris.any { it.isVideo() }
        )
    }

    // ═══════════ 中间件 ═══════════
    middleware {
        +object : PickerMiddleware {
            override suspend fun onBeforeSelect(uri: Uri, position: Int): Boolean {
                // 禁止选择大于 50MB 的文件
                val size = getFileSize(uri)
                return size <= 50 * 1024 * 1024
            }

            override suspend fun onBeforeReturn(
                result: List<Uri>,
                adapter: SelectableAdapter<*>
            ): List<Uri> {
                return result.take(maxCount)
            }
        }
    }

    // ═══════════ 条目过滤 ═══════════
    transforms {
        +GifFilter()                      // 过滤 GIF
        +SizeFilter(maxBytes = 100 * 1024 * 1024)  // 过滤大文件
    }

    // ═══════════ 埋点 ═══════════
    analytics = object : PickerAnalytics {
        override fun onPickerOpened(maxCount: Int) {
            Firebase.analytics.logEvent("picker_opened") { … }
        }
        override fun onPickerClosed(resultCount: Int, cancelled: Boolean) {
            …
        }
        override fun onSelectionChanged(count: Int) {
            …
        }
        override fun onAlbumSwitched(albumName: String) {
            …
        }
        override fun onCameraUsed() {
            …
        }
        override fun onPreviewOpened() {
            …
        }
        override fun onError(throwable: Throwable, context: String) {
            …
        }
    }

    // ═══════════ 日志 ═══════════
    logger = if (BuildConfig.DEBUG)
        PickerLogger.debug("MyPicker")
    else
        PickerLogger.release("MyPicker")
}
```

### 6.4 Fragment 扩展函数

```kotlin
// 类型安全的 DSL 注册
@JvmSynthetic
inline fun <reified T : Any> Fragment.registerForMediaPicker(
    crossinline configBlock: PickerScope<T>.() -> Unit
): () -> Unit

// 快速注册（返回 List<Uri>）
@JvmSynthetic
fun Fragment.registerForPhotoPicker(
    maxCount: Int = 9,
    onResult: (List<Uri>) -> Unit
): () -> Unit
```

### 6.5 ResultMapper — 结果类型映射

```kotlin
fun interface ResultMapper<T> {
    fun map(selected: List<Uri>, adapter: SelectableAdapter<*>?): T

    companion object {
        // 默认：原样返回 URI 列表
        val URI_LIST: ResultMapper<List<Uri>>

        // 工厂：从 Lambda 创建
        fun <T> of(block: (List<Uri>) -> T): ResultMapper<T>
    }
}

// PickResult 密封类
sealed class PickResult<out T> {
    data class Selected<T>(val uris: List<Uri>, val mapped: T) : PickResult<T>()
    object Cancelled : PickResult<Nothing>()
    data class Error(val throwable: Throwable) : PickResult<Nothing>()
}
```

### 6.6 TabScope DSL

```kotlin
@PickerDsl
class TabScope {
    var label: String = ""           // Tab 标签文字
    var adapterFactory: ((Context) -> SelectableAdapter<*>)? = null  // ★ 必填
    var showCamera: Boolean = false  // 是否显示拍照入口
    var showPrivacyHint: Boolean = true  // 是否显示隐私提示
}
```

### 6.7 MiddlewareScope & TransformScope & PluginScope

```kotlin
@PickerDsl
class MiddlewareScope {
    operator fun PickerMiddleware.unaryPlus()  // +middleware 语法
}

@PickerDsl
class TransformScope {
    operator fun ItemTransform.unaryPlus()     // +transform 语法
}

@PickerDsl
class PluginScope {
    operator fun PickerPlugin.unaryPlus()      // +plugin 语法
}
```

---

## 7. 数据流与时序

### 7.1 选择器启动流程

```
调用方                  Contract            Activity          Fragment
  │                        │                   │                 │
  │ launcher.launch(Unit)  │                   │                 │
  │───────────────────────►│                   │                 │
  │                        │ createIntent()    │                 │
  │                        │──────────────────►│                 │
  │                        │                   │ onCreate()      │
  │                        │                   │ overridePending │
  │                        │                   │ Transition(0,0) │
  │                        │                   │                 │
  │                        │                   │ postDelayed(50) │
  │                        │                   │────────────────►│
  │                        │                   │                 │ show()
  │                        │                   │                 │ STATE_HALF_
  │                        │                   │                 │ EXPANDED
  │                        │                   │                 │
  │                        │                   │                 │ setupPhotoTab()
  │                        │                   │                 │ ├─ MediaDataLoader
  │                        │                   │                 │ ├─ RecyclerView
  │                        │                   │                 │ ├─ MultiSelectHelper
  │                        │                   │                 │ ├─ GroupedAdapter
  │                        │                   │                 │ └─ TabLayoutMediator
  │                        │                   │                 │
  │                        │                   │                 │ loadInitialPage()
  │                        │                   │                 │ ├─ loadPage(0)
  │                        │                   │                 │ ├─ insertSections()
  │                        │                   │                 │ └─ submitList()
```

### 7.2 分页加载时序

```
用户滚动              onScrolled            PagingController       DataSource
  │                       │                       │                   │
  │ fling                 │                       │                   │
  │──►ScrollListener─────►│                       │                   │
  │                       │ lastVisible >=        │                   │
  │                       │ itemCount-5           │                   │
  │                       │──────────────────────►│                   │
  │                       │                       │ debounce 200ms    │
  │                       │                       │ (跳过快速滚动)    │
  │                       │                       │                   │
  │                       │                       │ loadNext()        │
  │                       │                       │──────────────────►│
  │                       │                       │                   │ loadNextPage()
  │                       │                       │                   │────►Dispatchers.IO
  │                       │                       │                   │     loadPage(offset)
  │                       │                       │                   │     sortByDesc
  │                       │                       │                   │     drop+take
  │                       │                       │                   │◄────List<MediaItem>
  │                       │                       │                   │ insertSections()
  │                       │                       │◄──LoadResult──────│
  │                       │                       │                   │
  │                       │                       │ appendPage()      │
  │                       │◄──adapter.appendPage──│                   │
  │                       │                       │                   │
  │                       │                       │ if loadedPageCount
  │                       │                       │ > MAX_CACHED_PAGES:
  │                       │                       │   trimOldest()
  │                       │                       │   rebuildAfterTrim()
```

### 7.3 多选操作时序

```
用户操作          GestureDetector      MultiSelectHelper    Adapter        Decoration
  │                     │                     │                │               │
  │ 长按                │                     │                │               │
  │────────────────────►│                     │                │               │
  │                     │ onLongPress()       │                │               │
  │                     │────────────────────►│                │               │
  │                     │                     │ isMultiSelect  │               │
  │                     │                     │ Mode = true    │               │
  │                     │                     │ toggle(pos)    │               │
  │                     │                     │───────────────►│               │
  │                     │                     │                │ Payload:      │
  │                     │                     │                │ SELECTION     │
  │                     │                     │                │ bindSelection │
  │                     │                     │                │ Only()        │
  │                     │                     │                │ (零Coil重载)   │
  │                     │                     │ invalidate     │               │
  │                     │                     │───────────────►│──────────────►│
  │                     │                     │                │               │ onDraw()
  │ 手指滑动            │                     │                │               │
  │────────────────────►│                     │                │               │
  │                     │ onTouchEvent(MOVE)  │                │               │
  │                     │────────────────────►│                │               │
  │                     │                     │ handleMove()   │               │
  │                     │                     │ ┌isSwipeSelect │               │
  │                     │                     │ ├findChild     │               │
  │                     │                     │ ├toggle(pos)   │               │
  │                     │                     │ ├Payload更新    │               │
  │                     │                     │ ├invalidDeco   │               │
  │                     │                     │ └autoScroll    │               │
  │                     │                     │   if near edge │               │
  │                     │                     │                │               │
  │ 完成/关闭           │                     │                │               │
  │────────────────────►│                     │                │               │
  │                     │                     │ getSelected    │               │
  │                     │                     │ ItemIds()      │               │
  │                     │                     │ → filterItems  │               │
  │                     │                     │ → uris list    │               │
  │                     │                     │ → onComplete() │               │
  │                     │                     │ → finish()     │               │
```

---

## 8. 线程安全模型

### 8.1 线程架构

```
┌─────────────────────────────────────────────────────────────┐
│                        MAIN THREAD                          │
│  - 所有 View 操作（包括 adapter notify*）                    │
│  - SelectionItemDecoration.onDraw                          │
│  - 选择状态回调 (onSelectionChanged)                        │
│  - PagingController scope = Dispatchers.Main               │
└──────────────────────────┬──────────────────────────────────┘
                           │ withContext(Dispatchers.IO)
┌──────────────────────────▼──────────────────────────────────┐
│                       IO THREAD                             │
│  - MediaDataLoader.loadPage()                              │
│  - MediaDataLoader.loadAlbums()                            │
│  - Cursor 遍历                                             │
│  - DateSectionHelper.insertSections()                      │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 同步策略

| 类                                            | 共享状态                             | 同步方式                                                       | 线程   |
|----------------------------------------------|----------------------------------|------------------------------------------------------------|------|
| `SelectionManager`                           | `_selected: LinkedHashSet<Uri>`  | `@Synchronized` 方法级                                        | Main |
| `PhotoPickerAdapter`                         | `selectedUris`, `selectionOrder` | `@Synchronized` 方法级                                        | Main |
| `DefaultMediaAdapter`                        | `_selectedUris`                  | `synchronized` getter + `selectedChecker` 中 `synchronized` | Main |
| `DefaultMediaAdapter.saveState/restoreState` | `_selectedUris`                  | `@Synchronized`                                            | 任意   |
| `MediaDataLoader`                            | 无                                | `withContext(Dispatchers.IO)` 隔离                           | IO   |
| `GroupedAdapter.items`                       | `MutableList<Any>`               | 仅 Main 线程操作                                                | Main |
| `MultiSelectHelper.selectedPositions`        | `SparseBooleanArray`             | 仅 Main 线程操作（触摸回调）                                          | Main |
| `TabAdapterRegistry`                         | `HashMap<Int, TabEntry>`         | 仅主线程操作                                                     | Main |

### 8.3 协程作用域管理

```kotlin
// PagingController 两种作用域模式：

// 模式1：显式绑定到 lifecycleScope（推荐）
PagingController(dataSource, adapter, parentScope = lifecycleScope)
// → SupervisorJob 继承 parentScope context
// → parent cancel 时自动取消所有子协程

// 模式2：独立作用域（兼容旧代码）
PagingController(dataSource, adapter)
// → Dispatchers.Main + SupervisorJob()
// → 必须调用 dispose() 释放

fun dispose() {
    cancelAll()        // cancel current jobs
    scope.cancel()     // cancel scope itself
    dataSource.close() // close cursor/stream
}
```

---

## 9. 极致滚动流畅度

### 9.1 优化清单

| #  | 优化措施                                                  | 技术原理                                             | 收益                            |
|----|-------------------------------------------------------|--------------------------------------------------|-------------------------------|
| 1  | `setHasFixedSize(true)`                               | 跳过 `requestLayout` 级联                            | 每次 notify 省 1 次 measure pass  |
| 2  | `setHasStableIds(true)`                               | Diff 用 ID 而非 position                            | 减少 50%+ 的 rebind              |
| 3  | `itemAnimator = null`                                 | 无 DefaultItemAnimator                            | 消去 layout 重排                  |
| 4  | `initialPrefetchItemCount = 12`                       | GapWorker 提前构建                                   | 快速 fling 无白屏                  |
| 5  | `setItemViewCacheSize(30)`                            | 离屏 VH 保持 attach                                  | 返回滚动零 onCreate                |
| 6  | `recycledViewPool.setMaxRecycledViews(TYPE_ITEM, 60)` | 深回收池                                             | 快速 fling 不 wait recycle       |
| 7  | Payload 局部刷新                                          | `bindSelectionOnly()` 跳过 Coil                    | 多选时零图片重载                      |
| 8  | `SpanSizeLookup` `by lazy`                            | 单例缓存                                             | 每次 layout pass 省 1 次分配        |
| 9  | `precomputedSquareSize`                               | 父宽度计算，非 `view.post{}`                            | 消去 40 个 post runnable         |
| 10 | `onScrolled` 缓存引用                                     | `cachedSheetBehavior`, `cachedGridLayoutManager` | 每帧零 `findViewById` 零 cast     |
| 11 | `SelectionItemDecoration` early-exit                  | `selectedPositions.size() == 0 → return`         | 非多选时零绘制开销                     |
| 12 | Toast debounce (2 秒)                                  | 防止快速多选堆积 Toast 队列                                | 无 UI 线程阻塞                     |
| 13 | Coil `crossfade(false)` + `size(300)`                 | 无淡入动画，缩略图尺度                                      | 滚动时无额外合成层                     |
| 14 | FrameLayout 替换 ConstraintLayout + 双 View 合并为单 View    | 零约束求解 + 减少可见 View 数                              | overdraw 2层，每帧省 constraint 求解 |
| 15 | Coil 自动 cancel 前一个 request                            | `image.load()` 内部 cancel                         | 快速滚动不堆积请求                     |

### 9.2 热路径性能剖析

```
onCreateViewHolder per item:
  ItemSectionHeaderBinding.inflate  ───  ~0.3ms
  ViewBinding.bind(root)            ───  ~0.1ms
  Total                             ───  ~0.4ms  (一次性)

onBindViewHolder per item (完整):
  items[position] as Item          ───  ~0.001ms
  helper?.selectedPositions?.get   ───  ~0.002ms (SparseBooleanArray)
  image.load(uri) { size(300) }    ───  async (不占主线程)
  visibility 设置 × 5               ───  ~0.05ms
  Total                            ───  ~0.06ms + async I/O

onBindViewHolder per item (Payload SELECTION):
  items[position] as Item          ───  ~0.001ms
  bindSelectionOnly()              ───  ~0.03ms (仅 visibility 切换)
  Total                            ───  ~0.03ms  ← 比完整 bind 快 2×
                                             ← 比旧版(含Coil重载)快 20×

onScrolled per frame:
  rv.canScrollVertically(-1)       ───  ~0.001ms
  cachedSheetBehavior?.state       ───  ~0.001ms
  cachedGridLayoutManager?.find    ───  ~0.05ms
  Total                            ───  ~0.05ms  (零分配)

SelectionItemDecoration.onDraw (多选模式):
  for child in visible:            ───  循环 ~15 个可见 item
    getChildAdapterPosition        ───  ~0.001ms × 15
    getItemViewType                ───  ~0.001ms × 15
    selectedPositions[pos]         ───  ~0.002ms × 15 (SparseBooleanArray 二分)
    drawRect × 2                   ───  ~0.02ms × 选中数
  Total                            ───  ~0.1ms  (GPU 完成)
```

### 9.3 性能反模式（已消除）

| 反模式                                     | 问题                               | 状态                |
|-----------------------------------------|----------------------------------|-------------------|
| `view.post { layoutParams = … }`        | 每个 VH 一个 post → Choreographer 堆积 | ✅ 消除              |
| `findViewById` 在 bind 中                 | 每帧树遍历                            | ✅ ViewBinding     |
| `SpanSizeLookup()` 每次 `new`             | GC 抖动                            | ✅ `by lazy`       |
| `notifyDataSetChanged` 在多选时             | 全部 rebind + Coil 重载              | ✅ Payload         |
| `invalidateItemDecorations` 无检查         | 没有选择也触发重绘                        | ✅ early-exit      |
| `findViewById` 在 `onScrolled` 中         | 每帧树查找                            | ✅ 缓存              |
| Toast 无 debounce                        | 滑动多选堆积 Toast 队列                  | ✅ 2 秒 debounce    |
| `SimpleDateFormat` 在 `onBindViewHolder` | 每次 bind 创建                       | ✅ `_dateFmt` 缓存对象 |

---

## 10. 内存管理

### 10.1 内存布局

```
每个 MediaItem 对象:    ~72 字节（id 8 + uri ref 8 + bool 1 + 3×long 24 + path ref 8 + padding）
每个 Header 对象:       ~32 字节（title ref 8 + padding）
每页 (40 items):        ~3KB + 若干 Header ~128B
8 页窗口:               ~25KB 纯数据 + ~2KB Header

Coil MemoryCache:
  size(300) 缩略图:     ~30KB/张 (ARGB_8888, 300×300 解码)
  Coil 默认 cache:      Runtime.getRuntime().maxMemory() / 4
  例: 256MB heap → 64MB cache → ~2100 张缓存

ViewHolder:
  每个 ItemViewHolder:   ~200 字节（7 个 View ref + 自身）
  缓存池 60 + cache 30:  ~18KB

总计峰值（正常使用）:
  Data:     ~25KB
  Coil:     ~15MB (实际加载的缩略图)
  Views:    ~18KB
  ──────────────────
  ~15MB    ← 合理且可控
```

### 10.2 窗口裁剪策略

```
滑动方向：向下 → 加载更新页 → 老页在前端

trimOldest(trimCount):
  ├── 如果 items 不足 trimCount → 全删（不会发生）
  ├── 删除前 trimCount 个条目
  ├── 检查新头部是否为 Header
  │   ├── 是 Header → 无需处理
  │   └── 不是 Header → 插入空白 Header("")
  └── notifyDataSetChanged()

rebuildAfterTrim():
  ├── getSelectedItemIds() → 获取当前选中的 Item ID
  ├── selectedPositions.clear()
  ├── 遍历 adapter 所有位置
  │   └── 如果 item.id 在选中集合中 → put(pos, true)
  └── invalidateItemDecorations()
```

### 10.3 生命周期引用管理

```
对象引用图:
  PickerTransparentActivity
  ├── MediaPickerBottomSheetFragment
  │    ├── photosData: MutableList<Any>
  │    ├── photosAdapter: GroupedAdapter
  │    │    └── items: MutableList<Any> (=== photosData)
  │    ├── photosHelper: MultiSelectHelper
  │    │    ├── selectedPositions: SparseBooleanArray
  │    │    ├── recyclerView: RecyclerView (弱引用)
  │    │    └── scrollHandler: Handler(mainLooper)
  │    ├── dataLoader: MediaDataLoader
  │    │    └── context: Context (Application Context)
  │    ├── cachedSheetBehavior → View 引用(GC可达)
  │    └── cachedGridLayoutManager → View 引用
  └── (无静态引用，Activity finish → GC 全部回收)

onDestroyView:
  cachedSheetBehavior = null     ← 解除 View 引用
  cachedGridLayoutManager = null
  photosHelper?.detach()          ← 移除 TouchListener + Decoration
  dataLoader = null               ← Cursor 由 use{} 自动关闭
  photosData.clear()              ← 释放数据
  photosAdapter = null
  _binding = null                 ← ViewBinding 释放
```

### 10.4 潜在内存风险（已防护）

| 风险           | 防护措施                                          |
|--------------|-----------------------------------------------|
| 分页数据无限增长     | `MAX_CACHED_PAGES = 8`，第 9 页触发 `trimOldest()` |
| 相册扫描遍历 10 万行 | `ALBUM_SCAN_LIMIT = 5000`                     |
| Coil 内存缓存过大  | Coil 默认 1/4 heap，`size(300)` 限制单图尺寸           |
| 协程泄漏         | `Scope.cancel()` on dispose，parentScope 自动取消  |
| Handler 泄漏   | `removeCallbacks()` on detach/stop            |
| View 泄漏      | `onDestroyView` 清空所有 View 引用                  |
| Context 泄漏   | MediaDataLoader 存的是参数传入的 context，由调用方管理       |

---

## 11. 性能基准

| 指标                | 典型值       | 条件                          |
|-------------------|-----------|-----------------------------|
| 首屏加载延迟            | 200–400ms | 40 items, MediaStore 冷查询    |
| 翻页加载延迟            | 100–200ms | 40 items, MediaStore 热查询    |
| 滚动帧率              | 60fps     | Profile GPU Rendering 无超标竖线 |
| 多选单次刷新            | <0.5ms    | Payload 局部刷新                |
| 滑动选择（手指扫过10个item） | <5ms      | Payload × 10                |
| 内存峰值              | ~15MB     | 8 页窗口 + Coil cache          |
| APK 增量            | ~50KB     | 编译后 dex                     |
| 方法数               | ~300      | dex 方法计数                    |
| 启动 Activity       | <100ms    | 透明 Activity + 50ms delay    |

---

## 12. 性能调优实战

> 代码层优化到极致后，剩余卡顿来自 GPU 渲染管线。本章指导你从现象到根因。

### 12.1 确认是否有卡顿

**最快方式：GPU 渲染柱状图**

```
设备 → 开发者选项 → 监控 → GPU 呈现模式分析 → "在屏幕上显示为条形图"
```

滑动列表，看底部柱状图：

- 全部在绿线（16ms）以下 → ✅ 60fps，无需优化
- 有柱子超过绿线 → 🚨 掉帧，继续排查

**命令行量化：**

```bash
# 重置统计
adb shell dumpsys gfxinfo com.your.app.package reset

# 滑动列表 10 秒后抓数据
adb shell dumpsys gfxinfo com.your.app.package framestats

# 关键指标
```

| 指标                | 含义            | 正常值   | 超标含义      |
|-------------------|---------------|-------|-----------|
| `Janky frames`    | 超 16.67ms 的帧数 | <5%   | 用户能感知到卡顿  |
| `50th percentile` | 中位帧耗时         | <8ms  | 过半帧是否轻松   |
| `90th percentile` | P90 帧耗时       | <12ms | 大部分滚动是否流畅 |
| `99th percentile` | P99 帧耗时       | <20ms | 最差帧是否可接受  |

### 12.2 用 Perfetto 定位瓶颈帧

**浏览器直接录制（零安装，推荐）：**

1. 打开 `https://ui.perfetto.dev`
2. 左上角 `Record new trace`，勾选数据源：

```
Android apps & services:
  ✅ gfx       # ★ 图形渲染管线（SwapBuffers/dequeueBuffer）
  ✅ view      # ★ View 系统（measure/layout/draw）
  ✅ input     # 触摸事件
  ✅ dalvik    # GC 事件
  ✅ res       # 资源加载
Scheduling & CPU:
  ✅ Scheduling details     # CPU 调度分布
  ✅ CPU frequency & idle   # 是否触发温控降频
```

3. 点 `Start Recording`
4. **立刻在设备上快速滑动选择器列表 10 秒**
5. 停止录制，打开 trace

### 12.3 分析瓶颈帧：四段式定位法

展开 `Choreographer#doFrame` 轨道，每一帧分为四段：

```
┌──────────────────────────────────────────────────────────┐
│ Choreographer#doFrame                                    │
│ ┌─────────┐ ┌──────────┐ ┌───────────┐ ┌──────────┐   │
│ │ INPUT   │ │ANIMATION │ │ TRAVERSAL │ │ DRAW     │   │
│ │ ~0.5ms  │ │ ~0ms     │ │ ??? ms    │ │ ??? ms   │   │
│ └─────────┘ └──────────┘ └───────────┘ └──────────┘   │
│                                         └─ 瓶颈在哪？ ──┘
└──────────────────────────────────────────────────────────┘
```

| 瓶颈段        | 正常耗时 | 超时含义              | 排查方向                                  |
|------------|------|-------------------|---------------------------------------|
| INPUT      | <1ms | 触摸处理有阻塞           | `onTouchEvent` 或 `GestureDetector` 耗时 |
| TRAVERSAL  | <8ms | measure/layout 太重 | View 层级过深、约束求解多、`requestLayout` 泛滥    |
| DRAW       | <8ms | GPU 渲染跟不上         | overdraw、纹理尺度、Shader 复杂度              |
| CPU freq 掉 | —    | 温控降频              | 设备发热，CPU 被限速                          |

### 12.4 深挖 DRAW 段：GPU 渲染管线分析

**第一步：看 `eglSwapBuffers` 耗时**

展开 `gfx` 轨道，找到 `eglSwapBuffers`：

```
eglSwapBuffers < 5ms   → ✅ GPU 轻松，渲染无压力
eglSwapBuffers 5-10ms  → ⚠️  GPU 接近饱和
eglSwapBuffers 10-16ms → 🔴 GPU 满负荷
eglSwapBuffers > 16ms  → 🚨 确定掉帧，GPU 跟不上 vsync

如果 eglSwapBuffers 高，子项：
  eglSwapBuffersWithDamageKHR  ← 最耗时？→ 帧缓冲区合成慢
  eglMakeCurrent               ← 高？→ 上下文切换开销
  GPU Fence 等待               ← 高？→ GPU 完成渲染滞后
```

**第二步：检查 Overdraw**

```bash
# 开启 overdraw 可视化
adb shell setprop debug.hwui.overdraw show

# 看本库 item 格子颜色：
#   原色 = 1层 ✅
#   蓝   = 2层 ✅（本库正常状态）
#   绿   = 3层 ⚠️
#   深红 = 4层+ 🚨 需修复

# 操作完关闭
adb shell setprop debug.hwui.overdraw false
```

本库 `item_media_grid.xml` 用 FrameLayout（零约束求解），非选中+非视频仅 2 层 overdraw（ImageView +
空心圈）。

**第三步：检查纹理尺度**

```bash
# 看 Coil 实际加载的图片分辨率
adb logcat -s Coil:V | grep -E "size|decode"

# 如果解码后的尺寸远大于 300px → 调整 size() 参数
```

### 12.5 深挖 TRAVERSAL 段：measure/layout 分析

如果瓶颈在 TRAVERSAL 而不是 DRAW：

```bash
# systrace 抓 view 标签（比 Perfetto 更适合 measure/layout 分析）
cd ~/Android/Sdk/platform-tools/systrace
python3 systrace.py --time=10 -o layout_trace.html view gfx input sched
```

在 Chrome 打开 `chrome://tracing` 加载 `layout_trace.html`，展开：

```
RecyclerView
├── onMeasure    ← 高？→ setHasFixedSize 是否生效？
├── onLayout     ← 高？→ item 数量是否异常多？
├── layoutChildren
│   ├── ViewHolder 创建  ← 高？→ recycledViewPool 不够深
│   ├── View 绑定       ← 高？→ onBindViewHolder 的 Coil load 是否异步？
│   └── SpanSizeLookup  ← 高？→ 是否用 lazy 缓存？
└── ItemDecoration.onDraw ← 高？→ 是否 early-exit？
```

本库对应优化措施：

| 如果这项高                   | 本库已做                                                     |
|-------------------------|----------------------------------------------------------|
| `onMeasure`             | `setHasFixedSize(true)`                                  |
| ViewHolder 创建           | `recycledViewPool: 60 TYPE_ITEM`、`itemViewCacheSize: 30` |
| View 绑定                 | Payload 局部刷新、Coil async load                             |
| `SpanSizeLookup`        | `by lazy` 单例缓存                                           |
| `ItemDecoration.onDraw` | 无选中项时 `return`                                           |

### 12.6 本库热路径基准值

滑动列表 10 秒后 `dumpsys gfxinfo` 的预期值：

| 操作               | P50  | P90  | P99  | Janky%       |
|------------------|------|------|------|--------------|
| 慢速滚动（首次加载）       | 5ms  | 10ms | 16ms | <3%          |
| 快速 fling         | 6ms  | 12ms | 20ms | <5%          |
| 多选滑动（Payload 生效） | 4ms  | 8ms  | 14ms | <2%          |
| trim 后首次滚动       | 10ms | 18ms | 25ms | 一次性（仅 8 页一次） |

如果 Janky% 高于预期，按 12.3 四段法定位。

### 12.7 如果确认是 GPU 瓶颈 — 降级方案

代码已无优化空间，只能降低 GPU 负载：

| 方案      | 改动                             | 效果                       |
|---------|--------------------------------|--------------------------|
| 降低缩略图尺寸 | `size(300)` → `size(200)`      | 纹理内存 ↓56%，draw 调用 ↓      |
| 开启硬件层   | `android:layerType="hardware"` | 纹理缓存到 GPU，省去重绘           |
| 减少网格密度  | `columnCount = 2`              | 同屏 item 数 ↓33%，draw 调用 ↓ |
| 关闭半透明   | mask `background="#FF000000"`  | 省去 alpha 合成              |

### 12.8 验证优化效果

```bash
# 优化前后各录一次，对比帧率

# Before
adb shell dumpsys gfxinfo com.your.app.package reset
# …滑动 10 秒…
adb shell dumpsys gfxinfo com.your.app.package framestats > before.txt

# After（改完代码重新打包后）
adb shell dumpsys gfxinfo com.your.app.package reset
# …滑动 10 秒…
adb shell dumpsys gfxinfo com.your.app.package framestats > after.txt

# 对比
grep "Janky frames" before.txt after.txt
grep "99th percentile" before.txt after.txt

# Perfetto SQL 验证（在 ui.perfetto.dev 中执行）
SELECT
  COUNT(*) AS total_frames,
  ROUND(CAST(SUM(CASE WHEN dur > 16000000 THEN 1 ELSE 0 END) AS FLOAT) / COUNT(*) * 100, 1) AS jank_pct,
  ROUND(AVG(dur) / 1000000, 2) AS avg_ms
FROM slice
WHERE name = 'Choreographer#doFrame';
```

---

## 13. 扩展点

### 13.1 自定义数据源

```kotlin
class RemoteDataSource(
    private val api: MediaApiService,
    private val albumId: String
) : PickerDataSource {

    private var page = 0
    override val pageSize = 40

    override var hasMore = true
        private set
    override var currentOffset = 0
        private set

    override suspend fun loadInitialPage(): LoadResult<List<Any>> {
        page = 0
        return loadPageInternal()
    }

    override suspend fun loadNextPage(offset: Int): LoadResult<List<Any>> {
        page++
        currentOffset = offset
        return loadPageInternal()
    }

    private suspend fun loadPageInternal(): LoadResult<List<Any>> {
        return try {
            val response = api.getMedia(albumId, page, pageSize)
            hasMore = response.hasMore
            currentOffset = page * pageSize
            LoadResult.Success(response.items.map { it.toMediaItem() })
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override suspend fun loadAlbums(): LoadResult<List<PickerDataSource.AlbumEntry>> {
        return try {
            val albums = api.getAlbums().map { it.toAlbumEntry() }
            LoadResult.Success(albums)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun close() {
        // 取消网络请求等
    }
}
```

### 13.2 自定义适配器

```kotlin
class WaterfallAdapter(context: Context) : SelectableAdapter<WaterfallAdapter.CardItem> {

    data class CardItem(val uri: Uri, val width: Int, val height: Int)

    private val impl = object : RecyclerView.Adapter<CardVH>() {
        // … 实现 StaggeredGrid 布局
    }

    override val recyclerAdapter get() = impl
    override val selectedUris get() = _selected
    override val selectedCount get() = _selected.size

    // x 实现所有 SelectableAdapter 方法
    // …

    inner class CardVH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.image)
        // …
    }
}
```

### 13.3 自定义布局适配器

```kotlin
val tabletLayout = object : LayoutAdaptor {
    override fun columnCount(widthPx: Int, density: Float): Int {
        val widthDp = widthPx / density
        return when {
            widthDp >= 840 -> 6   // 平板横屏
            widthDp >= 600 -> 4   // 平板竖屏
            else -> 3             // 手机
        }
    }
    override val itemSpacingDp = 2
}

val paddedLayout = object : LayoutAdaptor {
    override fun columnCount(widthPx: Int, density: Float) = 3
    override val itemSpacingDp = 4  // 更大间距
}
```

### 13.4 自定义中间件

```kotlin
// 权限检查中间件
class PermissionMiddleware(
    private val permissionChecker: () -> Boolean,
    private val onDenied: () -> Unit
) : PickerMiddleware {
    override suspend fun onBeforeLoad(page: Int, source: PickerDataSource): Boolean {
        if (!permissionChecker()) {
            onDenied(); return false
        }
        return true
    }
}

// 压缩中间件（返回时压缩大图）
class CompressMiddleware(
    private val maxSize: Int = 1920,
    private val quality: Int = 85
) : PickerMiddleware {
    override suspend fun onBeforeReturn(
        result: List<Uri>,
        adapter: SelectableAdapter<*>
    ): List<Uri> = result.map { uri -> compressIfNeeded(uri, maxSize, quality) }
}
```

### 13.5 自定义插件

```kotlin
class WatermarkPlugin : PickerPlugin {
    override val id = "watermark"

    override fun onInstall(host: PickerHost) {
        host.registerItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDrawOver(c: Canvas, parent: RecyclerView, state: State) {
                // 在缩略图上绘制水印
            }
        })
    }

    override fun onUninstall() {
        // 移除水印
    }
}

// 使用
plugins { +WatermarkPlugin() }
```

---

## 14. 权限详解

### 14.1 系统 PhotoPicker（PhotoPickerCompat.*）

| API   | 权限       | 说明                                     |
|-------|----------|----------------------------------------|
| 33+   | **无需权限** | 系统 PhotoPicker 自带沙箱，不需要 `READ_MEDIA_*` |
| 26–32 | 无需权限     | `ACTION_GET_CONTENT` 通过系统文件选择器，不需要存储权限 |

### 14.2 内置网格选择器（GridPickerContract / pickMedia）

| API   | 所需权限                                     | Manifest 声明                                                                   |
|-------|------------------------------------------|-------------------------------------------------------------------------------|
| 33+   | `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` | `<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />`     |
| 26–32 | `READ_EXTERNAL_STORAGE`                  | `<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />` |

### 14.3 运行时权限请求示例

```kotlin
private fun checkPermissionAndPick() {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    if (ContextCompat.checkSelfPermission(requireContext(), permission)
        == PackageManager.PERMISSION_GRANTED
    ) {
        pickGrid()
    } else {
        requestPermissionLauncher.launch(permission)
    }
}
```

---

## 15. 兼容性矩阵

### 15.1 Android 版本覆盖

| 功能                          | API 26–28 | API 29–32 | API 33–34 | API 35+ |
|-----------------------------|-----------|-----------|-----------|---------|
| 系统 PhotoPicker              | ✗         | ✗         | ✅         | ✅       |
| 系统降级 (GetContent)           | ✅         | ✅         | ✗         | ✗       |
| 内置网格选择器                     | ✅         | ✅         | ✅         | ✅       |
| `QUERY_ARG_OFFSET/LIMIT`    | ✅         | ✅         | ✅         | ✅       |
| `PickVisualMedia`           | ✗         | ✗         | ✅         | ✅       |
| 默认相册标签页                     | ✗         | ✗         | ✗         | ✅       |
| Coil `allowHardware(false)` | ✅         | ✗         | ✗         | ✗       |

### 15.2 厂商 ROM 兼容

| ROM            | 已知问题                  | 状态                       |
|----------------|-----------------------|--------------------------|
| MIUI (小米)      | 部分版本 `COUNT(_ID)` 被拒绝 | ✅ 已修复（改用标准列）             |
| ColorOS (OPPO) | MediaStore 排序个别差异     | ✅ 代码中 `sortByDescending` |
| EMUI (华为)      | 无已知问题                 | ✅                        |
| OneUI (三星)     | 无已知问题                 | ✅                        |
| 原生 Android     | 无已知问题                 | ✅                        |

---

## 16. 构建配置

### 16.1 完整 `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.shyky.tech.photo.picker"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true    // ★ 必须启用
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.constraintlayout)

    // Image loading
    implementation("io.coil-kt:coil:2.7.0")

    // Material Design
    implementation(libs.material)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
```

### 16.2 ViewBinding 类名映射

| 布局文件                          | 生成的 Binding 类                  |
|-------------------------------|--------------------------------|
| `item_media_grid.xml`         | `ItemMediaGridBinding`         |
| `item_section_header.xml`     | `ItemSectionHeaderBinding`     |
| `layout_bottom_sheet_ui.xml`  | `LayoutBottomSheetUiBinding`   |
| `item_album_row.xml`          | `ItemAlbumRowBinding`          |
| `item_album_folder.xml`       | `ItemAlbumFolderBinding`       |
| `item_selected_thumbnail.xml` | `ItemSelectedThumbnailBinding` |
| `popup_album_list.xml`        | `PopupAlbumListBinding`        |
| `activity_photo_picker.xml`   | `ActivityPhotoPickerBinding`   |
| `layout_tab_custom.xml`       | `LayoutTabCustomBinding`       |

---

## 17. 故障排查

### 17.1 编译错误

| 错误                                                           | 原因                                        | 解决                                                                |
|--------------------------------------------------------------|-------------------------------------------|-------------------------------------------------------------------|
| `Unresolved reference 'Item'` / `'ItemSectionHeaderBinding'` | ViewBinding 未生成                           | 确保 `buildFeatures { viewBinding = true }`                         |
| `Unresolved reference 'PickerLogger'`                        | 文件被删除                                     | PickerLogger 被 `PickerConfiguration`/`PickerScope`/`picker.kt` 引用 |
| `Platform declaration clash: getItemCount()I`                | `val itemCount` 与 `fun getItemCount()` 冲突 | 删除 `fun getItemCount()`                                           |
| `Class is not abstract and does not implement...`            | `companion object` 括号缺失                   | 检查 `{ }` 配对                                                       |

### 17.2 运行时错误

| 错误                                                 | 原因                                         | 解决                                                         |
|----------------------------------------------------|--------------------------------------------|------------------------------------------------------------|
| `IllegalStateException: dataSource must be set`    | DSL 块未设置 dataSource                        | 添加 `dataSource = MediaStoreDataSource(context)`            |
| `IllegalStateException: scope must be set`         | 未设 CoroutineScope                          | DSL 中 `scope = viewModelScope` 或用 `registerForPhotoPicker` |
| `IllegalStateException: At least one tab required` | 无 Tab 配置                                   | 添加 `photoTab { … }` 或 `albumTab { … }`                     |
| `NullPointerException: tv_section_label`           | `item_section_header.xml` root 即为 TextView | 使用 ViewBinding（自动绑定）                                       |
| `NullPointerException: adapterFactory`             | TabScope 未设 adapterFactory                 | 在 `photoTab {}` 中设置 `adapterFactory = { … }`               |
| 图片不显示/空白                                           | 缺少存储权限                                     | 检查 `READ_MEDIA_IMAGES` 权限                                  |
| 列表滚动后再选中项错位                                        | 未调用 `rebuildAfterTrim`                     | trimOldest 后立即调用 `rebuildAfterTrim()`                      |

### 17.3 性能问题

| 问题             | 可能原因             | 解决                                        |
|----------------|------------------|-------------------------------------------|
| 滚动卡顿           | 设备 GPU 弱 / 图片过大  | 将 `size(300)` 降为 `size(200)`              |
| 首屏加载慢          | 媒体库过大            | 减少 `PAGE_SIZE` 或使用 `bucketId` 过滤          |
| 内存溢出           | 未触发 trim         | 检查 `MAX_CACHED_PAGES` 是否正确生效              |
| 多选时卡顿          | Coil 未用缓存        | 确保 `diskCachePolicy(CachePolicy.ENABLED)` |
| 相册列表慢          | 扫描了全部图片          | 检查 `ALBUM_SCAN_LIMIT` 是否生效                |
| BottomSheet 闪烁 | 透明 Activity 动画未关 | 确保 `overridePendingTransition(0,0)`       |

---

## 18. 变更日志

### 生产级优化（当前版本）

```
线程安全:
  - SelectionManager 全部方法 @Synchronized
  - PhotoPickerAdapter 选择方法 @Synchronized
  - DefaultMediaAdapter 状态读写同步保护

性能优化:
  - ViewBinding 替代所有 findViewById（15处）
  - setHasFixedSize(true) + setHasStableIds(true)
  - itemAnimator = null + initialPrefetchItemCount = 12
  - PAYLOAD_SELECTION 局部刷新（多选零Coil重载）
  - SpanSizeLookup by lazy 缓存
  - precomputedSquareSize 消除 view.post{}
  - onScrolled 缓存引用（零分配每帧）
  - SelectionItemDecoration early-exit
  - Toast debounce 2秒
  - overdraw 降到 2-3 层

内存管理:
  - MAX_CACHED_PAGES = 8 窗口裁剪
  - ALBUM_SCAN_LIMIT = 5000
  - rebuildAfterTrim 修复裁剪后位置错位
  - PagingController 生命周期绑定
  - onDestroyView 清空所有引用

兼容性:
  - MediaDataLoader QUERY_ARG_OFFSET/LIMIT
  - loadAlbums 标准列替代 COUNT 投影
  - @Suppress("DEPRECATION") 标注废弃 API
  - 布局 XML 字符串全部资源化

代码质量:
  - 消除全部 null!! (3处)
  - 消除未使用的参数/字段
  - 删除死代码(PickerDefaults/ListViewModel/sadf)
  - MediaDataLoader 混合源分页算法修正
  - FlowPickerDataSource 正确实现 single-emission
  - PickerLogger 从空枚举改为实际日志类
```

---

## 附录A: 所有公共类型速查

| 类型                         | 分类  | 说明                   |
|----------------------------|-----|----------------------|
| `PhotoPickerCompat`        | 入口  | 系统选择器静态工厂            |
| `pickMedia()`              | 入口  | DSL 入口函数（顶层）         |
| `registerForPhotoPicker()` | 入口  | Fragment 快速注册        |
| `registerForMediaPicker()` | 入口  | Fragment DSL 注册      |
| `PickerConfiguration<T>`   | 配置  | 不可变配置对象              |
| `PickerScope<T>`           | 配置  | DSL 作用域              |
| `TabScope`                 | 配置  | Tab DSL 作用域          |
| `ResultMapper<T>`          | 映射  | 结果类型映射 fun interface |
| `PickResult<T>`            | 结果  | 选择结果密封类              |
| `PickerDataSource`         | 数据  | 数据源接口                |
| `MediaStoreDataSource`     | 数据  | MediaStore 实现        |
| `FlowPickerDataSource`     | 数据  | Flow 实现              |
| `LoadResult<T>`            | 数据  | 加载结果密封类              |
| `SelectableAdapter<T>`     | 适配器 | Tab 适配器接口            |
| `DefaultMediaAdapter`      | 适配器 | 默认实现                 |
| `GroupedAdapter`           | 适配器 | 核心 RV 适配器            |
| `PhotoPickerAdapter`       | 适配器 | 独立备选适配器              |
| `SelectionManager`         | 选择  | 线程安全选择管理             |
| `MultiSelectHelper`        | 选择  | 多选触摸处理               |
| `SelectionItemDecoration`  | 选择  | 选中装饰                 |
| `PickerMiddleware`         | 扩展  | 中间件接口                |
| `ItemTransform`            | 扩展  | 条目变换 fun interface   |
| `PickerPlugin`             | 扩展  | 插件接口                 |
| `PickerHost`               | 扩展  | 插件宿主接口               |
| `PickerStrings`            | UI  | 字符串资源接口              |
| `PickerAnalytics`          | UI  | 埋点接口                 |
| `UiOverrides`              | UI  | UI 覆盖                |
| `LayoutAdaptor`            | UI  | 布局配置                 |
| `PickerLogger`             | 工具  | 日志器                  |
| `PagingController`         | 工具  | 分页加载器                |
| `MediaDataLoader`          | 工具  | MediaStore 加载器       |
| `DateSectionHelper`        | 工具  | 日期分组                 |
| `OverlayManager`           | 工具  | 覆盖层管理                |
| `PickerComponentProvider`  | 工具  | 组件工厂                 |
| `OverlayManager`           | 工具  | 覆盖层管理                |
| `TabAdapterRegistry`       | 工具  | Tab 适配器注册            |
