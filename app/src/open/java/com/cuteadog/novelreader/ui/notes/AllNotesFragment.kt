package com.cuteadog.novelreader.ui.notes

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.data.dao.NoteDao
import com.cuteadog.novelreader.data.model.Highlight
import com.cuteadog.novelreader.data.model.Novel
import com.cuteadog.novelreader.data.repository.NovelRepository
import com.cuteadog.novelreader.ui.theme.ThemeManager
import com.cuteadog.novelreader.ui.theme.ThemePalette
import android.content.res.ColorStateList
import android.widget.ImageView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AllNotesFragment(
    private val noteDao: NoteDao,
    private val novelRepository: NovelRepository
) : Fragment() {

    private lateinit var rvAllNotes: RecyclerView
    private lateinit var emptyState: View
    private lateinit var btnManage: Button
    private lateinit var btnExport: Button
    private lateinit var adapter: AllNotesAdapter

    private val collapsedBooks = mutableSetOf<String>()
    private var allHighlights: List<Highlight> = emptyList()
    private var novels: List<Novel> = emptyList()
    private var chapterOrderMap: Map<String, Int> = emptyMap()

    private lateinit var createDocumentLauncher: ActivityResultLauncher<Intent>

    private var rootView: View? = null
    private val themeListener: (Int) -> Unit = {
        applyTheme(ThemeManager.currentPalette())
    }

    /** 主题过渡动画每帧调用：原地刷新可见颜色，避免 notifyDataSetChanged 的闪烁。 */
    private val paletteTickListener: (ThemePalette) -> Unit = { palette ->
        if (rootView != null) applyLivePalette(palette)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_all_notes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView = view

        rvAllNotes = view.findViewById(R.id.rv_all_notes)
        emptyState = view.findViewById(R.id.empty_state_notes)
        btnManage = view.findViewById(R.id.btn_notes_manage)
        btnExport = view.findViewById(R.id.btn_notes_export)

        // SAF launcher for export
        createDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    lifecycleScope.launch {
                        writeExportFile(uri)
                    }
                }
            }
        }

        adapter = AllNotesAdapter(
            onNoteClick = { highlight, novelTitle ->
                val intent = Intent(requireContext(), NoteDetailActivity::class.java).apply {
                    putExtra(NoteDetailActivity.EXTRA_HIGHLIGHT_JSON, serializeHighlight(highlight))
                    putExtra(NoteDetailActivity.EXTRA_NOVEL_TITLE, novelTitle)
                }
                startActivity(intent)
            },
            onToggleBook = { novelId ->
                if (collapsedBooks.contains(novelId)) {
                    collapsedBooks.remove(novelId)
                } else {
                    collapsedBooks.add(novelId)
                }
                rebuildList()
            },
            onSelectionChanged = {
                // Update button text to show selection count
                updateDeleteButtonText()
            }
        )

        rvAllNotes.layoutManager = LinearLayoutManager(requireContext())
        rvAllNotes.adapter = adapter

        btnManage.setOnClickListener {
            if (adapter.isSelectionMode) {
                exitSelectionMode()
            } else {
                if (allHighlights.isEmpty()) {
                    Toast.makeText(requireContext(), "没有可管理的笔记", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                enterSelectionMode()
            }
        }

        btnExport.setOnClickListener {
            if (adapter.isSelectionMode) {
                deleteSelected()
            } else {
                startExport()
            }
        }

        applyTheme(ThemeManager.currentPalette())
        ThemeManager.addListener(themeListener)
        ThemeManager.addPaletteTickListener(paletteTickListener)
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
        applyTheme(ThemeManager.currentPalette())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ThemeManager.removeListener(themeListener)
        ThemeManager.removePaletteTickListener(paletteTickListener)
        rootView = null
    }

    private fun applyTheme(palette: ThemePalette) {
        val v = rootView ?: return
        v.setBackgroundColor(palette.pageBg)
        rvAllNotes.setBackgroundColor(palette.pageBg)
        emptyState.setBackgroundColor(palette.pageBg)

        v.findViewById<ImageView>(R.id.iv_empty_notes_icon)?.setColorFilter(palette.textSecondary)
        v.findViewById<android.widget.TextView>(R.id.tv_empty_notes_text)?.setTextColor(palette.textSecondary)

        v.findViewById<View>(R.id.button_bar_notes)?.setBackgroundColor(palette.surfaceBg)

        val btnBgTint = ColorStateList.valueOf(palette.buttonBg)
        btnManage.backgroundTintList = btnBgTint
        btnManage.setTextColor(palette.buttonFg)
        btnExport.backgroundTintList = btnBgTint
        btnExport.setTextColor(palette.buttonFg)

        adapter.updatePalette(palette)
    }

    /**
     * 主题过渡动画每帧的原地刷新：只改颜色、不重建 item view，避免列表闪烁。
     * 注意：RV 子 item 的气泡由 AllNotesAdapter.applyLivePalette 负责。
     */
    private fun applyLivePalette(palette: ThemePalette) {
        val v = rootView ?: return
        v.setBackgroundColor(palette.pageBg)
        rvAllNotes.setBackgroundColor(palette.pageBg)
        emptyState.setBackgroundColor(palette.pageBg)

        v.findViewById<ImageView>(R.id.iv_empty_notes_icon)?.setColorFilter(palette.textSecondary)
        v.findViewById<android.widget.TextView>(R.id.tv_empty_notes_text)?.setTextColor(palette.textSecondary)

        v.findViewById<View>(R.id.button_bar_notes)?.setBackgroundColor(palette.surfaceBg)

        val btnBgTint = ColorStateList.valueOf(palette.buttonBg)
        btnManage.backgroundTintList = btnBgTint
        btnManage.setTextColor(palette.buttonFg)
        btnExport.backgroundTintList = btnBgTint
        btnExport.setTextColor(palette.buttonFg)

        adapter.applyLivePalette(rvAllNotes, palette)
    }

    fun loadNotes() {
        lifecycleScope.launch {
            novels = novelRepository.novels.first()
            val novelIds = novels.map { it.id }.toSet()

            val orderMap = mutableMapOf<String, Int>()
            for (novel in novels) {
                val chapters = novelRepository.getChapters(novel.id).first()
                chapters.forEachIndexed { index, chapter ->
                    orderMap[chapter.id] = index
                }
            }
            chapterOrderMap = orderMap

            allHighlights = noteDao.getAllHighlights()
                .filter { it.novelId in novelIds }

            // Default: collapse all on first load
            if (collapsedBooks.isEmpty() && allHighlights.isNotEmpty()) {
                val bookIds = allHighlights.map { it.novelId }.distinct()
                collapsedBooks.addAll(bookIds)
            }

            rebuildList()
        }
    }

    // ─────────── Selection / Manage Mode ───────────

    private fun enterSelectionMode() {
        adapter.enterSelectionMode()
        btnManage.text = getString(R.string.cancel)
        btnExport.text = getString(R.string.delete)
        rebuildList()
    }

    private fun exitSelectionMode() {
        adapter.exitSelectionMode()
        btnManage.text = getString(R.string.manage)
        btnExport.text = getString(R.string.export_notes)
        rebuildList()
    }

    private fun updateDeleteButtonText() {
        val count = adapter.selectedIds.size
        btnExport.text = if (count > 0) {
            "${getString(R.string.delete)}($count)"
        } else {
            getString(R.string.delete)
        }
    }

    private fun deleteSelected() {
        val ids = adapter.selectedIds.toSet()
        if (ids.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择要删除的笔记", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            noteDao.deleteHighlightsByIds(ids)
            exitSelectionMode()
            loadNotes()
            Toast.makeText(requireContext(), "已删除 ${ids.size} 条笔记", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────── Export ───────────

    private fun startExport() {
        if (allHighlights.isEmpty()) {
            Toast.makeText(requireContext(), "暂无笔记可导出", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "笔记导出_$timestamp.txt"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        createDocumentLauncher.launch(intent)
    }

    private suspend fun writeExportFile(uri: android.net.Uri) {
        try {
            val content = buildExportContent()
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(requireContext(), "导出成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildExportContent(): String {
        val sb = StringBuilder()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val noteDateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // Export header
        sb.appendLine("[导出时间]${dateFmt.format(Date())}")
        sb.appendLine()

        val novelTitleMap = novels.associate { it.id to it.title }
        val byNovel = allHighlights.groupBy { it.novelId }

        for ((novelId, highlights) in byNovel) {
            val novelTitle = novelTitleMap[novelId] ?: "未知书籍"
            sb.appendLine("【$novelTitle】")
            sb.appendLine()

            val byChapter = highlights.groupBy { it.chapterId }
                .entries
                .sortedBy { entry -> chapterOrderMap[entry.key] ?: Int.MAX_VALUE }

            for ((_, chapterHighlights) in byChapter) {
                val chapterTitle = chapterHighlights.first().chapterTitle.ifBlank { "未知章节" }
                sb.appendLine(chapterTitle)

                val sorted = chapterHighlights.sortedBy { it.startOffset }
                for (h in sorted) {
                    sb.appendLine("----------------------------------")
                    sb.appendLine("[原文]")
                    sb.appendLine(h.selectedText.trim())
                    if (h.isNote) {
                        sb.appendLine("[笔记]")
                        sb.appendLine(h.noteContent.trim())
                    }
                    sb.appendLine("[时间]")
                    sb.appendLine(noteDateFmt.format(Date(h.timestamp)))
                }
                sb.appendLine("----------------------------------")
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    // ─────────── List Building ───────────

    private fun rebuildList() {
        if (allHighlights.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            rvAllNotes.visibility = View.GONE
            adapter.updateItems(emptyList())
            return
        }

        emptyState.visibility = View.GONE
        rvAllNotes.visibility = View.VISIBLE

        val novelTitleMap = novels.associate { it.id to it.title }
        val items = mutableListOf<AllNotesItem>()

        val byNovel = allHighlights.groupBy { it.novelId }

        for ((novelId, highlights) in byNovel) {
            val novelTitle = novelTitleMap[novelId] ?: "未知书籍"
            val isCollapsed = collapsedBooks.contains(novelId)

            items.add(AllNotesItem.BookHeader(novelId, novelTitle, highlights.size, isCollapsed))

            if (!isCollapsed) {
                val byChapter = highlights.groupBy { it.chapterId }
                    .entries
                    .sortedBy { entry -> chapterOrderMap[entry.key] ?: Int.MAX_VALUE }

                for ((chapterId, chapterHighlights) in byChapter) {
                    val chapterTitle = chapterHighlights.first().chapterTitle
                    items.add(AllNotesItem.ChapterDivider(
                        chapterId = chapterId,
                        novelId = novelId,
                        chapterTitle = if (chapterTitle.isBlank()) "未知章节" else chapterTitle
                    ))

                    val sorted = chapterHighlights.sortedBy { it.startOffset }
                    for (h in sorted) {
                        items.add(AllNotesItem.NoteEntry(h, novelTitle))
                    }
                }
            }
        }

        adapter.updateAllHighlights(allHighlights)
        adapter.updateItems(items)
    }

    private fun serializeHighlight(highlight: Highlight): String {
        return kotlinx.serialization.json.Json.encodeToString(
            Highlight.serializer(), highlight
        )
    }
}
