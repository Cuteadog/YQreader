package com.cuteadog.novelreader.ui.notes

import android.content.res.ColorStateList
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

            row.setBackgroundColor(palette.surfaceBg)
            divider.setBackgroundColor(palette.dividerColor)
            tvBookTitle.setTextColor(palette.textPrimary)
            tvNoteCount.setTextColor(palette.textSecondary)
            ivToggle.setColorFilter(palette.textSecondary)
            ivToggleLeft.setColorFilter(palette.textSecondary)
            cbSelect.buttonTintList = ColorStateList.valueOf(palette.buttonBg)

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

            itemView.setBackgroundColor(palette.pageBg)
            line.setBackgroundColor(palette.dividerColor)
            tvChapterTitle.setTextColor(palette.textSecondary)
            cbSelect.buttonTintList = ColorStateList.valueOf(palette.buttonBg)

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

            itemView.setBackgroundColor(palette.pageBg)
            tvSelectedText.setTextColor(palette.textPrimary)
            tvNoteContent.setTextColor(palette.textSecondary)
            cbSelect.buttonTintList = ColorStateList.valueOf(palette.buttonBg)

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
