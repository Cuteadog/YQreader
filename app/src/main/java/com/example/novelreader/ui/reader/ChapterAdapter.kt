package com.example.novelreader.ui.reader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.novelreader.R
import com.example.novelreader.data.model.Chapter
import androidx.core.graphics.toColorInt

class ChapterAdapter(
    private val chapters: List<Chapter>,
    private var currentChapterIndex: Int,
    private val onChapterClick: (Chapter, Int) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder>() {

    // 及时更新章节栏，保证当前章节为高亮章节
    fun updateCurrentIndex(newIndex: Int) {
        val oldIndex = currentChapterIndex
        currentChapterIndex = newIndex
        notifyItemChanged(oldIndex)
        notifyItemChanged(newIndex)
    }

    inner class ChapterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvChapterTitle: TextView = itemView.findViewById(R.id.tv_chapter_title)

        fun bind(chapter: Chapter, position: Int) {
            tvChapterTitle.text = chapter.title

            // 根据主题设置文字颜色
            val textColor = when (ReaderActivity.currentTheme) {
                ReaderActivity.THEME_DAY -> ContextCompat.getColor(itemView.context, R.color.text_primary)
                ReaderActivity.THEME_EYE -> ContextCompat.getColor(itemView.context, R.color.text_primary)
                ReaderActivity.THEME_NIGHT -> "#80808D".toColorInt()
                else -> ContextCompat.getColor(itemView.context, R.color.text_primary)
            }

            // 高亮当前章节
            if (position == currentChapterIndex) {
                tvChapterTitle.setTextColor(itemView.context.getColor(R.color.primary))
                tvChapterTitle.isBold = true
            } else {
                tvChapterTitle.setTextColor(textColor)
                tvChapterTitle.isBold = false
            }

            itemView.setOnClickListener {
                onChapterClick(chapter, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter, parent, false)
        return ChapterViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        holder.bind(chapters[position], position)
    }

    override fun getItemCount(): Int = chapters.size
}

// 扩展函数，用于设置TextView的粗体
private var TextView.isBold: Boolean
    get() = typeface?.isBold ?: false
    set(value) {
        typeface = android.graphics.Typeface.create(typeface, if (value) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }