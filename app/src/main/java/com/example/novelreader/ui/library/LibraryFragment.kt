package com.example.novelreader.ui.library

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
import com.example.novelreader.R
import com.example.novelreader.data.model.Novel
import com.example.novelreader.data.repository.NovelRepository
import com.example.novelreader.databinding.FragmentLibraryBinding
import com.example.novelreader.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryFragment(
    private val novelRepository: NovelRepository,
    private val novelsDir: String
) : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private var isSelectionMode = false
    private lateinit var novelAdapter: NovelAdapter
    private var novels: List<Novel> = emptyList()

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
                    com.example.novelreader.service.ImportWorker.enqueueWork(requireContext(), uri, novelsDir)
                    Toast.makeText(requireContext(), "开始导入，请稍候", Toast.LENGTH_SHORT).show()
                }
            } else {
                android.util.Log.d("LibraryFragment", "用户取消选择文件夹")
            }
        }

        setupRecyclerView()
        setupButtons()
        observeNovels()
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
                    startActivity(
                        Intent(requireContext(), ReaderActivity::class.java).apply {
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
            android.util.Log.d("LibraryFragment", "开始删除选中小说，目录: $novelsDir")
            val selectedIds = novels.filter { it.isSelected }.joinToString { it.id }
            android.util.Log.d("LibraryFragment", "选中的小说 ID: $selectedIds")

            novelRepository.deleteSelectedNovels(java.io.File(novelsDir))
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}