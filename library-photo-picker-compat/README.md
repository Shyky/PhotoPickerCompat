# PhotoPickerCompat — Library 模块

> 一行代码集成图片/视频选择器，兼容 Android 6.0（API 23）及以上所有版本

## 快速开始

### 添加依赖

在 `settings.gradle.kts` 添加 JitPack 仓库：

```kotlin
maven { url = uri("https://jitpack.io") }
```

在 `build.gradle.kts` 添加依赖：

```kotlin
dependencies {
    // 固定版本（推荐）
    implementation("com.github.Shyky.PhotoPickerCompat:library-photo-picker-compat:1.0.0")

    // 或跟踪 main 分支最新提交
    implementation("com.github.Shyky.PhotoPickerCompat:library-photo-picker-compat:main-SNAPSHOT")
}
```

### 最简用法 — 系统选择器（3 行代码）

```kotlin
class MainActivity : AppCompatActivity() {
    // 单张图片
    private val pickImage = registerForActivityResult(PhotoPickerCompat.image()) { uri: Uri? ->
        uri?.let { imageView.setImageURI(it) }
    }

    // 多张图片（最多 9 张）
    private val pickImages = registerForActivityResult(
        PhotoPickerCompat.multipleImages(maxCount = 9)
    ) { uris: List<Uri> -> adapter.submitList(uris) }

    // 图片+视频混合（API 35+ 支持选择默认标签页）
    private val pickMedia = registerForActivityResult(
        PhotoPickerCompat.multipleMedia(maxCount = 9, defaultTab = PickDefaultTab.ALBUMS)
    ) { uris: List<Uri> -> handle(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.btnPick.setOnClickListener { pickImages.launch(Unit) }
    }
}
```

### 内置网格选择器（统一体验）

```kotlin
class MyFragment : Fragment() {
    // 方式 1：一行注册
    private val pickGrid = registerForPhotoPicker(maxCount = 9) { uris ->
        viewModel.handleSelectedUris(uris)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnSelect.setOnClickListener { pickGrid() }
    }
}
```

## API 速查

| 方法 | 输入 | 输出 | API 33+ 行为 | API 23-32 降级 |
|------|------|------|-------------|---------------|
| `PhotoPickerCompat.image()` | `Unit` | `Uri?` | 系统 PhotoPicker（仅图片） | `GetContent("image/*")` |
| `PhotoPickerCompat.multipleImages(n)` | `Unit` | `List<Uri>` | 系统多选（限制 n 张） | `GetMultipleContents("image/*")` |
| `PhotoPickerCompat.video()` | `Unit` | `Uri?` | 系统 PhotoPicker（仅视频） | `GetContent("video/*")` |
| `PhotoPickerCompat.multipleVideos(n)` | `Unit` | `List<Uri>` | 系统多选（限制 n 个） | `GetMultipleContents("video/*")` |
| `PhotoPickerCompat.imageAndVideo(tab)` | `Unit` | `Uri?` | 系统混合模式 | `ACTION_GET_CONTENT` + MIME 数组 |
| `PhotoPickerCompat.multipleMedia(n, tab)` | `Unit` | `List<Uri>` | 系统混合多选 | `ACTION_GET_CONTENT` + `EXTRA_ALLOW_MULTIPLE` |
| `PhotoPickerCompat.gridPicker(n)` | `Unit` | `List<Uri>` | 内置 BottomSheet 网格 | 内置 BottomSheet 网格 |
| `registerForPhotoPicker(n) { uris -> }` | `()` | — | Fragment 快速注册 | Fragment 快速注册 |

## 权限要求

| 选择方式 | API 版本 | 所需权限 |
|---------|---------|---------|
| 系统选择器（PhotoPickerCompat.*） | 23+ | **无需权限** |
| 系统降级（GetContent 路径） | 23+ | **无需权限** |
| 内置网格（GridPickerContract / registerForPhotoPicker） | 33+ | `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` |
| 内置网格（GridPickerContract / registerForPhotoPicker） | 23-32 | `READ_EXTERNAL_STORAGE` |

## 兼容性

| 功能 | API 23-25 | API 26-32 | API 33-34 | API 35+ |
|------|-----------|-----------|-----------|---------|
| 系统 PhotoPicker | ✗ | ✗ | ✅ | ✅ |
| 系统降级（GetContent） | ✅ | ✅ | ✗ | ✗ |
| 内置网格选择器 | ✅ | ✅ | ✅ | ✅ |
| 分页查询（QUERY_ARG） | ✗※ | ✅ | ✅ | ✅ |
| 默认相册标签页 | ✗ | ✗ | ✗ | ✅ |

> ※ API 23-25 降级为经典 `query()` + `sortOrder` + 手动截取，功能等价。

## 模块结构

```
com.shyky.tech.photo.picker.compat/
├── PhotoPickerCompat.kt          # 系统选择器入口（6 个静态工厂方法）
├── picker.kt                     # DSL 顶层入口 + 快捷函数
├── FragmentExt.kt                # Fragment 扩展（一行注册）
├── contract/                     # ActivityResultContract 实现
│   ├── PhotoPickerContract.kt    # 6 个系统选择器 Contract
│   ├── MediaPickerContract.kt    # 自定义 DSL 选择器 Contract
│   └── GridPickerContract.kt     # 简化版网格选择器 Contract
├── config/                       # 配置系统（DSL 构建器）
├── data/                         # 数据层（MediaStore + 分页）
├── ui/                           # UI 容器（透明 Activity + BottomSheet）
├── adapter/                      # RecyclerView 适配器
├── selection/                    # 选择管理（多选 + 手势）
├── pipeline/                     # 扩展点（中间件 + 插件 + 变换）
├── result/                       # 结果映射
└── util/                         # 工具（日期分组 + 覆盖层）
```

## 性能

| 指标 | 典型值 |
|------|--------|
| 首屏加载 | 200-400ms |
| 翻页加载 | 100-200ms |
| 滚动帧率 | 60fps |
| 内存峰值 | ~15MB |

## 扩展

```kotlin
// 中间件：在选中前校验
middleware {
    +object : PickerMiddleware {
        override suspend fun onBeforeSelect(uri: Uri, pos: Int): Boolean {
            return getFileSize(uri) <= 50 * 1024 * 1024  // 限制 50MB
        }
    }
}

// 条目过滤：过滤 GIF
transforms { +GifFilter() }

// 插件：自定义装饰
plugins { +WatermarkPlugin() }
```

## License

Apache 2.0

---

> 完整架构、线程安全模型、性能调优实战见 [项目根目录 README](../README.md)
