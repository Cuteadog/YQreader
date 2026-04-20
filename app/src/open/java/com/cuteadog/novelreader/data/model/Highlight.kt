package com.cuteadog.novelreader.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Highlight(
    val id: String,
    val chapterId: String,
    val novelId: String,
    val selectedText: String,
    val startOffset: Int,      // chapter-absolute
    val endOffset: Int,        // chapter-absolute
    val color: Int = YELLOW,
    val noteContent: String = "",
    val chapterTitle: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val isNote: Boolean get() = noteContent.isNotBlank()

    companion object {
        // 旧搭配
        // const val YELLOW = 0xC8FFD600.toInt()
        // const val GREEN  = 0xC8A5D6A7.toInt()
        // const val PINK   = 0xC8F48FB1.toInt()
        // const val BLUE   = 0xC881D4FA.toInt()

        const val YELLOW = 0xFFE8D48B.toInt()  // 柔和米黄色
        const val GREEN  = 0xFFB8D9B8.toInt()  // 柔和豆绿色
        const val PINK   = 0xFFE8BAC8.toInt()  // 柔和樱花粉
        const val BLUE   = 0xFFB8D4E8.toInt()  // 柔和雾霾蓝
        val ALL_COLORS = listOf(YELLOW, GREEN, PINK, BLUE)
        val COLOR_NAMES = listOf("黄", "绿", "粉", "蓝")
    }
}
