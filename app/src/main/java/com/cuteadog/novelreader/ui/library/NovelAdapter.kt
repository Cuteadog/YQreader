package com.cuteadog.novelreader.ui.library

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cuteadog.novelreader.BuildConfig
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.data.model.Novel
import com.cuteadog.novelreader.databinding.ItemNovelBinding
import com.cuteadog.novelreader.ui.theme.ThemeManager
import com.cuteadog.novelreader.ui.theme.ThemePalette
import coil.load
import java.time.format.DateTimeFormatter

class NovelAdapter(
    private val novels: List<Novel>,
    private val isSelectionMode: Boolean,
    private val onNovelClick: (Novel) -> Unit,
    private val onNovelLongClick: (Novel) -> Unit
) : RecyclerView.Adapter<NovelAdapter.NovelViewHolder>() {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private var palette: ThemePalette? =
        if (BuildConfig.ENABLE_NOTES_HIGHLIGHT) ThemeManager.currentPalette() else null

    fun updatePalette(newPalette: ThemePalette) {
        palette = newPalette
        notifyDataSetChanged()
    }

    /**
     * 主题过渡动画期间：原地更新可见 item 的气泡底色与文字色，避免 notifyDataSetChanged 的闪烁。
     * 不可见 item 会在滑入时通过 bind() 取最新 palette。
     */
    fun applyLivePalette(rv: RecyclerView, newPalette: ThemePalette) {
        palette = newPalette
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val bg = child.background as? GradientDrawable ?: continue
            bg.setColor(newPalette.titleBarBg)
            child.findViewById<TextView>(R.id.tv_title)?.setTextColor(newPalette.titleBarFg)
            child.findViewById<TextView>(R.id.tv_author)?.setTextColor(newPalette.titleBarFgMuted)
            child.findViewById<TextView>(R.id.tv_last_read)?.setTextColor(newPalette.titleBarFgMuted)
            child.findViewById<android.widget.CheckBox>(R.id.cb_select)?.buttonTintList =
                ColorStateList.valueOf(newPalette.titleBarFg)
        }
    }

    inner class NovelViewHolder(private val binding: ItemNovelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(novel: Novel) {
            // 设置封面
            if (novel.coverPath != null) {
                binding.ivCover.load(novel.coverPath) {
                    placeholder(R.drawable.ic_book_placeholder)
                    error(R.drawable.ic_book_placeholder)
                }
            } else {
                binding.ivCover.setImageResource(R.drawable.ic_book_placeholder)
            }

            // 设置标题
            binding.tvTitle.text = novel.title

            // 设置作者
            binding.tvAuthor.text = novel.author

            // 设置最后阅读时间
            binding.tvLastRead.text = novel.getLastReadTimeAsLocalDateTime().format(formatter)

            // 设置选择模式下的复选框
            if (isSelectionMode) {
                binding.cbSelect.visibility = View.VISIBLE
                // 先移除监听器，避免在设置 isChecked 时触发回调
                binding.cbSelect.setOnCheckedChangeListener(null)
                binding.cbSelect.isChecked = novel.isSelected
                // 添加监听器：当用户点击复选框时，更新选中状态
                binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    // 只有当状态确实变化时才更新
                    if (isChecked != novel.isSelected) {
                        onNovelLongClick(novel)
                    }
                }
            } else {
                binding.cbSelect.visibility = View.GONE
                // 清理监听器，避免内存泄漏
                binding.cbSelect.setOnCheckedChangeListener(null)
            }

            // 设置点击事件（整行点击）
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    // 选择模式下点击整行，也触发选中切换
                    onNovelLongClick(novel)
                } else {
                    onNovelClick(novel)
                }
            }

            // 设置长按事件（进入选择模式）
            binding.root.setOnLongClickListener {
                onNovelLongClick(novel)
                true
            }

            palette?.let { p ->
                val density = itemView.resources.displayMetrics.density
                binding.root.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f * density
                    setColor(p.titleBarBg)
                }
                binding.tvTitle.setTextColor(p.titleBarFg)
                binding.tvAuthor.setTextColor(p.titleBarFgMuted)
                binding.tvLastRead.setTextColor(p.titleBarFgMuted)
                binding.cbSelect.buttonTintList = ColorStateList.valueOf(p.titleBarFg)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NovelViewHolder {
        val binding = ItemNovelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NovelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NovelViewHolder, position: Int) {
        if (position < novels.size) {
            holder.bind(novels[position])
        }
    }

    override fun getItemCount(): Int = novels.size
}