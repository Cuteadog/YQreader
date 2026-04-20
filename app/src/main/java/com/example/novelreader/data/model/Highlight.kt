package com.example.novelreader.data.model

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
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val YELLOW = 0xC8FFD600.toInt()
        const val GREEN  = 0xC8A5D6A7.toInt()
        const val PINK   = 0xC8F48FB1.toInt()
        const val BLUE   = 0xC881D4FA.toInt()
        val ALL_COLORS = listOf(YELLOW, GREEN, PINK, BLUE)
        val COLOR_NAMES = listOf("黄", "绿", "粉", "蓝")
    }
}
