package com.shyky.tech.app.photo.picker.compat

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.shyky.tech.app.photo.picker.compat.databinding.FragmentFirstBinding
import com.shyky.tech.photo.picker.compat.PhotoPickerCompat
import com.shyky.tech.photo.picker.compat.contract.PickDefaultTab

/**
 * ★ PhotoPickerCompat 完整示例 Fragment
 *
 * 演示库的所有使用方式 — 全部 launcher 在 [onCreate] 中初始化。
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val selectedUris = mutableListOf<Uri>()
    private val resultAdapter = ThumbnailAdapter()

    // ═══════════════════ Launcher（在 onCreate 中初始化）═══════════════════
    private lateinit var pickSingleImage: ActivityResultLauncher<Unit>
    private lateinit var pickMultipleImages: ActivityResultLauncher<Unit>
    private lateinit var pickSingleVideo: ActivityResultLauncher<Unit>
    private lateinit var pickMultipleVideos: ActivityResultLauncher<Unit>
    private lateinit var pickSingleMedia: ActivityResultLauncher<Unit>
    private lateinit var pickMultipleMedia: ActivityResultLauncher<Unit>
    private lateinit var pickGrid: ActivityResultLauncher<Unit>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pickSingleImage = registerForActivityResult(PhotoPickerCompat.image()) { uri ->
            if (uri != null) showResult(listOf(uri), "单张图片")
        }

        pickMultipleImages =
            registerForActivityResult(PhotoPickerCompat.multipleImages(maxCount = 9)) { uris ->
                showResult(uris, "${uris.size} 张图片")
            }

        pickSingleVideo = registerForActivityResult(PhotoPickerCompat.video()) { uri ->
            if (uri != null) showResult(listOf(uri), "单个视频")
        }

        pickMultipleVideos =
            registerForActivityResult(PhotoPickerCompat.multipleVideos(maxCount = 5)) { uris ->
                showResult(uris, "${uris.size} 个视频")
            }

        pickSingleMedia = registerForActivityResult(
            PhotoPickerCompat.media(defaultTab = PickDefaultTab.ALBUMS)
        ) { uri ->
            if (uri != null) showResult(listOf(uri), "单张媒体")
        }

        pickMultipleMedia = registerForActivityResult(
            PhotoPickerCompat.multipleMedia(maxCount = 9, defaultTab = PickDefaultTab.ALBUMS)
        ) { uris ->
            showResult(uris, "${uris.size} 个媒体文件")
        }

        pickGrid = registerForActivityResult(PhotoPickerCompat.custom(maxCount = 9)) { uris ->
            showResult(uris, "网格选择: ${uris.size} 张")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvResult.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = resultAdapter
        }

        with(binding) {
            btnSingleImage.setOnClickListener { pickSingleImage.launch(Unit) }
            btnMultipleImages.setOnClickListener { pickMultipleImages.launch(Unit) }
            btnSingleVideo.setOnClickListener { pickSingleVideo.launch(Unit) }
            btnMultipleVideos.setOnClickListener { pickMultipleVideos.launch(Unit) }
            btnSingleMedia.setOnClickListener { pickSingleMedia.launch(Unit) }
            btnMultipleMedia.setOnClickListener { pickMultipleMedia.launch(Unit) }
            btnGridPicker.setOnClickListener { pickGrid.launch(Unit) }
            btnQuickRegister.setOnClickListener {
                Toast.makeText(
                    requireContext(),
                    "此模式需在 Fragment 成员变量上调用 registerForPhotoPicker，见源码注释",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showResult(uris: List<Uri>, label: String) {
        selectedUris.clear()
        selectedUris.addAll(uris)
        resultAdapter.submitList(uris)

        binding.tvResultCount.text = when {
            uris.isEmpty() -> "未选择（取消或空结果）"
            else -> "已选 $label: ${uris.size} 个"
        }

        if (uris.isNotEmpty()) {
            Toast.makeText(requireContext(), "已选择 ${uris.size} 个文件", Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class ThumbnailAdapter : RecyclerView.Adapter<ThumbnailAdapter.VH>() {
        private val data = mutableListOf<Uri>()

        fun submitList(uris: List<Uri>) {
            data.clear()
            data.addAll(uris)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val iv = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    parent.width / 3
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(2, 2, 2, 2)
            }
            return VH(iv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.imageView.load(data[position]) {
                size(200)
                crossfade(false)
            }
        }

        override fun getItemCount() = data.size

        inner class VH(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
    }
}
