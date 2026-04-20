package com.cuteadog.novelreader.data.model

import kotlinx.serialization.Serializable

/**
 * Personal版本的Highlight - stub实现
 * 高亮功能在personal版本中不可用
 */
@Serializable
data class Highlight(
    val id: String = "",
    val chapterId: String = "",
    val novelId: String = "",
    val selectedText: String = "",
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val color: Int = 0,
    val noteContent: String = "",
    val chapterTitle: String = "",
    val timestamp: Long = 0
) {
    val isNote: Boolean get() = noteContent.isNotBlank()

    companion object {
        const val YELLOW = 0
        const val GREEN = 1
        const val BLUE = 2
        const val RED = 3
    }
}
