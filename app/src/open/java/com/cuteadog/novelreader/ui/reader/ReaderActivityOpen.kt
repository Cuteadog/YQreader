package com.cuteadog.novelreader.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.data.model.Highlight
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Open flavor specific ReaderActivity subclass
 * Contains note and highlight functionality
 */
class ReaderActivityOpen : ReaderActivity() {

    private var pendingJumpChapterId: String? = null
    private var pendingJumpStartOffset: Int = -1

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        // 检查是否有跳转参数（从笔记详情页过来）
        pendingJumpChapterId = intent.getStringExtra("jump_chapter_id")
        pendingJumpStartOffset = intent.getIntExtra("jump_start_offset", -1)

        super.onCreate(savedInstanceState)
        lifecycleScope.launch { noteDao.migrateIfNeeded() }
    }

    override fun onChaptersLoaded() {
        super.onChaptersLoaded()
        val jumpChapterId = pendingJumpChapterId
        if (jumpChapterId != null && pendingJumpStartOffset >= 0) {
            pendingJumpChapterId = null
            val jumpOffset = pendingJumpStartOffset
            pendingJumpStartOffset = -1

            val targetHighlight = com.cuteadog.novelreader.data.model.Highlight(
                id = "",
                chapterId = jumpChapterId,
                novelId = novel.id,
                selectedText = "",
                startOffset = jumpOffset,
                endOffset = jumpOffset + 1,
                color = 0
            )
            binding.pageView.post { locateToOffset(targetHighlight) }
        }
    }

    @SuppressLint("InflateParams")
    override fun showNotesBottomSheet() {
        val bottomSheet = BottomSheetDialog(this@ReaderActivityOpen)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_notes, null)
        bottomSheet.setContentView(sheetView)

        val palette = com.cuteadog.novelreader.ui.theme.ThemeManager.currentPalette()
        val tabLayout = sheetView.findViewById<TabLayout>(R.id.tab_notes_filter)
        val rvNotes = sheetView.findViewById<RecyclerView>(R.id.rv_notes)
        val tvEmpty = sheetView.findViewById<TextView>(R.id.tv_notes_empty)

        // 运行时主题：根、标题栏、标题文字、Tab、列表区、空态
        sheetView.findViewById<android.view.View>(R.id.notes_sheet_root)?.setBackgroundColor(palette.pageBg)
        sheetView.findViewById<android.view.View>(R.id.notes_sheet_header)?.setBackgroundColor(palette.groupBg)
        sheetView.findViewById<TextView>(R.id.tv_notes_sheet_title)?.setTextColor(palette.textPrimary)
        rvNotes.setBackgroundColor(palette.pageBg)
        tvEmpty.setTextColor(palette.textSecondary)
        tabLayout.setTabTextColors(palette.textSecondary, palette.textPrimary)
        tabLayout.setSelectedTabIndicatorColor(palette.textPrimary)
        // BottomSheet 容器背景
        (sheetView.parent as? android.view.View)?.setBackgroundColor(palette.pageBg)

        tabLayout.addTab(tabLayout.newTab().setText("全部"))
        tabLayout.addTab(tabLayout.newTab().setText("高亮"))
        tabLayout.addTab(tabLayout.newTab().setText("笔记"))

        // 把每个 TabView 的真实背景替换成半径受限的 ripple。
        // TabLayout 的 app:tabBackground 只会作为静态 drawable 绘制，
        // 真正响应触摸的 ripple 来自 tabRippleColor 所构造的 RippleDrawable。
        // 这里把 tabRippleColor 设为 null（XML 中声明），并手动挂一个 18dp 半径的 ripple 作为 TabView 背景。
        (tabLayout.getChildAt(0) as? android.view.ViewGroup)?.let { strip ->
            for (i in 0 until strip.childCount) {
                strip.getChildAt(i).background = androidx.core.content.ContextCompat.getDrawable(
                    this, R.drawable.bg_action_ripple_compact
                )
            }
        }

        lateinit var adapter: NoteEntryAdapter
        adapter = NoteEntryAdapter(
            entries = emptyList(),
            onDelete = { highlight ->
                lifecycleScope.launch {
                    noteDao.deleteHighlight(highlight.id)
                    refreshPageHighlights()
                    loadNotesForSheet(tabLayout.selectedTabPosition, adapter, tvEmpty)
                }
            },
            onJumpTo = onJumpTo@{ highlight ->
                bottomSheet.dismiss()
                val chapterIndex = chapters.indexOfFirst { it.id == highlight.chapterId }
                if (chapterIndex < 0) return@onJumpTo

                if (chapterIndex != currentChapterIndex) {
                    currentChapterIndex = chapterIndex
                    currentPageIndex = 0
                    currentChapterPages = emptyList()
                    loadCurrentChapter()
                    chapterAdapter.updateCurrentIndex(chapterIndex)
                    binding.pageView.post { locateToOffset(highlight) }
                } else {
                    locateToOffset(highlight)
                }
            }
        )
        rvNotes.adapter = adapter
        rvNotes.layoutManager = LinearLayoutManager(this)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                loadNotesForSheet(tab.position, adapter, tvEmpty)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        loadNotesForSheet(0, adapter, tvEmpty)
        bottomSheet.show()
    }

    override fun loadNotesForSheet(tab: Int, adapter: NoteEntryAdapter, tvEmpty: TextView) {
        lifecycleScope.launch {
            val all = noteDao.getAllHighlightsForNovel(novel.id)
            val entries: List<Highlight> = when (tab) {
                1 -> all.filter { !it.isNote }
                2 -> all.filter { it.isNote }
                else -> all.sortedByDescending { it.timestamp }
            }
            adapter.updateEntries(entries)
            val isEmpty = entries.isEmpty()
            val rvNotes = (tvEmpty.parent as? android.view.ViewGroup)?.findViewById<RecyclerView>(R.id.rv_notes)
            rvNotes?.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
            tvEmpty.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    @SuppressLint("InflateParams")
    override fun showTextActionPopup(anchorX: Float, anchorY: Float) {
        currentSelectionPopup?.dismiss()
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_text_actions, null)
        applyPopupTheme(popupView, com.cuteadog.novelreader.ui.theme.ThemeManager.currentPalette())

        val popup = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            false
        )
        popup.isOutsideTouchable = false
        currentSelectionPopup = popup

        // Measure popup to position it above anchor
        popupView.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
        val popupH = popupView.measuredHeight.coerceAtLeast(64)
        val popupW = popupView.measuredWidth.coerceAtLeast(256)

        // 使用 getLocationInWindow 替代 getLocationOnScreen，避免真机上状态栏/刘海偏移
        val location = IntArray(2)
        binding.pageView.getLocationInWindow(location)

        val screenX = (location[0] + anchorX - popupW / 2).toInt()
            .coerceAtLeast(0)
        val screenY = (location[1] + anchorY - popupH - 16).toInt()
            .coerceAtLeast(0)

        // Check if selection overlaps with any highlight (with or without note)
        lifecycleScope.launch {
            val overlapping = getOverlappingHighlights(selectionChapterAbsStart, selectionChapterAbsEnd)
            val overlappingNote = overlapping.find { it.isNote }

            // Update note action text based on whether editing or creating
            val noteTextView = popupView.findViewById<TextView?>(R.id.tv_note_action)
            if (overlappingNote != null) {
                noteTextView?.text = "编辑"
            }

            // 删除功能 - 仅在选中区域有高亮时显示
            val deleteAction = popupView.findViewById<LinearLayout?>(R.id.action_delete)
            if (overlapping.isNotEmpty()) {
                deleteAction?.visibility = android.view.View.VISIBLE
                deleteAction?.setOnClickListener {
                    lifecycleScope.launch {
                        for (h in overlapping) {
                            noteDao.deleteHighlight(h.id)
                        }
                        refreshPageHighlights()
                    }
                    binding.pageView.clearSelection()
                    Toast.makeText(this@ReaderActivityOpen, "已删除高亮", Toast.LENGTH_SHORT).show()
                }
            }

            // 高亮功能
            val highlightTextView = popupView.findViewById<TextView?>(R.id.tv_highlight_action)
            if (overlapping.isNotEmpty()) {
                highlightTextView?.text = "改色"
            }
            popupView.findViewById<LinearLayout?>(R.id.action_highlight)?.setOnClickListener {
                binding.pageView.clearSelection()
                if (overlapping.isNotEmpty()) {
                    showColorPickerForExisting(overlapping)
                } else {
                    showColorPickerThenHighlight()
                }
            }

            // 笔记功能
            popupView.findViewById<LinearLayout?>(R.id.action_note)?.setOnClickListener {
                binding.pageView.clearSelection()
                if (overlappingNote != null) {
                    showNoteEditorDialog(selectedText, selectionChapterAbsStart, selectionChapterAbsEnd, overlappingNote)
                } else {
                    // 如果选中区域有高亮但无笔记，在已有高亮上添加笔记
                    val baseHighlight = overlapping.firstOrNull()
                    showNoteEditorDialog(selectedText, selectionChapterAbsStart, selectionChapterAbsEnd, baseHighlight)
                }
            }

            // 复制功能
            popupView.findViewById<LinearLayout>(R.id.action_copy).setOnClickListener {
                copyToClipboard(selectedText)
                binding.pageView.clearSelection()
            }

            popupView.findViewById<LinearLayout>(R.id.action_cancel).setOnClickListener {
                binding.pageView.clearSelection()
            }

            // 有删除按钮时需要重新测量弹窗宽度
            if (overlapping.isNotEmpty()) {
                popupView.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
                val newW = popupView.measuredWidth.coerceAtLeast(256)
                val newScreenX = (location[0] + anchorX - newW / 2).toInt().coerceAtLeast(0)
                popup.showAtLocation(binding.pageView, android.view.Gravity.NO_GRAVITY, newScreenX, screenY)
            } else {
                popup.showAtLocation(binding.pageView, android.view.Gravity.NO_GRAVITY, screenX, screenY)
            }
        }
    }

    private suspend fun getOverlappingHighlights(startOffset: Int, endOffset: Int): List<Highlight> {
        val chapterId = chapters.getOrNull(currentChapterIndex)?.id ?: return emptyList()
        val highlights = noteDao.getHighlightsForChapter(chapterId)
        return highlights.filter { it.startOffset < endOffset && it.endOffset > startOffset }
    }

    override fun showColorPickerThenHighlight() {
        showThemedColorPicker("选择高亮颜色") { chosen ->
            selectedHighlightColor = chosen
            applyHighlight(chosen)
        }
    }

    private fun showColorPickerForExisting(highlights: List<Highlight>) {
        showThemedColorPicker("修改高亮颜色") { newColor ->
            lifecycleScope.launch {
                for (h in highlights) {
                    noteDao.saveHighlight(h.copy(color = newColor))
                }
                refreshPageHighlights()
            }
        }
    }

    private fun showThemedColorPicker(title: String, onPick: (Int) -> Unit) {
        val palette = com.cuteadog.novelreader.ui.theme.ThemeManager.currentPalette()
        val colors = Highlight.ALL_COLORS
        val names = Highlight.COLOR_NAMES

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }

        val selectableBgRes = android.util.TypedValue().also {
            theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        val rows = mutableListOf<Pair<android.view.View, Int>>()
        for (i in colors.indices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(24, 20, 24, 20)
                isClickable = true
                isFocusable = true
                if (selectableBgRes != 0) setBackgroundResource(selectableBgRes)
            }
            val swatch = android.view.View(this).apply {
                val size = (24 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (16 * resources.displayMetrics.density).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(colors[i])
                    setStroke(2, palette.dividerColor)
                }
            }
            val label = TextView(this).apply {
                text = names[i]
                setTextColor(palette.textPrimary)
                textSize = 16f
            }
            row.addView(swatch)
            row.addView(label)
            container.addView(row)
            rows.add(row to colors[i])
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("取消", null)
            .create()

        for ((row, color) in rows) {
            row.setOnClickListener {
                onPick(color)
                dialog.dismiss()
            }
        }

        dialog.show()
        dialog.applyDialogChromeTheme(palette)
    }

    override fun applyHighlight(color: Int) {
        if (chapters.isEmpty() || currentChapterIndex >= chapters.size) return
        val chapter = chapters[currentChapterIndex]
        val highlight = Highlight(
            id = UUID.randomUUID().toString(),
            chapterId = chapter.id,
            novelId = novel.id,
            selectedText = selectedText,
            startOffset = selectionChapterAbsStart,
            endOffset = selectionChapterAbsEnd,
            color = color,
            chapterTitle = chapter.title
        )
        lifecycleScope.launch {
            // Remove overlapping highlights before saving new one
            val overlapping = getOverlappingHighlights(selectionChapterAbsStart, selectionChapterAbsEnd)
            for (h in overlapping) {
                noteDao.deleteHighlight(h.id)
            }
            noteDao.saveHighlight(highlight)
            refreshPageHighlights()
        }
        binding.pageView.clearSelection()
    }

    @SuppressLint("InflateParams")
    override fun showNoteEditorDialog(
        text: String,
        absStart: Int,
        absEnd: Int,
        existingHighlight: Highlight?
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_note_editor, null)
        val tvSelected = dialogView.findViewById<TextView>(R.id.tv_selected_text)
        val etNote = dialogView.findViewById<android.widget.EditText>(R.id.et_note_content)

        tvSelected.text = text
        if (existingHighlight != null) etNote.setText(existingHighlight.noteContent)

        val palette = com.cuteadog.novelreader.ui.theme.ThemeManager.currentPalette()
        dialogView.findViewById<android.view.View>(R.id.dialog_root)?.setBackgroundColor(palette.pageBg)
        dialogView.findViewById<android.view.View>(R.id.note_editor_divider)?.setBackgroundColor(palette.dividerColor)
        etNote.setTextColor(palette.textPrimary)
        etNote.setHintTextColor(palette.textSecondary)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (existingHighlight?.isNote != true) "添加笔记" else "编辑笔记")
            .setView(dialogView)
            .setPositiveButton("保存") { dialog, _ ->
                val content = etNote.text.toString()
                val chapter = if (chapters.isNotEmpty() && currentChapterIndex < chapters.size) {
                    chapters[currentChapterIndex]
                } else {
                    dialog.dismiss()
                    return@setPositiveButton
                }
                val highlight = Highlight(
                    id = existingHighlight?.id ?: UUID.randomUUID().toString(),
                    chapterId = chapter.id,
                    novelId = novel.id,
                    selectedText = text,
                    startOffset = absStart,
                    endOffset = absEnd,
                    color = existingHighlight?.color ?: selectedHighlightColor,
                    noteContent = content,
                    chapterTitle = chapter.title
                )
                lifecycleScope.launch {
                    noteDao.saveHighlight(highlight)
                    refreshPageHighlights()
                }
                binding.pageView.clearSelection()
                Toast.makeText(this, "笔记已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
        dialog.applyDialogChromeTheme(palette)
        etNote.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        etNote.postDelayed({ imm.showSoftInput(etNote, InputMethodManager.SHOW_IMPLICIT) }, 100)
    }

    override fun refreshPageHighlights() {
        refreshHighlightsJob?.cancel()
        refreshHighlightsJob = lifecycleScope.launch {
            if (chapters.isEmpty() || currentChapterIndex >= chapters.size) return@launch
            val chapter = chapters[currentChapterIndex]
            val pages = currentChapterPages
            val pageIdx = currentPageIndex
            if (pageIdx !in pages.indices) return@launch

            val pageStart = calculatePageStartOffset(pageIdx, pages)
            val pageLayout = pages[pageIdx]
            val pageEnd = pageStart + pageLayout.text.length

            val highlights = noteDao.getHighlightsForChapter(chapter.id)
            val pageHighlights = highlights.mapNotNull { h ->
                if (h.startOffset < pageEnd && h.endOffset > pageStart) {
                    val start = (h.startOffset - pageStart).coerceAtLeast(0)
                    val end = (h.endOffset - pageStart).coerceAtMost(pageLayout.text.length)
                    if (start < end) PageView.PageHighlight(start, end, h.color) else null
                } else null
            }

            if (currentPageIndex == pageIdx && currentChapterPages === pages) {
                binding.pageView.setPageHighlights(pageHighlights)
            }
        }
    }

    override fun locateToOffset(highlight: Highlight) {
        val chapterIdx = chapters.indexOfFirst { it.id == highlight.chapterId }
        if (chapterIdx != currentChapterIndex) {
            currentChapterIndex = chapterIdx
            currentPageIndex = 0
            currentChapterPages = emptyList()
            loadCurrentChapter()
            binding.pageView.post { locateToOffset(highlight) }
            return
        }

        val pages = currentChapterPages
        var targetPage = -1
        var accum = 0
        for (i in pages.indices) {
            val pageLen = pages[i].text.length
            if (highlight.startOffset in accum until accum + pageLen) {
                targetPage = i
                break
            }
            accum += pageLen
        }
        if (targetPage == -1) return

        if (targetPage != currentPageIndex) {
            currentPageIndex = targetPage
            val newCurrent = currentChapterPages[currentPageIndex]
            val newPrev = if (currentPageIndex > 0) currentChapterPages[currentPageIndex - 1] else null
            val newNext = if (currentPageIndex + 1 < currentChapterPages.size) currentChapterPages[currentPageIndex + 1] else null
            binding.pageView.applyPageTransition(newCurrent, newPrev, newNext)
            updatePageIndicator()
            updateMenuTitle()
            saveReadingProgress()
            refreshPageHighlights()
        }
    }
}
