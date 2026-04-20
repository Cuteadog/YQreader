package com.cuteadog.novelreader.ui.reader

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cuteadog.novelreader.data.model.Highlight

/**
 * Personal版本的NoteEntryAdapter - stub实现
 * 笔记功能在personal版本中不可用
 */
class NoteEntryAdapter(
    private var entries: List<Highlight> = emptyList(),
    private val onDelete: (Highlight) -> Unit = {},
    private val onJumpTo: (Highlight) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount(): Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        throw UnsupportedOperationException("Notes not available in personal version")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}

    fun updateEntries(newEntries: List<Highlight>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}
