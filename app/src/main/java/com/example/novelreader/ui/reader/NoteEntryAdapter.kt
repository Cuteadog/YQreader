package com.example.novelreader.ui.reader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.novelreader.R
import com.example.novelreader.data.model.Highlight
import com.example.novelreader.data.model.Note

sealed class NoteEntry {
    data class HighlightEntry(val highlight: Highlight) : NoteEntry()
    data class NoteData(val note: Note) : NoteEntry()
}

class NoteEntryAdapter(
    private var entries: List<NoteEntry>,
    private val onDelete: (NoteEntry) -> Unit,
    private val onJumpTo: (NoteEntry) -> Unit
) : RecyclerView.Adapter<NoteEntryAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val colorBar: View = view.findViewById(R.id.view_highlight_color)
        val tvChapter: TextView = view.findViewById(R.id.tv_note_chapter)
        val tvSelectedText: TextView = view.findViewById(R.id.tv_note_selected_text)
        val tvNoteContent: TextView = view.findViewById(R.id.tv_note_content)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_note)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_note_entry, parent, false)
        return VH(v)
    }

    override fun getItemCount() = entries.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = entries[position]
        when (entry) {
            is NoteEntry.HighlightEntry -> {
                val h = entry.highlight
                holder.colorBar.setBackgroundColor(h.color)
                holder.tvChapter.text = "高亮"
                holder.tvSelectedText.text = h.selectedText
                holder.tvNoteContent.visibility = View.GONE
            }
            is NoteEntry.NoteData -> {
                val n = entry.note
                holder.colorBar.setBackgroundColor(n.highlightColor)
                holder.tvChapter.text = "笔记 · ${n.chapterTitle}"
                holder.tvSelectedText.text = "「${n.selectedText}」"
                if (n.noteContent.isNotBlank()) {
                    holder.tvNoteContent.visibility = View.VISIBLE
                    holder.tvNoteContent.text = n.noteContent
                } else {
                    holder.tvNoteContent.visibility = View.GONE
                }
            }
        }
        holder.btnDelete.setOnClickListener { onDelete(entry) }
        holder.itemView.setOnClickListener { onJumpTo(entry) }
    }

    fun updateEntries(newEntries: List<NoteEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}
