package com.cuteadog.novelreader.data.model

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

@Serializable
data class Chapter(
    val id: String,
    val novelId: String,
    val title: String,
    val filePath: String,
    val pageCount: Int = 0,
    val currentPageIndex: Int = 0
) : JavaSerializable {
    companion object {
        fun createFromFile(novelId: String, fileName: String, filePath: String): Chapter {
            val title = fileName.substringBeforeLast('.').let {
                if (it.equals("PROLOGUE", ignoreCase = true)) {
                    "序章"
                } else {
                    it
                }
            }
            
            return Chapter(
                id = "${novelId}_${fileName.hashCode()}",
                novelId = novelId,
                title = title,
                filePath = filePath
            )
        }
    }
}