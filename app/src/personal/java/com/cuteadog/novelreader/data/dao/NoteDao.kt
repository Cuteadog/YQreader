package com.cuteadog.novelreader.data.dao

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cuteadog.novelreader.data.model.Highlight

/**
 * Personal版本的NoteDao - stub实现
 * 笔记和高亮功能在personal版本中不可用
 */
class NoteDao(private val dataStore: DataStore<Preferences>) {

    suspend fun migrateIfNeeded() {}

    suspend fun getHighlightsForChapter(chapterId: String): List<Highlight> = emptyList()

    suspend fun getAllHighlightsForNovel(novelId: String): List<Highlight> = emptyList()

    suspend fun saveHighlight(highlight: Highlight) {}

    suspend fun deleteHighlight(highlightId: String) {}

    suspend fun deleteHighlightsForChapter(chapterId: String) {}

    suspend fun deleteHighlightsForNovel(novelId: String) {}
}
