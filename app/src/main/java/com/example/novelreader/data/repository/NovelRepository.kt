package com.example.novelreader.data.repository

import com.example.novelreader.data.dao.NovelDao
import com.example.novelreader.data.model.Chapter
import com.example.novelreader.data.model.Novel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

class NovelRepository(private val novelDao: NovelDao) {

    val novels: Flow<List<Novel>> = novelDao.novels

    fun getChapters(novelId: String): Flow<List<Chapter>> {
        return novelDao.getChapters(novelId)
    }

    suspend fun addNovel(novel: Novel, chapters: List<Chapter>) {
        novelDao.addNovel(novel)
        novelDao.addChapters(chapters)
    }

    suspend fun deleteNovel(novelId: String, novelsDir: File) {
        // 删除数据库中的记录
        novelDao.deleteNovel(novelId)
        
        // 删除文件系统中的文件
        val novelDir = File(novelsDir, novelId)
        if (novelDir.exists()) {
            novelDir.deleteRecursively()
        }
    }

    suspend fun updateNovelReadProgress(novelId: String, chapterIndex: Int, pageIndex: Int) {
        novelDao.updateNovelReadProgress(novelId, chapterIndex, pageIndex)
    }

    suspend fun updateChapterPageProgress(chapterId: String, pageIndex: Int) {
        novelDao.updateChapterPageProgress(chapterId, pageIndex)
    }

    suspend fun updateNovelSelection(novelId: String, isSelected: Boolean) {
        val novels = getCurrentNovels()
        val updatedNovel = novels.find { it.id == novelId }?.copy(isSelected = isSelected)
        if (updatedNovel != null) {
            novelDao.saveNovel(updatedNovel)
        }
    }

    suspend fun clearNovelSelections() {
        val novels = getCurrentNovels()
        val updatedNovels = novels.map { it.copy(isSelected = false) }
        novelDao.saveNovels(updatedNovels)
    }

    suspend fun deleteSelectedNovels(novelsDir: File) {
        val novels = getCurrentNovels()
        val selectedNovels = novels.filter { it.isSelected }
        
        selectedNovels.forEach { novel ->
            deleteNovel(novel.id, novelsDir)
        }
    }
    
    private suspend fun getCurrentNovels(): List<Novel> {
        return novels.first()
    }
}