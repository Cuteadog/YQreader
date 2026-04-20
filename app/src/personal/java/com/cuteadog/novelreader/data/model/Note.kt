package com.cuteadog.novelreader.data.model

import kotlinx.serialization.Serializable

/**
 * Personal版本的Note - stub实现
 * 笔记功能在personal版本中不可用
 */
@Serializable
data class Note(
    val id: String = "",
    val chapterId: String = "",
    val novelId: String = "",
    val chapterTitle: String = "",
    val selectedText: String = "",
    val noteContent: String = "",
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val highlightColor: Int = 0
)
