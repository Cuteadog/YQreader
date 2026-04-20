package com.cuteadog.novelreader.data.dao

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.cuteadog.novelreader.data.model.Chapter
import com.cuteadog.novelreader.data.model.Novel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

class NovelDao(private val dataStore: DataStore<Preferences>) {

    private val NOVELS_KEY = stringPreferencesKey("novels")
    private val CHAPTERS_KEY = stringPreferencesKey("chapters")
    private val json = Json { ignoreUnknownKeys = true }

    val novels: Flow<List<Novel>> = dataStore.data.map { preferences ->
        val novelsJson = preferences[NOVELS_KEY] ?: "[]"
        val novels = json.decodeFromString<List<Novel>>(novelsJson)
        novels.sortedByDescending { Novel.parseLastReadTime(it.lastReadTime) }
    }

    suspend fun saveNovels(novels: List<Novel>) {
        dataStore.edit { preferences ->
            preferences[NOVELS_KEY] = json.encodeToString(novels)
        }
    }

    suspend fun saveNovel(novel: Novel) {
        val currentNovels = getCurrentNovels()
        val updatedNovels = currentNovels.map {
            if (it.id == novel.id) novel else it
        }
        saveNovels(updatedNovels)
    }

    suspend fun addNovel(novel: Novel) {
        val currentNovels = getCurrentNovels()
        saveNovels(currentNovels + novel)
    }

    suspend fun deleteNovel(novelId: String) {
        val currentNovels = getCurrentNovels()
        saveNovels(currentNovels.filter { it.id != novelId })
        
        // 同时删除相关章节
        val currentChapters = getCurrentChapters()
        saveChapters(currentChapters.filter { it.novelId != novelId })
    }

    suspend fun updateNovelReadProgress(novelId: String, chapterIndex: Int, pageIndex: Int) {
        val currentNovels = getCurrentNovels()
        val updatedNovels = currentNovels.map {
            if (it.id == novelId) {
                it.copy(
                    currentChapterIndex = chapterIndex,
                    currentPageIndex = pageIndex,
                    lastReadTime = LocalDateTime.now().format(Novel.Companion.formatter)
                )
            } else {
                it
            }
        }
        saveNovels(updatedNovels)
    }

    fun getChapters(novelId: String): Flow<List<Chapter>> {
        return dataStore.data.map { preferences ->
            val chaptersJson = preferences[CHAPTERS_KEY] ?: "[]"
            val chapters = json.decodeFromString<List<Chapter>>(chaptersJson)
            chapters.filter { it.novelId == novelId }
                .sortedWith(compareBy<Chapter> { 
                    // 确保序章在最前面
                    if (it.title == "序章" || it.filePath.endsWith("PROLOGUE.txt", ignoreCase = true)) 0 else 1
                }.thenBy { it.title })
        }
    }

    suspend fun saveChapters(chapters: List<Chapter>) {
        dataStore.edit { preferences ->
            preferences[CHAPTERS_KEY] = json.encodeToString(chapters)
        }
    }

    suspend fun addChapters(chapters: List<Chapter>) {
        val currentChapters = getCurrentChapters()
        saveChapters(currentChapters + chapters)
    }

    suspend fun updateChapterPageProgress(chapterId: String, pageIndex: Int) {
        val currentChapters = getCurrentChapters()
        val updatedChapters = currentChapters.map {
            if (it.id == chapterId) {
                it.copy(currentPageIndex = pageIndex)
            } else {
                it
            }
        }
        saveChapters(updatedChapters)
    }

    private suspend fun getCurrentNovels(): List<Novel> {
        val preferences = dataStore.data.first()
        val novelsJson = preferences[NOVELS_KEY] ?: "[]"
        return json.decodeFromString<List<Novel>>(novelsJson)
    }

    private suspend fun getCurrentChapters(): List<Chapter> {
        val preferences = dataStore.data.first()
        val chaptersJson = preferences[CHAPTERS_KEY] ?: "[]"
        return json.decodeFromString<List<Chapter>>(chaptersJson)
    }

    /**
     * 储存位置切换后改写所有 Novel.coverPath 和 Chapter.filePath 中的目录前缀。
     * 仅当原值以 [oldPrefix] 开头时改写，避免误伤外部路径。
     */
    suspend fun rewritePathPrefix(oldPrefix: String, newPrefix: String) {
        if (oldPrefix == newPrefix) return
        val novels = getCurrentNovels().map { novel ->
            val cover = novel.coverPath
            if (cover != null && cover.startsWith(oldPrefix)) {
                novel.copy(coverPath = newPrefix + cover.substring(oldPrefix.length))
            } else {
                novel
            }
        }
        saveNovels(novels)

        val chapters = getCurrentChapters().map { chapter ->
            if (chapter.filePath.startsWith(oldPrefix)) {
                chapter.copy(filePath = newPrefix + chapter.filePath.substring(oldPrefix.length))
            } else {
                chapter
            }
        }
        saveChapters(chapters)
    }
}