package com.cuteadog.novelreader.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,
    val chapterId: String,
    val novelId: String,
    val chapterTitle: String,
    val selectedText: String,
    val noteContent: String,
    val startOffset: Int,
    val endOffset: Int,
    val highlightColor: Int = Highlight.YELLOW,
    val timestamp: Long = System.currentTimeMillis()
)
