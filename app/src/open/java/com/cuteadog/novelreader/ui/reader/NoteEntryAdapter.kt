package com.cuteadog.novelreader.ui.reader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.data.model.Highlight
import com.cuteadog.novelreader.ui.theme.ThemeManager
import com.cuteadog.novelreader.ui.theme.ThemePalette

class NoteEntryAdapter(
    private var entries: List<Highlight>,
    private val onDelete: (Highlight) -> Unit,
    private val onJumpTo: (Highlight) -> Unit
) : RecyclerView.Adapter<NoteEntryAdapter.VH>() {

    private var palette: ThemePalette = ThemeManager.currentPalette()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val colorBar: View = view.findViewById(R.id.view_highlight_color)
        val tvChapter: TextView = view.findViewById(R.id.tv_note_chapter)
        val tvSelectedText: TextView = view.findViewById(R.id.tv_note_selected_text)
        val tvNoteContent: TextView = view.findViewById(R.id.tv_note_content)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_note)
        val divider: View = view.findViewById(R.id.note_entry_divider)
    }

    fun updatePalette(palette: ThemePalette) {
        this.palette = palette
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_note_entry, parent, false)
        return VH(v)
    }

    override fun getItemCount() = entries.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val h = entries[position]
        holder.colorBar.setBackgroundColor(h.color)
        holder.tvChapter.setTextColor(palette.textSecondary)
        holder.tvSelectedText.setTextColor(palette.textPrimary)
        holder.tvNoteContent.setTextColor(palette.textSecondary)
        holder.btnDelete.setColorFilter(palette.textSecondary)
        holder.divider.setBackgroundColor(palette.dividerColor)
        if (h.isNote) {
            holder.tvChapter.text = "笔记 · ${h.chapterTitle}"
            holder.tvSelectedText.text = "「${h.selectedText}」"
            holder.tvNoteContent.visibility = View.VISIBLE
            holder.tvNoteContent.text = h.noteContent
        } else {
            holder.tvChapter.text = if (h.chapterTitle.isNotBlank()) "高亮 · ${h.chapterTitle}" else "高亮"
            holder.tvSelectedText.text = h.selectedText
            holder.tvNoteContent.visibility = View.GONE
        }
        holder.btnDelete.setOnClickListener { onDelete(h) }
        holder.itemView.setOnClickListener { onJumpTo(h) }
    }

    fun updateEntries(newEntries: List<Highlight>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}
