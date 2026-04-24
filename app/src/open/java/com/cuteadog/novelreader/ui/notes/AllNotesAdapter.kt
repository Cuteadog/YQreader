package com.cuteadog.novelreader.ui.notes

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.data.model.Highlight
import com.cuteadog.novelreader.ui.theme.ThemeManager
import com.cuteadog.novelreader.ui.theme.ThemePalette

sealed class AllNotesItem {
    data class BookHeader(
        val novelId: String,
        val novelTitle: String,
        val noteCount: Int,
        val isCollapsed: Boolean
    ) : AllNotesItem()

    data class ChapterDivider(
        val chapterId: String,
        val novelId: String,
        val chapterTitle: String
    ) : AllNotesItem()

    data class NoteEntry(
        val highlight: Highlight,
        val novelTitle: String
    ) : AllNotesItem()
}

class AllNotesAdapter(
    private val onNoteClick: (Highlight, String) -> Unit,
    private val onToggleBook: (String) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_BOOK_HEADER = 0
        private const val TYPE_CHAPTER_DIVIDER = 1
        private const val TYPE_NOTE_ENTRY = 2
    }

    private var items: List<AllNotesItem> = emptyList()
    private var allHighlights: List<Highlight> = emptyList()
    var isSelectionMode = false
        private set
    val selectedIds = mutableSetOf<String>() // highlight IDs

    private var palette: ThemePalette = ThemeManager.currentPalette()

    fun updatePalette(newPalette: ThemePalette) {
        palette = newPalette
        notifyDataSetChanged()
    }

    /**
     * 主题过渡动画期间：原地更新可见 item 的文字色，避免 notifyDataSetChanged 的闪烁。
     * 不可见 item 会在滑入时通过 bind()/applyBookBubble() 取最新 palette。
     */
    fun applyLivePalette(rv: RecyclerView, newPalette: ThemePalette) {
        palette = newPalette
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)

            // 刷新自定义 Drawable（线框颜色实时从 palette 读取）
            child.invalidate()

            // BookHeader
            child.findViewById<View>(R.id.header_divider)
                ?.setBackgroundColor(newPalette.dividerColor)
            child.findViewById<TextView>(R.id.tv_book_title)
                ?.setTextColor(newPalette.textPrimary)
            child.findViewById<TextView>(R.id.tv_note_count)
                ?.setTextColor(newPalette.textSecondary)
            child.findViewById<ImageView>(R.id.iv_toggle)
                ?.setColorFilter(newPalette.textSecondary)
            child.findViewById<ImageView>(R.id.iv_toggle_left)
                ?.setColorFilter(newPalette.textSecondary)

            // ChapterDivider
            child.findViewById<View>(R.id.chapter_divider_line)
                ?.setBackgroundColor(newPalette.dividerColor)
            child.findViewById<TextView>(R.id.tv_chapter_title)
                ?.setTextColor(newPalette.textSecondary)

            // NoteEntry
            child.findViewById<TextView>(R.id.tv_selected_text)
                ?.setTextColor(newPalette.textPrimary)
            child.findViewById<TextView>(R.id.tv_note_content)
                ?.setTextColor(newPalette.textSecondary)

            // CheckBox（三种 VH 共用 id）
            child.findViewById<CheckBox>(R.id.cb_select)?.buttonTintList =
                ColorStateList.valueOf(newPalette.textSecondary)
        }
    }

    fun updateItems(newItems: List<AllNotesItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** Provide full highlight list so selection works even when books are collapsed */
    fun updateAllHighlights(highlights: List<Highlight>) {
        allHighlights = highlights
    }

    fun enterSelectionMode() {
        isSelectionMode = true
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
    }

    /** Get all highlight IDs belonging to a given novelId (from full list, not just visible items) */
    private fun getHighlightIdsForNovel(novelId: String): Set<String> {
        return allHighlights.filter { it.novelId == novelId }
            .map { it.id }
            .toSet()
    }

    /** Get all highlight IDs belonging to a given chapterId (from full list, not just visible items) */
    private fun getHighlightIdsForChapter(chapterId: String): Set<String> {
        return allHighlights.filter { it.chapterId == chapterId }
            .map { it.id }
            .toSet()
    }

    /** Check if ALL highlights for a novel are selected */
    private fun isNovelFullySelected(novelId: String): Boolean {
        val ids = getHighlightIdsForNovel(novelId)
        return ids.isNotEmpty() && selectedIds.containsAll(ids)
    }

    /** Check if ALL highlights for a chapter are selected */
    private fun isChapterFullySelected(chapterId: String): Boolean {
        val ids = getHighlightIdsForChapter(chapterId)
        return ids.isNotEmpty() && selectedIds.containsAll(ids)
    }

    private fun toggleNovelSelection(novelId: String) {
        val ids = getHighlightIdsForNovel(novelId)
        if (selectedIds.containsAll(ids)) {
            selectedIds.removeAll(ids)
        } else {
            selectedIds.addAll(ids)
        }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    private fun toggleChapterSelection(chapterId: String) {
        val ids = getHighlightIdsForChapter(chapterId)
        if (selectedIds.containsAll(ids)) {
            selectedIds.removeAll(ids)
        } else {
            selectedIds.addAll(ids)
        }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    private fun toggleNoteSelection(highlightId: String) {
        if (selectedIds.contains(highlightId)) {
            selectedIds.remove(highlightId)
        } else {
            selectedIds.add(highlightId)
        }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is AllNotesItem.BookHeader -> TYPE_BOOK_HEADER
        is AllNotesItem.ChapterDivider -> TYPE_CHAPTER_DIVIDER
        is AllNotesItem.NoteEntry -> TYPE_NOTE_ENTRY
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_BOOK_HEADER -> BookHeaderVH(
                inflater.inflate(R.layout.item_all_notes_book_header, parent, false)
            )
            TYPE_CHAPTER_DIVIDER -> ChapterDividerVH(
                inflater.inflate(R.layout.item_all_notes_chapter_divider, parent, false)
            )
            TYPE_NOTE_ENTRY -> NoteEntryVH(
                inflater.inflate(R.layout.item_all_notes_entry, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AllNotesItem.BookHeader -> (holder as BookHeaderVH).bind(item)
            is AllNotesItem.ChapterDivider -> (holder as ChapterDividerVH).bind(item)
            is AllNotesItem.NoteEntry -> (holder as NoteEntryVH).bind(item)
        }
        applyBookBubble(holder.itemView, position)
    }

    /** 判定是否为同一本书气泡的首条（当前条为 BookHeader） */
    private fun isFirstInBookBubble(position: Int): Boolean =
        items[position] is AllNotesItem.BookHeader

    /** 判定是否为同一本书气泡的末条（下一条是新书的 BookHeader 或列表末尾） */
    private fun isLastInBookBubble(position: Int): Boolean =
        position == items.size - 1 || items[position + 1] is AllNotesItem.BookHeader

    /** 为每本书的笔记条目绘制一个外部大框（线框效果）。
     *  首条画左边+右边+上边+上圆角，中间条只画左边+右边，末条画左边+右边+下边+下圆角。
     *  无填充（透明），边框为 3dp，颜色绑定标题栏三色（titleBarBg）。
     *  在 draw() 时实时读取 palette，以支持主题过渡动画。 */
    private fun applyBookBubble(itemView: View, position: Int) {
        val density = itemView.resources.displayMetrics.density
        val r = 16f * density
        val sw = 3f * density
        val first = isFirstInBookBubble(position)
        val last = isLastInBookBubble(position)

        itemView.background = object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = sw
                strokeCap = Paint.Cap.BUTT
            }

            override fun draw(canvas: Canvas) {
                // 实时读取当前 palette 颜色，支持主题渐变动画
                paint.color = palette.titleBarBg

                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                val hf = sw / 2f  // half stroke width: stroke 中心线距边界的偏移

                // ---- 左、右边线 ----
                val topClip = if (first) hf + r else 0f
                val bottomClip = if (last) h - hf - r else h
                canvas.drawLine(hf, topClip, hf, bottomClip, paint)
                canvas.drawLine(w - hf, topClip, w - hf, bottomClip, paint)

                // ---- 上边 + 上圆角（仅首条） ----
                if (first) {
                    canvas.drawLine(hf + r, hf, w - hf - r, hf, paint)
                    // 左上圆角
                    canvas.drawArc(RectF(hf, hf, hf + 2 * r, hf + 2 * r), 180f, 90f, false, paint)
                    // 右上圆角
                    canvas.drawArc(RectF(w - hf - 2 * r, hf, w - hf, hf + 2 * r), 270f, 90f, false, paint)
                }

                // ---- 下边 + 下圆角（仅末条） ----
                if (last) {
                    canvas.drawLine(hf + r, h - hf, w - hf - r, h - hf, paint)
                    // 左下圆角
                    canvas.drawArc(RectF(hf, h - hf - 2 * r, hf + 2 * r, h - hf), 90f, 90f, false, paint)
                    // 右下圆角
                    canvas.drawArc(RectF(w - hf - 2 * r, h - hf - 2 * r, w - hf, h - hf), 0f, 90f, false, paint)
                }
            }

            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSPARENT
        }
        val lp = itemView.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val side = (12 * density).toInt()
        val gap = (6 * density).toInt()
        lp.leftMargin = side
        lp.rightMargin = side
        lp.topMargin = if (first) gap else 0
        lp.bottomMargin = if (last) gap else 0
        itemView.layoutParams = lp
    }

    inner class BookHeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val row: View = view.findViewById(R.id.book_header_row)
        private val divider: View = view.findViewById(R.id.header_divider)
        private val tvBookTitle: TextView = view.findViewById(R.id.tv_book_title)
        private val ivToggle: ImageView = view.findViewById(R.id.iv_toggle)
        private val tvNoteCount: TextView = view.findViewById(R.id.tv_note_count)
        private val btnToggle: View = view.findViewById(R.id.btn_toggle)
        private val ivToggleLeft: ImageView = view.findViewById(R.id.iv_toggle_left)
        private val cbSelect: CheckBox = view.findViewById(R.id.cb_select)

        fun bind(item: AllNotesItem.BookHeader) {
            tvBookTitle.text = item.novelTitle

            row.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            divider.setBackgroundColor(palette.dividerColor)
            tvBookTitle.setTextColor(palette.textPrimary)
            tvNoteCount.setTextColor(palette.textSecondary)
            ivToggle.setColorFilter(palette.textSecondary)
            ivToggleLeft.setColorFilter(palette.textSecondary)
            cbSelect.buttonTintList = ColorStateList.valueOf(palette.textSecondary)

            val toggleIcon = if (item.isCollapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less

            if (isSelectionMode) {
                // Selection mode: toggle left visible, normal toggle hidden, checkbox visible
                btnToggle.visibility = View.GONE
                ivToggleLeft.visibility = View.VISIBLE
                ivToggleLeft.setImageResource(toggleIcon)
                cbSelect.visibility = View.VISIBLE
                cbSelect.isChecked = isNovelFullySelected(item.novelId)

                cbSelect.setOnClickListener { toggleNovelSelection(item.novelId) }
                // Selection mode: tapping row (or toggle icon) folds/unfolds;
                // only the checkbox toggles selection.
                val toggleListener = View.OnClickListener { onToggleBook(item.novelId) }
                ivToggleLeft.isClickable = true
                ivToggleLeft.setOnClickListener(toggleListener)
                itemView.setOnClickListener(toggleListener)
            } else {
                // Normal mode
                btnToggle.visibility = View.VISIBLE
                ivToggleLeft.visibility = View.GONE
                cbSelect.visibility = View.GONE

                tvNoteCount.text = "共${item.noteCount}条"
                ivToggle.setImageResource(toggleIcon)

                val clickListener = View.OnClickListener { onToggleBook(item.novelId) }
                btnToggle.setOnClickListener(clickListener)
                itemView.setOnClickListener(clickListener)
                cbSelect.setOnClickListener(null)
            }
        }
    }

    inner class ChapterDividerVH(view: View) : RecyclerView.ViewHolder(view) {
        private val line: View = view.findViewById(R.id.chapter_divider_line)
        private val tvChapterTitle: TextView = view.findViewById(R.id.tv_chapter_title)
        private val cbSelect: CheckBox = view.findViewById(R.id.cb_select)

        fun bind(item: AllNotesItem.ChapterDivider) {
            tvChapterTitle.text = item.chapterTitle

            line.setBackgroundColor(palette.dividerColor)
            tvChapterTitle.setTextColor(palette.textSecondary)
            cbSelect.buttonTintList = ColorStateList.valueOf(palette.textSecondary)

            if (isSelectionMode) {
                cbSelect.visibility = View.VISIBLE
                cbSelect.isChecked = isChapterFullySelected(item.chapterId)
                cbSelect.setOnClickListener { toggleChapterSelection(item.chapterId) }
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            } else {
                cbSelect.visibility = View.GONE
                cbSelect.setOnClickListener(null)
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            }
        }
    }

    inner class NoteEntryVH(view: View) : RecyclerView.ViewHolder(view) {
        private val colorBar: View = view.findViewById(R.id.view_color_bar)
        private val tvSelectedText: TextView = view.findViewById(R.id.tv_selected_text)
        private val tvNoteContent: TextView = view.findViewById(R.id.tv_note_content)
        private val cbSelect: CheckBox = view.findViewById(R.id.cb_select)

        fun bind(item: AllNotesItem.NoteEntry) {
            colorBar.setBackgroundColor(item.highlight.color)
            tvSelectedText.text = item.highlight.selectedText

            tvSelectedText.setTextColor(palette.textPrimary)
            tvNoteContent.setTextColor(palette.textSecondary)
            cbSelect.buttonTintList = ColorStateList.valueOf(palette.textSecondary)

            if (item.highlight.isNote) {
                tvNoteContent.visibility = View.VISIBLE
                tvNoteContent.text = item.highlight.noteContent
            } else {
                tvNoteContent.visibility = View.GONE
            }

            if (isSelectionMode) {
                cbSelect.visibility = View.VISIBLE
                cbSelect.isChecked = selectedIds.contains(item.highlight.id)
                cbSelect.setOnClickListener { toggleNoteSelection(item.highlight.id) }
                itemView.setOnClickListener { toggleNoteSelection(item.highlight.id) }
            } else {
                cbSelect.visibility = View.GONE
                cbSelect.setOnClickListener(null)
                itemView.setOnClickListener { onNoteClick(item.highlight, item.novelTitle) }
            }
        }
    }
}
