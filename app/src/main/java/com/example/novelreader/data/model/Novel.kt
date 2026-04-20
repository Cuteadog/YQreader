@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.example.novelreader.data.model

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class Novel(
    val id: String,
    val title: String,
    val author: String = "未知",
    val coverPath: String?,
    val lastReadTime: String,
    val totalChapters: Int,
    val currentChapterIndex: Int = 0,
    val currentPageIndex: Int = 0,
    val fileSize: Long = 0,
    val isSelected: Boolean = false
) : JavaSerializable {
    companion object {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        
        fun createFromFolder(folderName: String, coverPath: String?, chapterCount: Int): Novel {
            return Novel(
                id = folderName.hashCode().toString(),
                title = folderName,
                coverPath = coverPath,
                lastReadTime = LocalDateTime.now().format(formatter),
                totalChapters = chapterCount
            )
        }
        
        fun parseLastReadTime(timeString: String): LocalDateTime {
            return LocalDateTime.parse(timeString, formatter)
        }
    }
    
    fun getLastReadTimeAsLocalDateTime(): LocalDateTime {
        return parseLastReadTime(lastReadTime)
    }
}