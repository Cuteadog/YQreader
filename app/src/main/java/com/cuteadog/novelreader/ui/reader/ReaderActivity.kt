package com.cuteadog.novelreader.ui.reader

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
import com.cuteadog.novelreader.MyApplication
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.data.dao.NoteDao
import com.cuteadog.novelreader.data.dao.NovelDao
import com.cuteadog.novelreader.data.model.Chapter
import com.cuteadog.novelreader.data.model.Highlight
import com.cuteadog.novelreader.data.model.Novel
import com.cuteadog.novelreader.data.repository.NovelRepository
import com.cuteadog.novelreader.databinding.ActivityReaderBinding
import com.cuteadog.novelreader.ui.system.SystemUiHelper
import com.cuteadog.novelreader.util.NovelParser
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
import com.cuteadog.novelreader.BuildConfig
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator


open class ReaderActivity : AppCompatActivity() {
    internal lateinit var binding: ActivityReaderBinding
    internal lateinit var drawerLayout: DrawerLayout
    internal lateinit var topMenu: View
    internal lateinit var bottomMenu: View
    internal var menuVisible = false
    private val hideMenuRunnable = Runnable { hideMenu() }

    protected lateinit var novel: Novel
    protected var chapters: List<Chapter> = emptyList()

    protected var currentChapterPages: List<StaticLayout> = emptyList()
    protected lateinit var novelRepository: NovelRepository
    protected lateinit var noteDao: NoteDao

    protected var currentChapterIndex: Int = 0
    protected var currentPageIndex: Int = 0
    protected var totalPages: Int = 0

    protected lateinit var novelParser: NovelParser
    protected lateinit var chapterAdapter: ChapterAdapter

    protected var textSize: Float = 32f
    protected var textSizeToastView: TextView? = null

    // Current selection state (chapter-absolute offsets)
    protected var selectionChapterAbsStart: Int = -1
    protected var selectionChapterAbsEnd: Int = -1
    protected var selectedText: String = ""
    protected var currentSelectionPopup: PopupWindow? = null
    // Selected highlight color (default yellow)
    protected var selectedHighlightColor: Int = Highlight.YELLOW
    protected var refreshHighlightsJob: kotlinx.coroutines.Job? = null

    companion object {
        const val EXTRA_NOVEL = "com.cuteadog.novelreader.EXTRA_NOVEL"
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

        // 测量菜单高度（首次显示时 height 为 0，因为 visibility=GONE）
        fun measureIfNeeded(v: View) {
            if (v.height == 0) {
                val parentWidth = (v.parent as View).width
                v.measure(
                    View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
            }
        }
        measureIfNeeded(topMenu)
        measureIfNeeded(bottomMenu)

        val topH = if (topMenu.height > 0) topMenu.height else topMenu.measuredHeight
        val bottomH = if (bottomMenu.height > 0) bottomMenu.height else bottomMenu.measuredHeight

        // 顶部菜单从上方滑入
        topMenu.translationY = -topH.toFloat()
        topMenu.visibility = View.VISIBLE
        topMenu.animate().translationY(0f).setDuration(250)
            .setInterpolator(DecelerateInterpolator()).start()

        // 底部菜单从下方滑入
        bottomMenu.translationY = bottomH.toFloat()
        bottomMenu.visibility = View.VISIBLE
        bottomMenu.animate().translationY(0f).setDuration(250)
            .setInterpolator(DecelerateInterpolator()).start()

        resetMenuTimer()
    }

    private fun hideMenu() {
        if (!menuVisible) return
        menuVisible = false
        topMenu.removeCallbacks(hideMenuRunnable)

        // 顶部菜单向上滑出
        topMenu.animate()
            .translationY(-topMenu.height.toFloat())
            .setDuration(250)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { topMenu.visibility = View.GONE; topMenu.translationY = 0f }
            .start()

        // 底部菜单向下滑出
        bottomMenu.animate()
            .translationY(bottomMenu.height.toFloat())
            .setDuration(250)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { bottomMenu.visibility = View.GONE; bottomMenu.translationY = 0f }
            .start()
    }

    private fun resetMenuTimer() {
        topMenu.removeCallbacks(hideMenuRunnable)
        topMenu.postDelayed(hideMenuRunnable, 5000)
    }

    protected fun updateMenuTitle() {
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
            // 重置菜单停留时间
            resetMenuTimer()
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
    private fun applyTheme(animate: Boolean = true) {
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
                topMenu.setBackgroundColor("#CCF3F3F3".toColorInt())
                findViewById<TextView>(R.id.tv_menu_title).setTextColor(Color.BLACK)
                bottomMenu.setBackgroundColor("#CCF3F3F3".toColorInt())
                setMenuIconsTint(Color.BLACK)
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
                findViewById<TextView>(R.id.tv_menu_title).setTextColor("#80808D".toColorInt())
                bottomMenu.setBackgroundColor("#CC445566".toColorInt())
                setMenuIconsTint("#80808D".toColorInt())
                binding.rvChapters.setBackgroundColor("#445566".toColorInt())
                if (::chapterAdapter.isInitialized) chapterAdapter.notifyDataSetChanged()
            }
        }

        binding.pageView.setTextColor(newText)
        currentChapterPages = emptyList()
        loadCurrentChapter()

        if (animate && oldBg != newBg) {
            binding.pageView.animateBackgroundColor(binding.tvPageCeiling, oldBg, newBg)
            binding.pageView.animateBackgroundColor(binding.pageView, oldBg, newBg)
            binding.pageView.animateBackgroundColor(binding.tvPageIndicator, oldBg, newBg)
        } else {
            binding.tvPageCeiling.setBackgroundColor(newBg)
            binding.pageView.setBackgroundColor(newBg)
            binding.tvPageIndicator.setBackgroundColor(newBg)
        }

        // 系统栏透明，根据页面底色切换图标深 / 浅色
        SystemUiHelper.updateStatusBarIcons(this, newBg)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 首次绘制前同步取缓存主题，避免打开阅读器时从白色过渡到目标色的闪烁
        currentTheme = com.cuteadog.novelreader.ui.theme.ThemeManager.current()
        val initialBg = when (currentTheme) {
            THEME_DAY -> "#FFFFFF".toColorInt()
            THEME_EYE -> "#FFF5E6".toColorInt()
            else -> "#202020".toColorInt()
        }
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(initialBg))

        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 阅读界面：进入沉浸模式，状态栏/导航栏隐藏，边缘上滑可短暂唤出
        SystemUiHelper.enterImmersive(this)
        SystemUiHelper.updateStatusBarIcons(this, initialBg)

        // 立即把页面可见区域刷成对应主题的底色（text 颜色等在 applyTheme 里完成）
        val initialTextColor = when (currentTheme) {
            THEME_DAY, THEME_EYE -> Color.BLACK
            else -> "#80808D".toColorInt()
        }
        binding.pageView.setBackgroundColor(initialBg)
        binding.tvPageCeiling.setBackgroundColor(initialBg)
        binding.tvPageIndicator.setBackgroundColor(initialBg)
        binding.pageView.setTextColor(initialTextColor)

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
            if (!binding.pageView.isInSelectionMode()) {
                if (menuVisible) hideMenu() else showMenu()
            }
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
        
        // 主题切换按钮 - 仅在 personal 版本中启用
        if (BuildConfig.ENABLE_THEME_SWITCH) {
            findViewById<View?>(R.id.btn_theme)?.setOnClickListener {
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
                resetMenuTimer()
            }
        } else {
            findViewById<View?>(R.id.btn_theme)?.visibility = View.GONE
        }
        
        // 笔记按钮 - 仅在 open 版本中启用
        if (BuildConfig.ENABLE_NOTES_HIGHLIGHT) {
            findViewById<View>(R.id.btn_notes).setOnClickListener {
                showNotesBottomSheet()
                resetMenuTimer()
            }
        } else {
            findViewById<View?>(R.id.btn_notes)?.visibility = View.GONE
        }

        // 文本选择功能 - 仅在 open 版本中启用
        if (BuildConfig.ENABLE_NOTES_HIGHLIGHT) {
            binding.pageView.onTextSelectedListener = { text, localStart, localEnd, anchorX, anchorY ->
                selectedText = text
                val pageStart = calculatePageStartOffset(currentPageIndex, currentChapterPages)
                selectionChapterAbsStart = pageStart + localStart
                selectionChapterAbsEnd = pageStart + localEnd
                // 选择模式下锁定抽屉，防止拖动手柄时误触
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                showTextActionPopup(anchorX, anchorY)
            }
        } else {
            // Personal flavor: only copy functionality
            binding.pageView.onTextSelectedListener = { text, localStart, localEnd, anchorX, anchorY ->
                selectedText = text
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                showTextActionPopup(anchorX, anchorY)
            }
        }

        // Dismiss popup when selection is cleared (e.g., user taps outside selection)
        binding.pageView.onSelectionClearedListener = {
            currentSelectionPopup?.dismiss()
            currentSelectionPopup = null
            // 退出选择模式，解锁抽屉
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        }

        // 拖动手柄时隐藏操作栏，松手后重新显示
        binding.pageView.onHandleDragListener = { isDragging ->
            currentSelectionPopup?.contentView?.visibility =
                if (isDragging) View.INVISIBLE else View.VISIBLE
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
                    
                    // 无条件读取已保存主题：personal 由阅读器内按钮切换，
                    // open 由主页面按钮切换，二者共享 THEME_KEY
                    // 首次打开不走动画，避免白→目标色的闪烁
                    val savedTheme = preferences[THEME_KEY] ?: THEME_DAY
                    currentTheme = savedTheme
                    applyTheme(animate = false)
                    currentChapterPages = emptyList()
                    loadChapters()
                }
            }
        })
    }

    // ────────────── Page offset helpers ──────────────

    protected fun calculatePageStartOffset(pageIndex: Int, pages: List<StaticLayout> = currentChapterPages): Int {
        var offset = 0
        for (i in 0 until pageIndex) {
            if (i < pages.size) offset += pages[i].text.length
        }
        return offset
    }

    // ────────────── Text action popup ──────────────

    protected fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("novel_text", text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    // ────────────── Highlights refresh ──────────────

    // ────────────── Notes bottom sheet ──────────────

    // Stub implementations for flavors that don't have notes/highlight features
    open fun showNotesBottomSheet() {
        // Empty implementation for personal flavor
    }

    open fun showTextActionPopup(anchorX: Float, anchorY: Float) {
        // Personal flavor: show only copy functionality
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

        // Copy functionality - available in all flavors
        popupView.findViewById<LinearLayout>(R.id.action_copy).setOnClickListener {
            copyToClipboard(selectedText)
            binding.pageView.clearSelection()
        }

        popupView.findViewById<LinearLayout>(R.id.action_cancel).setOnClickListener {
            binding.pageView.clearSelection()
        }

        popup.showAtLocation(binding.pageView, android.view.Gravity.NO_GRAVITY, screenX, screenY)
    }

    open fun refreshPageHighlights() {
        // Empty implementation for personal flavor
    }

    open fun loadNotesForSheet(tab: Int, adapter: NoteEntryAdapter, tvEmpty: TextView) {
        // Empty implementation for personal flavor
    }

    open fun showColorPickerThenHighlight() {
        // Empty implementation for personal flavor
    }

    open fun applyHighlight(color: Int) {
        // Empty implementation for personal flavor
    }

    open fun showNoteEditorDialog(
        text: String,
        absStart: Int,
        absEnd: Int,
        existingHighlight: Highlight? = null
    ) {
        // Empty implementation for personal flavor
    }

    open fun locateToOffset(highlight: Highlight) {
        // Empty implementation for personal flavor
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
                    onChaptersLoaded()
                }
            }
        }
    }

    protected open fun onChaptersLoaded() {
        // Subclasses can override to perform actions after chapters are loaded
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

    protected fun loadCurrentChapter() {
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
        if (BuildConfig.ENABLE_NOTES_HIGHLIGHT) refreshPageHighlights()  // 直接调用，不用 post
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
                    if (BuildConfig.ENABLE_NOTES_HIGHLIGHT) refreshPageHighlights()
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
                    if (BuildConfig.ENABLE_NOTES_HIGHLIGHT) refreshPageHighlights()
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
    protected fun updatePageIndicator() {
        binding.tvPageIndicator.text = "${currentPageIndex + 1}/$totalPages"
    }

    protected fun saveReadingProgress() {
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 从其它 Activity 切回时，系统栏可能被 BEHAVIOR_SHOW_TRANSIENT 临时弹出；
        // 在菜单未显示的情况下重新隐藏，保持沉浸
        if (hasFocus && !menuVisible) {
            SystemUiHelper.hideSystemBars(this)
        }
    }
}
