package com.cuteadog.novelreader.data.dao

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cuteadog.novelreader.data.model.Highlight
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NoteDao(private val dataStore: DataStore<Preferences>) {

    private val HIGHLIGHTS_KEY = stringPreferencesKey("highlights")
    private val NOTES_KEY = stringPreferencesKey("notes")
    private val json = Json { ignoreUnknownKeys = true }

    /** 旧版 Note 数据结构，仅用于迁移 */
    @Serializable
    private data class LegacyNote(
        val id: String = "",
        val chapterId: String = "",
        val novelId: String = "",
        val chapterTitle: String = "",
        val selectedText: String = "",
        val noteContent: String = "",
        val startOffset: Int = 0,
        val endOffset: Int = 0,
        val highlightColor: Int = Highlight.YELLOW,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * 将旧版 notes 数据迁移到统一的 highlights 存储中。
     * 迁移后删除旧的 notes key 和关联的 _hl 高亮副本。
     */
    suspend fun migrateIfNeeded() {
        val prefs = dataStore.data.first()
        val notesRaw = prefs[NOTES_KEY] ?: return // 没有旧数据，无需迁移

        val legacyNotes = try {
            json.decodeFromString<List<LegacyNote>>(notesRaw)
        } catch (_: Exception) {
            emptyList()
        }
        if (legacyNotes.isEmpty()) {
            // 清除空的 notes key
            dataStore.edit { it.remove(NOTES_KEY) }
            return
        }

        val highlightsRaw = prefs[HIGHLIGHTS_KEY] ?: "[]"
        val highlights = json.decodeFromString<MutableList<Highlight>>(highlightsRaw)

        for (note in legacyNotes) {
            // 移除旧的 _hl 关联高亮副本
            highlights.removeAll { it.id == note.id + "_hl" }
            // 移除已有相同 id 的记录
            highlights.removeAll { it.id == note.id }
            // 将笔记转为统一的 Highlight
            highlights.add(Highlight(
                id = note.id,
                chapterId = note.chapterId,
                novelId = note.novelId,
                selectedText = note.selectedText,
                startOffset = note.startOffset,
                endOffset = note.endOffset,
                color = note.highlightColor,
                noteContent = note.noteContent,
                chapterTitle = note.chapterTitle,
                timestamp = note.timestamp
            ))
        }

        dataStore.edit {
            it[HIGHLIGHTS_KEY] = json.encodeToString(highlights)
            it.remove(NOTES_KEY)
        }
    }

    suspend fun getAllHighlights(): List<Highlight> {
        val prefs = dataStore.data.first()
        val raw = prefs[HIGHLIGHTS_KEY] ?: "[]"
        return json.decodeFromString<List<Highlight>>(raw)
    }

    suspend fun getHighlightsForChapter(chapterId: String): List<Highlight> {
        val prefs = dataStore.data.first()
        val raw = prefs[HIGHLIGHTS_KEY] ?: "[]"
        return json.decodeFromString<List<Highlight>>(raw).filter { it.chapterId == chapterId }
    }

    suspend fun getAllHighlightsForNovel(novelId: String): List<Highlight> {
        val prefs = dataStore.data.first()
        val raw = prefs[HIGHLIGHTS_KEY] ?: "[]"
        return json.decodeFromString<List<Highlight>>(raw).filter { it.novelId == novelId }
    }

    suspend fun saveHighlight(highlight: Highlight) {
        val prefs = dataStore.data.first()
        val raw = prefs[HIGHLIGHTS_KEY] ?: "[]"
        val list = json.decodeFromString<MutableList<Highlight>>(raw)
        list.removeAll { it.id == highlight.id }
        list.add(highlight)
        dataStore.edit { it[HIGHLIGHTS_KEY] = json.encodeToString(list) }
    }

    suspend fun deleteHighlightsByIds(ids: Set<String>) {
        val prefs = dataStore.data.first()
        val raw = prefs[HIGHLIGHTS_KEY] ?: "[]"
        val list = json.decodeFromString<MutableList<Highlight>>(raw)
        list.removeAll { it.id in ids }
        dataStore.edit { it[HIGHLIGHTS_KEY] = json.encodeToString(list) }
    }

    suspend fun deleteHighlight(highlightId: String) {
        val prefs = dataStore.data.first()
        val raw = prefs[HIGHLIGHTS_KEY] ?: "[]"
        val list = json.decodeFromString<MutableList<Highlight>>(raw)
        list.removeAll { it.id == highlightId }
        dataStore.edit { it[HIGHLIGHTS_KEY] = json.encodeToString(list) }
    }

    suspend fun deleteHighlightsForNovel(novelId: String) {
        val prefs = dataStore.data.first()
        val raw = prefs[HIGHLIGHTS_KEY] ?: "[]"
        val list = json.decodeFromString<MutableList<Highlight>>(raw)
        list.removeAll { it.novelId == novelId }
        dataStore.edit { it[HIGHLIGHTS_KEY] = json.encodeToString(list) }
    }

    suspend fun deleteHighlightsForChapter(chapterId: String) {
        val prefs = dataStore.data.first()
        val raw = prefs[HIGHLIGHTS_KEY] ?: "[]"
        val list = json.decodeFromString<MutableList<Highlight>>(raw)
        list.removeAll { it.chapterId == chapterId }
        dataStore.edit { it[HIGHLIGHTS_KEY] = json.encodeToString(list) }
    }
}
