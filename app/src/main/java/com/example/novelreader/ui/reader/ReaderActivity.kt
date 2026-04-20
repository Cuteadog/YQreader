package com.example.novelreader.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.novelreader.MyApplication
import com.example.novelreader.R
import com.example.novelreader.data.dao.NoteDao
import com.example.novelreader.data.dao.NovelDao
import com.example.novelreader.data.model.Chapter
import com.example.novelreader.data.model.Highlight
import com.example.novelreader.data.model.Note
import com.example.novelreader.data.model.Novel
import com.example.novelreader.data.repository.NovelRepository
import com.example.novelreader.databinding.ActivityReaderBinding
import com.example.novelreader.util.NovelParser
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import android.text.StaticLayout
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.novelreader.ui.reader.NoteEntryAdapter
import com.example.novelreader.ui.reader.NoteEntry


class ReaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReaderBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var topMenu: View
    private lateinit var bottomMenu: View
    private var menuVisible = false
    private val hideMenuRunnable = Runnable { hideMenu() }

    private lateinit var novel: Novel
    private var chapters: List<Chapter> = emptyList()

    private var currentChapterPages: List<StaticLayout> = emptyList()
    private lateinit var novelRepository: NovelRepository
    private lateinit var noteDao: NoteDao

    private var currentChapterIndex: Int = 0
    private var currentPageIndex: Int = 0
    private var totalPages: Int = 0

    private lateinit var novelParser: NovelParser
    private lateinit var chapterAdapter: ChapterAdapter

    private var textSize: Float = 32f
    private var textSizeToastView: TextView? = null

    // Current selection state (chapter-absolute offsets)
    private var selectionChapterAbsStart: Int = -1
    private var selectionChapterAbsEnd: Int = -1
    private var selectedText: String = ""
    private var currentSelectionPopup: PopupWindow? = null
    // Selected highlight color (default yellow)
    private var selectedHighlightColor: Int = Highlight.YELLOW
    private var refreshHighlightsJob: kotlinx.coroutines.Job? = null

    companion object {
        const val EXTRA_NOVEL = "com.example.novelreader.EXTRA_NOVEL"
        private val FONT_SIZE_KEY = floatPreferencesKey("font_size")
        private val THEME_KEY = intPreferencesKey("theme")

        const val THEME_DAY = 0
        const val THEME_EYE = 1
        const val THEME_NIGHT = 2

        var currentTheme = THEME_DAY
    }

    private fun showMenu() {
        if (menuVisible) return
        menuVisible = true
        topMenu.visibility = View.VISIBLE
        bottomMenu.visibility = View.VISIBLE
        topMenu.removeCallbacks(hideMenuRunnable)
        topMenu.postDelayed(hideMenuRunnable, 5000)
    }

    private fun hideMenu() {
        if (!menuVisible) return
        menuVisible = false
        topMenu.visibility = View.GONE
        bottomMenu.visibility = View.GONE
        topMenu.removeCallbacks(hideMenuRunnable)
    }

    private fun updateMenuTitle() {
        val title = if (currentChapterIndex < chapters.size) {
            "${novel.title} - ${chapters[currentChapterIndex].title}"
        } else {
            novel.title
        }
        findViewById<TextView>(R.id.tv_menu_title).text = title
    }

    private fun adjustFontSize(delta: Float) {
        val newSize = textSize + delta
        if (newSize in 16f..48f) {
            textSize = newSize
            binding.pageView.setTextSize(textSize)
            currentChapterPages = emptyList()
            loadCurrentChapter()
            lifecycleScope.launch {
                val dataStore = (application as MyApplication).dataStore
                dataStore.edit { preferences ->
                    preferences[FONT_SIZE_KEY] = textSize
                }
            }
            // 清除缩放后可能过期的选择状态
            binding.pageView.clearSelection()
            // 显示字体大小提示
            showFontSizeToast(textSize)
        }
    }

    private fun showFontSizeToast(size: Float) {
        // 移除已有的提示框
        textSizeToastView?.let { (it.parent as? ViewGroup)?.removeView(it) }

        val toastView = TextView(this).apply {
            text = String.format("%.0f", size)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 16f
            setPadding(32, 16, 32, 16)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(220, 0, 0, 0))
                cornerRadius = 24f
            }
            elevation = 10f
            alpha = 0.8f
        }
        textSizeToastView = toastView

        // 获取主内容容器（DrawerLayout 的第一个子 View，即 FrameLayout）
        val contentContainer = (binding.root as? ViewGroup)?.getChildAt(0) as? FrameLayout
        if (contentContainer == null) {
            // 降级方案
            Toast.makeText(this, "字体大小: ${size.toInt()}", Toast.LENGTH_SHORT).show()
            return
        }

        // 测量宽高
        toastView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val w = toastView.measuredWidth
        val h = toastView.measuredHeight
        if (w == 0 || h == 0) return

        // 计算视图位置
        bottomMenu.post {
            val bottomLocation = IntArray(2)
            bottomMenu.getLocationOnScreen(bottomLocation)
            val containerLocation = IntArray(2)
            contentContainer.getLocationOnScreen(containerLocation)

            val bottomMenuTop = bottomLocation[1]
            val relativeY = bottomMenuTop - containerLocation[1] - h - 16
            val relativeX = (contentContainer.width - w) / 2

            toastView.layoutParams = FrameLayout.LayoutParams(w, h).apply {
                leftMargin = relativeX
                topMargin = relativeY
            }
            contentContainer.addView(toastView)
            textSizeToastView = toastView
        }

        // 2秒后移除
        contentContainer.postDelayed({
            (toastView.parent as? ViewGroup)?.removeView(toastView)
            if (textSizeToastView == toastView) textSizeToastView = null
        }, 2000)
    }

    private fun setMenuIconsTint(color: Int) {
        val tintColor = Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))
        findViewById<ImageButton>(R.id.btn_back).setColorFilter(tintColor)
        findViewById<ImageButton>(R.id.btn_chapter).setColorFilter(tintColor)
        findViewById<ImageButton>(R.id.btn_font_decrease).setColorFilter(tintColor)
        findViewById<ImageButton>(R.id.btn_font_increase).setColorFilter(tintColor)
        findViewById<ImageButton>(R.id.btn_theme).setColorFilter(tintColor)
        findViewById<ImageButton>(R.id.btn_notes).setColorFilter(tintColor)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun applyTheme() {
        val oldBg = binding.pageView.getBackgroundColor()
        val newBg = when (currentTheme) {
            THEME_DAY -> "#FFFFFF".toColorInt()
            THEME_EYE -> "#FFF5E6".toColorInt()
            else -> "#202020".toColorInt()
        }
        val newText = when (currentTheme) {
            THEME_DAY -> Color.BLACK
            THEME_EYE -> Color.BLACK
            else -> "#80808D".toColorInt()
        }

        when (currentTheme) {
            THEME_DAY -> {
                topMenu.setBackgroundColor("#CC2196F3".toColorInt())
                findViewById<TextView>(R.id.tv_menu_title).setTextColor(Color.WHITE)
                bottomMenu.setBackgroundColor("#CC2196F3".toColorInt())
                setMenuIconsTint(Color.WHITE)
                binding.rvChapters.setBackgroundColor("#FFFFFF".toColorInt())
                if (::chapterAdapter.isInitialized) chapterAdapter.notifyDataSetChanged()
            }
            THEME_EYE -> {
                topMenu.setBackgroundColor("#CCFFDEAD".toColorInt())
                findViewById<TextView>(R.id.tv_menu_title).setTextColor(Color.BLACK)
                bottomMenu.setBackgroundColor("#CCFFDEAD".toColorInt())
                setMenuIconsTint(Color.BLACK)
                binding.rvChapters.setBackgroundColor("#FFF5E6".toColorInt())
                if (::chapterAdapter.isInitialized) chapterAdapter.notifyDataSetChanged()
            }
            THEME_NIGHT -> {
                topMenu.setBackgroundColor("#CC445566".toColorInt())
                findViewById<TextView>(R.id.tv_menu_title).setTextColor("#2196F3".toColorInt())
                bottomMenu.setBackgroundColor("#CC445566".toColorInt())
                setMenuIconsTint("#2196F3".toColorInt())
                binding.rvChapters.setBackgroundColor("#445566".toColorInt())
                if (::chapterAdapter.isInitialized) chapterAdapter.notifyDataSetChanged()
            }
        }

        binding.pageView.setTextColor(newText)
        currentChapterPages = emptyList()
        loadCurrentChapter()

        binding.pageView.animateBackgroundColor(binding.tvPageCeiling, oldBg, newBg)
        binding.pageView.animateBackgroundColor(binding.pageView, oldBg, newBg)
        binding.pageView.animateBackgroundColor(binding.tvPageIndicator, oldBg, newBg)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        novel = intent.getSerializableExtra(EXTRA_NOVEL) as Novel

        val dataStore = (application as MyApplication).dataStore
        val novelDao = NovelDao(dataStore)
        novelRepository = NovelRepository(novelDao)
        noteDao = NoteDao(dataStore)

        currentChapterIndex = novel.currentChapterIndex
        currentPageIndex = novel.currentPageIndex

        novelParser = NovelParser(this)

        binding.pageView.setTextSize(textSize)
        binding.pageView.onPageChangeListener = { direction ->
            handlePageChange(direction)
        }

        drawerLayout = binding.drawerLayout
        binding.pageView.onPageClickListener = {
            if (menuVisible) hideMenu() else showMenu()
        }

        topMenu = findViewById(R.id.top_menu)
        bottomMenu = findViewById(R.id.bottom_menu)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_chapter).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
            hideMenu()
        }
        findViewById<View>(R.id.btn_font_decrease).setOnClickListener { adjustFontSize(-2f) }
        findViewById<View>(R.id.btn_font_increase).setOnClickListener { adjustFontSize(2f) }
        findViewById<View>(R.id.btn_theme).setOnClickListener {
            currentTheme = when (currentTheme) {
                THEME_DAY -> THEME_EYE
                THEME_EYE -> THEME_NIGHT
                else -> THEME_DAY
            }
            lifecycleScope.launch {
                val ds = (application as MyApplication).dataStore
                ds.edit { preferences -> preferences[THEME_KEY] = currentTheme }
            }
            applyTheme()
        }
        findViewById<View>(R.id.btn_notes).setOnClickListener {
            showNotesBottomSheet()
        }

        // Text selection callback from PageView
        binding.pageView.onTextSelectedListener = { text, localStart, localEnd, anchorX, anchorY ->
            selectedText = text
            val pageStart = calculatePageStartOffset(currentPageIndex, currentChapterPages)
            selectionChapterAbsStart = pageStart + localStart
            selectionChapterAbsEnd = pageStart + localEnd
            showTextActionPopup(anchorX, anchorY)
        }

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: View) { hideMenu() }
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        binding.pageView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.pageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                lifecycleScope.launch {
                    val preferences = dataStore.data.first()
                    val savedSize = preferences[FONT_SIZE_KEY] ?: textSize
                    textSize = savedSize
                    binding.pageView.setTextSize(textSize)
                    val savedTheme = preferences[THEME_KEY] ?: THEME_DAY
                    currentTheme = savedTheme
                    applyTheme()
                    currentChapterPages = emptyList()
                    loadChapters()
                }
            }
        })
    }

    // ────────────── Page offset helpers ──────────────

    private fun calculatePageStartOffset(pageIndex: Int, pages: List<StaticLayout> = currentChapterPages): Int {
        var offset = 0
        for (i in 0 until pageIndex) {
            if (i < pages.size) offset += pages[i].text.length
        }
        return offset
    }

    // ────────────── Text action popup ──────────────

    @SuppressLint("InflateParams")
    private fun showTextActionPopup(anchorX: Float, anchorY: Float) {
        currentSelectionPopup?.dismiss()
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_text_actions, null)

        val popup = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.isOutsideTouchable = true
        popup.setOnDismissListener { binding.pageView.clearSelection() }
        currentSelectionPopup = popup

        // Measure popup to position it above anchor
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupH = popupView.measuredHeight.coerceAtLeast(64)
        val popupW = popupView.measuredWidth.coerceAtLeast(256)

        val location = IntArray(2)
        binding.pageView.getLocationOnScreen(location)

        val screenX = (location[0] + anchorX - popupW / 2).toInt()
            .coerceAtLeast(0)
        val screenY = (location[1] + anchorY - popupH - 16).toInt()
            .coerceAtLeast(0)

        popupView.findViewById<LinearLayout>(R.id.action_highlight).setOnClickListener {
            popup.dismiss()
            showColorPickerThenHighlight()
        }

        popupView.findViewById<LinearLayout>(R.id.action_note).setOnClickListener {
            popup.dismiss()
            showNoteEditorDialog(selectedText, selectionChapterAbsStart, selectionChapterAbsEnd)
        }

        popupView.findViewById<LinearLayout>(R.id.action_copy).setOnClickListener {
            popup.dismiss()
            copyToClipboard(selectedText)
        }

        popupView.findViewById<LinearLayout>(R.id.action_cancel).setOnClickListener {
            popup.dismiss()
        }

        popup.showAtLocation(binding.pageView, Gravity.NO_GRAVITY, screenX, screenY)
    }

    private fun showColorPickerThenHighlight() {
        val colors = Highlight.ALL_COLORS
        val names = Highlight.COLOR_NAMES
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择高亮颜色")
            .setItems(names.toTypedArray()) { _, which ->
                selectedHighlightColor = colors[which]
                applyHighlight(selectedHighlightColor)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyHighlight(color: Int) {
        if (chapters.isEmpty() || currentChapterIndex >= chapters.size) return
        val chapter = chapters[currentChapterIndex]
        val highlight = Highlight(
            id = UUID.randomUUID().toString(),
            chapterId = chapter.id,
            novelId = novel.id,
            selectedText = selectedText,
            startOffset = selectionChapterAbsStart,
            endOffset = selectionChapterAbsEnd,
            color = color
        )
        lifecycleScope.launch {
            noteDao.saveHighlight(highlight)
            refreshPageHighlights()
        }
        binding.pageView.clearSelection()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("novel_text", text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    // ────────────── Note editor dialog ──────────────

    @SuppressLint("InflateParams")
    private fun showNoteEditorDialog(
        text: String,
        absStart: Int,
        absEnd: Int,
        existingNote: Note? = null
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_note_editor, null)
        val tvSelected = dialogView.findViewById<TextView>(R.id.tv_selected_text)
        val etNote = dialogView.findViewById<android.widget.EditText>(R.id.et_note_content)

        tvSelected.text = text
        if (existingNote != null) etNote.setText(existingNote.noteContent)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (existingNote == null) "添加笔记" else "编辑笔记")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val content = etNote.text.toString()
                val chapter = if (chapters.isNotEmpty() && currentChapterIndex < chapters.size)
                    chapters[currentChapterIndex] else return@setPositiveButton
                val note = Note(
                    id = existingNote?.id ?: UUID.randomUUID().toString(),
                    chapterId = chapter.id,
                    novelId = novel.id,
                    chapterTitle = chapter.title,
                    selectedText = text,
                    noteContent = content,
                    startOffset = absStart,
                    endOffset = absEnd,
                    highlightColor = selectedHighlightColor
                )
                lifecycleScope.launch {
                    noteDao.saveNote(note)
                    // Also save a highlight for this note
                    val highlight = Highlight(
                        id = note.id + "_hl",
                        chapterId = chapter.id,
                        novelId = novel.id,
                        selectedText = text,
                        startOffset = absStart,
                        endOffset = absEnd,
                        color = selectedHighlightColor
                    )
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
        etNote.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        etNote.postDelayed({ imm.showSoftInput(etNote, InputMethodManager.SHOW_IMPLICIT) }, 100)
    }

    // ────────────── Highlights refresh ──────────────

    private fun refreshPageHighlights() {
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

    private fun locateToOffset(entry: NoteEntry) {
        val (chapterId, absOffset) = when (entry) {
            is NoteEntry.HighlightEntry -> entry.highlight.chapterId to entry.highlight.startOffset
            is NoteEntry.NoteData -> entry.note.chapterId to entry.note.startOffset
        }
        val chapterIdx = chapters.indexOfFirst { it.id == chapterId }
        if (chapterIdx != currentChapterIndex) {
            // 章节不匹配，重新加载章节
            currentChapterIndex = chapterIdx
            currentPageIndex = 0
            currentChapterPages = emptyList()
            loadCurrentChapter()
            binding.pageView.post { locateToOffset(entry) }
            return
        }

        val pages = currentChapterPages
        var targetPage = -1
        var accum = 0
        for (i in pages.indices) {
            val pageLen = pages[i].text.length
            if (absOffset in accum until accum + pageLen) {
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

    // ────────────── Notes bottom sheet ──────────────

    @SuppressLint("InflateParams")
    private fun showNotesBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_notes, null)
        bottomSheet.setContentView(sheetView)

        val tabLayout = sheetView.findViewById<TabLayout>(R.id.tab_notes_filter)
        val rvNotes = sheetView.findViewById<RecyclerView>(R.id.rv_notes)
        val tvEmpty = sheetView.findViewById<TextView>(R.id.tv_notes_empty)

        tabLayout.addTab(tabLayout.newTab().setText("全部"))
        tabLayout.addTab(tabLayout.newTab().setText("高亮"))
        tabLayout.addTab(tabLayout.newTab().setText("笔记"))

        lateinit var adapter: NoteEntryAdapter   // 先声明
        adapter = NoteEntryAdapter(
            entries = emptyList(),
            onDelete = { entry ->
                lifecycleScope.launch {
                    when (entry) {
                        is NoteEntry.HighlightEntry -> noteDao.deleteHighlight(entry.highlight.id)
                        is NoteEntry.NoteData -> {
                            noteDao.deleteNote(entry.note.id)
                            noteDao.deleteHighlight(entry.note.id + "_hl")
                        }
                    }
                    refreshPageHighlights()
                    loadNotesForSheet(tabLayout.selectedTabPosition, adapter, tvEmpty)
                }
            },
            onJumpTo = onJumpTo@{ entry ->
                bottomSheet.dismiss()
                val chapterId = when (entry) {
                    is NoteEntry.HighlightEntry -> entry.highlight.chapterId
                    is NoteEntry.NoteData -> entry.note.chapterId
                }
                val chapterIndex = chapters.indexOfFirst { it.id == chapterId }
                if (chapterIndex < 0) return@onJumpTo

                if (chapterIndex != currentChapterIndex) {
                    // 跨章节跳转
                    currentChapterIndex = chapterIndex
                    currentPageIndex = 0
                    currentChapterPages = emptyList()
                    loadCurrentChapter()
                    chapterAdapter.updateCurrentIndex(chapterIndex)
                    // 等待分页完成后定位
                    binding.pageView.post { locateToOffset(entry) }
                } else {
                    // 同一章节内直接定位
                    locateToOffset(entry)
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

    private fun loadNotesForSheet(tab: Int, adapter: NoteEntryAdapter, tvEmpty: TextView) {
        lifecycleScope.launch {
            val highlights = noteDao.getAllHighlightsForNovel(novel.id)
            val notes = noteDao.getAllNotesForNovel(novel.id)
            val entries: List<NoteEntry> = when (tab) {
                1 -> highlights.map { NoteEntry.HighlightEntry(it) }
                2 -> notes.map { NoteEntry.NoteData(it) }
                else -> {
                    val noteHighlightIds = notes.map { it.id + "_hl" }.toSet()
                    val standaloneHighlights = highlights.filter { it.id !in noteHighlightIds }
                    val result = mutableListOf<NoteEntry>()
                    result.addAll(notes.map { NoteEntry.NoteData(it) })
                    result.addAll(standaloneHighlights.map { NoteEntry.HighlightEntry(it) })
                    result.sortedByDescending {
                        when (it) {
                            is NoteEntry.HighlightEntry -> it.highlight.timestamp
                            is NoteEntry.NoteData -> it.note.timestamp
                        }
                    }
                }
            }
            adapter.updateEntries(entries)
            tvEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ────────────── Chapter loading ──────────────

    private fun loadChapters() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                novelRepository.getChapters(novel.id).collectLatest { chapterList ->
                    if (chapterList.isEmpty()) {
                        Toast.makeText(this@ReaderActivity, "没有章节内容", Toast.LENGTH_SHORT).show()
                        return@collectLatest
                    }
                    chapters = chapterList
                    setupChapterList()
                    loadCurrentChapter()
                }
            }
        }
    }

    private fun setupChapterList() {
        chapterAdapter = ChapterAdapter(
            chapters = chapters,
            currentChapterIndex = currentChapterIndex,
            onChapterClick = { _, position ->
                currentChapterIndex = position
                currentPageIndex = 0
                currentChapterPages = emptyList()
                loadCurrentChapter()
                drawerLayout.closeDrawer(GravityCompat.START)
                if (::chapterAdapter.isInitialized) chapterAdapter.updateCurrentIndex(position)
            }
        )
        binding.rvChapters.adapter = chapterAdapter
    }

    private fun loadCurrentChapter() {
        if (currentChapterIndex >= chapters.size) return

        val currentChapter = chapters[currentChapterIndex]
        val chapterFile = File(currentChapter.filePath)

        if (!chapterFile.exists()) {
            Toast.makeText(this, "章节文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val pageWidth = binding.pageView.width
        val pageHeight = binding.pageView.height
        val availableHeight = pageHeight - PageView.PADDING_TOP.toInt() * 2
        if (pageWidth <= 0 || availableHeight <= 0) {
            binding.pageView.post { loadCurrentChapter() }
            return
        }

        if (currentChapterPages.isEmpty() || currentChapter != chapters[currentChapterIndex]) {
            val chapterContent = try {
                chapterFile.readText()
            } catch (e: Exception) {
                Toast.makeText(this, "读取章节失败: ${e.message}", Toast.LENGTH_SHORT).show()
                return
            }

            val typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            val textColor = binding.pageView.getTextColor()
            currentChapterPages = novelParser.getPageLayouts(
                chapterContent, pageWidth, pageHeight, textSize, typeface, textColor
            )
            refreshHighlightsJob?.cancel()
            binding.pageView.setPageHighlights(emptyList()) // 清空旧高亮
        }
        totalPages = currentChapterPages.size
        currentPageIndex = currentPageIndex.coerceIn(0, totalPages - 1)

        val currentPageContent = currentChapterPages[currentPageIndex]
        val prevPageContent = if (currentPageIndex > 0) currentChapterPages[currentPageIndex - 1] else null
        val nextPageContent = if (currentPageIndex < totalPages - 1) currentChapterPages[currentPageIndex + 1] else null

        // 在设置页面内容前清除选择
        binding.pageView.clearSelection()

        binding.pageView.setPageContent(currentPageContent, nextPageContent, prevPageContent)
        updatePageIndicator()
        updateMenuTitle()

        // 先清除旧高亮，再立即刷新新高亮
        binding.pageView.setPageHighlights(emptyList())
        refreshPageHighlights()  // 直接调用，不用 post
    }

    private fun handlePageChange(direction: Int) {
        when (direction) {
            PageView.PAGE_PREVIOUS -> {
                if (currentPageIndex > 0) {
                    currentPageIndex--
                    val newCurrent = currentChapterPages[currentPageIndex]
                    val newPrev = if (currentPageIndex > 0) currentChapterPages[currentPageIndex - 1] else null
                    val newNext = if (currentPageIndex + 1 < totalPages) currentChapterPages[currentPageIndex + 1] else null
                    binding.pageView.applyPageTransition(newCurrent, newPrev, newNext)
                    updatePageIndicator()
                    updateMenuTitle()
                    saveReadingProgress()
                    refreshPageHighlights()
                    binding.pageView.post { binding.pageView.invalidate() }
                } else if (currentChapterIndex > 0) {
                    currentChapterIndex--
                    currentPageIndex = Int.MAX_VALUE
                    currentChapterPages = emptyList()
                    loadCurrentChapter()
                    if (::chapterAdapter.isInitialized) {
                        chapterAdapter.updateCurrentIndex(currentChapterIndex)
                        binding.rvChapters.smoothScrollToPosition(currentChapterIndex)
                    }
                }
            }

            PageView.PAGE_NEXT -> {
                if (currentPageIndex < totalPages - 1) {
                    currentPageIndex++
                    val newCurrent = currentChapterPages[currentPageIndex]
                    val newPrev = if (currentPageIndex > 0) currentChapterPages[currentPageIndex - 1] else null
                    val newNext = if (currentPageIndex + 1 < totalPages) currentChapterPages[currentPageIndex + 1] else null
                    binding.pageView.applyPageTransition(newCurrent, newPrev, newNext)
                    updatePageIndicator()
                    updateMenuTitle()
                    saveReadingProgress()
                    refreshPageHighlights()
                    binding.pageView.post { binding.pageView.invalidate() }
                } else if (currentChapterIndex < chapters.size - 1) {
                    currentChapterIndex++
                    currentPageIndex = 0
                    currentChapterPages = emptyList()
                    loadCurrentChapter()
                    if (::chapterAdapter.isInitialized) {
                        chapterAdapter.updateCurrentIndex(currentChapterIndex)
                        binding.rvChapters.smoothScrollToPosition(currentChapterIndex)
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updatePageIndicator() {
        binding.tvPageIndicator.text = "${currentPageIndex + 1}/$totalPages"
    }

    private fun saveReadingProgress() {
        lifecycleScope.launch {
            novelRepository.updateNovelReadProgress(novel.id, currentChapterIndex, currentPageIndex)
        }
    }

    // ────────────── Summary ──────────────

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            saveReadingProgress()
            super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loadCurrentChapter()
    }
}
