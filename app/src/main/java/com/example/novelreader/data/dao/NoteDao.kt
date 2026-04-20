package com.example.novelreader.data.dao

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.novelreader.data.model.Highlight
import com.example.novelreader.data.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NoteDao(private val dataStore: DataStore<Preferences>) {

    private val HIGHLIGHTS_KEY = stringPreferencesKey("highlights")
    private val NOTES_KEY = stringPreferencesKey("notes")
    private val json = Json { ignoreUnknownKeys = true }

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

    suspend fun deleteHighlight(highlightId: String) {
        val prefs = dataStore.data.first()
        val raw = prefs[HIGHLIGHTS_KEY] ?: "[]"
        val list = json.decodeFromString<MutableList<Highlight>>(raw)
        list.removeAll { it.id == highlightId }
        dataStore.edit { it[HIGHLIGHTS_KEY] = json.encodeToString(list) }
    }

    suspend fun getNotesForChapter(chapterId: String): List<Note> {
        val prefs = dataStore.data.first()
        val raw = prefs[NOTES_KEY] ?: "[]"
        return json.decodeFromString<List<Note>>(raw).filter { it.chapterId == chapterId }
    }

    suspend fun getAllNotesForNovel(novelId: String): List<Note> {
        val prefs = dataStore.data.first()
        val raw = prefs[NOTES_KEY] ?: "[]"
        return json.decodeFromString<List<Note>>(raw).filter { it.novelId == novelId }
    }

    suspend fun saveNote(note: Note) {
        val prefs = dataStore.data.first()
        val raw = prefs[NOTES_KEY] ?: "[]"
        val list = json.decodeFromString<MutableList<Note>>(raw)
        list.removeAll { it.id == note.id }
        list.add(note)
        dataStore.edit { it[NOTES_KEY] = json.encodeToString(list) }
    }

    suspend fun deleteNote(noteId: String) {
        val prefs = dataStore.data.first()
        val raw = prefs[NOTES_KEY] ?: "[]"
        val list = json.decodeFromString<MutableList<Note>>(raw)
        list.removeAll { it.id == noteId }
        dataStore.edit { it[NOTES_KEY] = json.encodeToString(list) }
    }

    suspend fun deleteHighlightsForNovel(novelId: String) {
        val prefs = dataStore.data.first()
        val raw = prefs[HIGHLIGHTS_KEY] ?: "[]"
        val list = json.decodeFromString<MutableList<Highlight>>(raw)
        list.removeAll { it.novelId == novelId }
        dataStore.edit { it[HIGHLIGHTS_KEY] = json.encodeToString(list) }
    }

    suspend fun deleteNotesForNovel(novelId: String) {
        val prefs = dataStore.data.first()
        val raw = prefs[NOTES_KEY] ?: "[]"
        val list = json.decodeFromString<MutableList<Note>>(raw)
        list.removeAll { it.novelId == novelId }
        dataStore.edit { it[NOTES_KEY] = json.encodeToString(list) }
    }
}
