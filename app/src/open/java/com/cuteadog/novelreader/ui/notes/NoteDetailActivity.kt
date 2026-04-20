package com.cuteadog.novelreader.ui.notes

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cuteadog.novelreader.MyApplication
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.data.dao.NovelDao
import com.cuteadog.novelreader.data.model.Highlight
import com.cuteadog.novelreader.data.model.Novel
import com.cuteadog.novelreader.data.repository.NovelRepository
import com.cuteadog.novelreader.ui.reader.ReaderActivity
import com.cuteadog.novelreader.ui.system.SystemUiHelper
import com.cuteadog.novelreader.ui.theme.ThemeManager
import com.cuteadog.novelreader.ui.theme.ThemePalette
import android.content.res.ColorStateList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HIGHLIGHT_JSON = "highlight_json"
        const val EXTRA_NOVEL_TITLE = "novel_title"
    }

    private lateinit var highlight: Highlight
    private var novelTitle: String = ""
    private var shareManager: ShareManager? = null

    private val themeListener: (Int) -> Unit = {
        applyTheme(ThemeManager.currentPalette())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_detail)

        SystemUiHelper.applyEdgeToEdge(
            activity = this,
            topInsetView = findViewById(R.id.title_bar),
            bottomInsetView = findViewById(R.id.scroll_view),
            referenceBgColor = ThemeManager.currentPalette().titleBarBg
        )

        val highlightJson = intent.getStringExtra(EXTRA_HIGHLIGHT_JSON)
        novelTitle = intent.getStringExtra(EXTRA_NOVEL_TITLE) ?: ""

        if (highlightJson == null) {
            finish()
            return
        }

        highlight = try {
            Json.decodeFromString(Highlight.serializer(), highlightJson)
        } catch (e: Exception) {
            finish()
            return
        }

        setupViews()
        applyTheme(ThemeManager.currentPalette())
        ThemeManager.addListener(themeListener)
    }

    override fun onResume() {
        super.onResume()
        applyTheme(ThemeManager.currentPalette())
    }

    override fun onDestroy() {
        ThemeManager.removeListener(themeListener)
        super.onDestroy()
    }

    @Deprecated("startActivityForResult 仍是简单可靠的分享图片导入入口")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        shareManager?.handleActivityResult(requestCode, resultCode, data)
    }

    private fun applyTheme(palette: ThemePalette) {
        findViewById<View>(R.id.root_layout)?.setBackgroundColor(palette.pageBg)
        findViewById<View>(R.id.title_bar)?.setBackgroundColor(palette.titleBarBg)
        findViewById<TextView>(R.id.tv_title)?.setTextColor(palette.titleBarFg)
        findViewById<ImageButton>(R.id.btn_back)?.setColorFilter(palette.titleBarFg)
        findViewById<ImageButton>(R.id.btn_share)?.setColorFilter(palette.titleBarFg)

        findViewById<View>(R.id.content_container)?.setBackgroundColor(palette.pageBg)
        findViewById<TextView>(R.id.tv_book_chapter)?.setTextColor(palette.textSecondary)
        findViewById<TextView>(R.id.tv_original_label)?.setTextColor(palette.textSecondary)
        findViewById<TextView>(R.id.tv_original_text)?.setTextColor(palette.textPrimary)
        findViewById<TextView>(R.id.tv_note_label)?.setTextColor(palette.textSecondary)
        findViewById<TextView>(R.id.tv_note_text)?.setTextColor(palette.textPrimary)
        findViewById<TextView>(R.id.tv_datetime)?.setTextColor(palette.textSecondary)

        findViewById<View>(R.id.divider_1)?.setBackgroundColor(palette.dividerColor)
        findViewById<View>(R.id.divider_2)?.setBackgroundColor(palette.dividerColor)
        findViewById<View>(R.id.divider_3)?.setBackgroundColor(palette.dividerColor)

        findViewById<Button>(R.id.btn_jump_to_source)?.let { btn ->
            btn.backgroundTintList = ColorStateList.valueOf(palette.buttonBg)
            btn.setTextColor(palette.buttonFg)
        }

        SystemUiHelper.updateStatusBarIcons(this, palette.titleBarBg)
    }

    private fun setupViews() {
        // Back button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // Share button
        findViewById<ImageButton>(R.id.btn_share).setOnClickListener {
            val manager = shareManager ?: ShareManager(this, highlight, novelTitle).also {
                shareManager = it
            }
            manager.showShareSheet()
        }

        // Book name and chapter
        val chapterInfo = if (highlight.chapterTitle.isNotBlank()) {
            "$novelTitle · ${highlight.chapterTitle}"
        } else {
            novelTitle
        }
        findViewById<TextView>(R.id.tv_book_chapter).text = chapterInfo

        // Original text with color indicator
        findViewById<View>(R.id.view_color_indicator).setBackgroundColor(highlight.color)
        findViewById<TextView>(R.id.tv_original_text).text = highlight.selectedText

        // Note section (hide if no note)
        val sectionNote = findViewById<View>(R.id.section_note)
        if (highlight.isNote) {
            sectionNote.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_note_text).text = highlight.noteContent
        } else {
            sectionNote.visibility = View.GONE
        }

        // Date and time
        val sdf = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(highlight.timestamp))
        findViewById<TextView>(R.id.tv_datetime).text = dateStr

        // Jump to source button
        findViewById<Button>(R.id.btn_jump_to_source).setOnClickListener {
            jumpToSource()
        }
    }

    private fun jumpToSource() {
        lifecycleScope.launch {
            val dataStore = (application as MyApplication).dataStore
            val novelDao = NovelDao(dataStore)
            val novelRepository = NovelRepository(novelDao)
            val novels = novelRepository.novels.first()
            val novel = novels.find { it.id == highlight.novelId }

            if (novel == null) {
                Toast.makeText(this@NoteDetailActivity, "该书已被删除", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val readerActivityClass = try {
                Class.forName("com.cuteadog.novelreader.ui.reader.ReaderActivityOpen")
            } catch (e: ClassNotFoundException) {
                com.cuteadog.novelreader.ui.reader.ReaderActivity::class.java
            }

            val intent = Intent(this@NoteDetailActivity, readerActivityClass).apply {
                putExtra(ReaderActivity.EXTRA_NOVEL, novel)
                putExtra("jump_chapter_id", highlight.chapterId)
                putExtra("jump_start_offset", highlight.startOffset)
            }
            // 不调用 finish()，使返回时回到笔记详情页而不是书架
            startActivity(intent)
        }
    }
}
