package com.cuteadog.novelreader.ui.library

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cuteadog.novelreader.BuildConfig
import com.cuteadog.novelreader.MyApplication
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.data.dao.NoteDao
import com.cuteadog.novelreader.data.model.Novel
import com.cuteadog.novelreader.data.repository.NovelRepository
import com.cuteadog.novelreader.databinding.FragmentLibraryBinding
import com.cuteadog.novelreader.storage.StorageLocationManager
import com.cuteadog.novelreader.ui.reader.ReaderActivity
import com.cuteadog.novelreader.ui.theme.ThemeManager
import com.cuteadog.novelreader.ui.theme.ThemePalette
import android.content.res.ColorStateList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryFragment(
    private val novelRepository: NovelRepository
) : Fragment() {

    /** 从 StorageLocationManager 读取最新目录，跟随用户切换储存位置后立刻生效。 */
    private fun currentNovelsDir(): String =
        StorageLocationManager.novelsDir(requireContext()).absolutePath

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private var isSelectionMode = false
    private lateinit var novelAdapter: NovelAdapter
    private var novels: List<Novel> = emptyList()

    private val themeListener: (Int) -> Unit = {
        if (_binding != null) applyTheme(ThemeManager.currentPalette())
    }

    // 声明 launcher，稍后在 onViewCreated 中初始化
    private lateinit var openDirectoryLauncher: ActivityResultLauncher<Intent>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化 launcher
        openDirectoryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    android.util.Log.d("LibraryFragment", "导入回调执行，URI: $uri")
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
                    com.cuteadog.novelreader.service.ImportWorker.enqueueWork(requireContext(), uri, currentNovelsDir())
                    Toast.makeText(requireContext(), "开始导入，请稍候", Toast.LENGTH_SHORT).show()
                }
            } else {
                android.util.Log.d("LibraryFragment", "用户取消选择文件夹")
            }
        }

        setupRecyclerView()
        setupButtons()
        observeNovels()

        if (BuildConfig.ENABLE_NOTES_HIGHLIGHT) {
            applyTheme(ThemeManager.currentPalette())
            ThemeManager.addListener(themeListener)
        }
    }

    private fun setupRecyclerView() {
        novelAdapter = NovelAdapter(
            novels = emptyList(),
            isSelectionMode = isSelectionMode,
            onNovelClick = { novel ->
                startActivity(
                    Intent(requireContext(), ReaderActivity::class.java).apply {
                        putExtra(ReaderActivity.EXTRA_NOVEL, novel)
                    }
                )
            },
            onNovelLongClick = { novel ->
                toggleSelectionMode()
                toggleNovelSelection(novel)
            }
        )

        binding.rvNovels.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = novelAdapter
        }
    }

    private fun setupButtons() {
        binding.btnManage.setOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                if (novels.isEmpty()) {
                    Toast.makeText(requireContext(), "没有可管理的小说", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                enterSelectionMode()
            }
        }

        binding.btnImport.setOnClickListener {
            if (isSelectionMode) {
                deleteSelectedNovels()
            } else {
                android.util.Log.d("LibraryFragment", "准备打开文件夹选择器")
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                openDirectoryLauncher.launch(intent)
            }
        }
    }

    private fun observeNovels() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                novelRepository.novels.collectLatest { novels ->
                    this@LibraryFragment.novels = novels
                    updateAdapter()
                    updateEmptyState()
                }
            }
        }
    }

    private fun updateAdapter() {
        novelAdapter = NovelAdapter(
            novels = novels,
            isSelectionMode = isSelectionMode,
            onNovelClick = { novel ->
                if (isSelectionMode) {
                    toggleNovelSelection(novel)
                } else {
                    val readerActivityClass = if (com.cuteadog.novelreader.BuildConfig.ENABLE_NOTES_HIGHLIGHT) {
                        Class.forName("com.cuteadog.novelreader.ui.reader.ReaderActivityOpen")
                    } else {
                        ReaderActivity::class.java
                    }
                    startActivity(
                        Intent(requireContext(), readerActivityClass).apply {
                            putExtra(ReaderActivity.EXTRA_NOVEL, novel)
                        }
                    )
                }
            },
            onNovelLongClick = { novel ->
                if (!isSelectionMode) {
                    enterSelectionMode()
                }
                toggleNovelSelection(novel)
            }
        )
        binding.rvNovels.adapter = novelAdapter
    }

    private fun updateEmptyState() {
        if (novels.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvNovels.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvNovels.visibility = View.VISIBLE
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        binding.btnManage.text = getString(R.string.cancel)
        binding.btnImport.text = getString(R.string.delete)
        updateAdapter()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        binding.btnManage.text = getString(R.string.manage)
        binding.btnImport.text = getString(R.string.import_novel)

        lifecycleScope.launch {
            novelRepository.clearNovelSelections()
        }

        updateAdapter()
    }

    private fun toggleSelectionMode() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            enterSelectionMode()
        }
    }

    private fun toggleNovelSelection(novel: Novel) {
        lifecycleScope.launch {
            novelRepository.updateNovelSelection(novel.id, !novel.isSelected)
        }
    }

    private fun deleteSelectedNovels() {
        lifecycleScope.launch {
            val dir = currentNovelsDir()
            android.util.Log.d("LibraryFragment", "开始删除选中小说，目录: $dir")
            val selectedNovels = novels.filter { it.isSelected }
            val selectedIds = selectedNovels.joinToString { it.id }
            android.util.Log.d("LibraryFragment", "选中的小说 ID: $selectedIds")

            // 级联删除：同时删除关联的笔记和高亮
            if (BuildConfig.ENABLE_NOTES_HIGHLIGHT) {
                val dataStore = (requireActivity().application as MyApplication).dataStore
                val noteDao = NoteDao(dataStore)
                for (novel in selectedNovels) {
                    noteDao.deleteHighlightsForNovel(novel.id)
                }
            }

            novelRepository.deleteSelectedNovels(java.io.File(currentNovelsDir()))
            exitSelectionMode()
            Toast.makeText(requireContext(), "已删除选中的小说", Toast.LENGTH_SHORT).show()
        }
    }

    fun retryImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        openDirectoryLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.ENABLE_NOTES_HIGHLIGHT && _binding != null) {
            applyTheme(ThemeManager.currentPalette())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (BuildConfig.ENABLE_NOTES_HIGHLIGHT) {
            ThemeManager.removeListener(themeListener)
        }
        _binding = null
    }

    private fun applyTheme(palette: ThemePalette) {
        val b = _binding ?: return
        b.root.setBackgroundColor(palette.pageBg)
        b.rvNovels.setBackgroundColor(palette.pageBg)
        b.emptyState.setBackgroundColor(palette.pageBg)

        b.root.findViewById<android.widget.ImageView>(R.id.iv_empty_icon)?.setColorFilter(palette.textSecondary)
        b.root.findViewById<android.widget.TextView>(R.id.tv_empty_text)?.setTextColor(palette.textSecondary)

        b.buttonBar.setBackgroundColor(palette.surfaceBg)

        val buttonBgTint = ColorStateList.valueOf(palette.buttonBg)
        b.btnManage.backgroundTintList = buttonBgTint
        b.btnManage.setTextColor(palette.buttonFg)
        b.btnImport.backgroundTintList = buttonBgTint
        b.btnImport.setTextColor(palette.buttonFg)

        novelAdapter.updatePalette(palette)
    }
}