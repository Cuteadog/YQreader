package com.example.novelreader.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Data
import com.example.novelreader.data.model.Chapter
import com.example.novelreader.data.model.Novel
import com.example.novelreader.util.FileUtil
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import androidx.core.net.toUri

class ImportWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        // 导入相关常量
        const val ACTION_IMPORT_STARTED = "com.example.novelreader.ACTION_IMPORT_STARTED"
        const val ACTION_IMPORT_PROGRESS = "com.example.novelreader.ACTION_IMPORT_PROGRESS"
        const val ACTION_IMPORT_COMPLETED = "com.example.novelreader.ACTION_IMPORT_COMPLETED"
        const val ACTION_IMPORT_ERROR = "com.example.novelreader.ACTION_IMPORT_ERROR"

        const val EXTRA_PROGRESS = "com.example.novelreader.EXTRA_PROGRESS"
        const val EXTRA_ERROR = "com.example.novelreader.EXTRA_ERROR"

        const val EXTRA_FOLDER_URI = "com.example.novelreader.EXTRA_FOLDER_URI"
        const val EXTRA_NOVELS_DIR = "com.example.novelreader.EXTRA_NOVELS_DIR"

        fun enqueueWork(context: Context, folderUri: Uri, novelsDir: String) {
            try {
                val data = Data.Builder()
                    .putString(EXTRA_FOLDER_URI, folderUri.toString())
                    .putString(EXTRA_NOVELS_DIR, novelsDir)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<ImportWorker>()
                    .setInputData(data)
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)
                Log.d("ImportWorker", "任务已入队，URI: $folderUri")
            } catch (e: Exception) {
                Log.e("ImportWorker", "入队失败", e)
            }
        }
    }
    override suspend fun doWork(): Result {
        val folderUriString = inputData.getString(EXTRA_FOLDER_URI) ?: return Result.failure()
        val novelsDirPath = inputData.getString(EXTRA_NOVELS_DIR) ?: return Result.failure()

        val folderUri = folderUriString.toUri()
        val novelsDir = File(novelsDirPath)

        Log.e("ImportWorker", "开始导入小说，文件夹URI: $folderUri")
        Log.e("ImportWorker", "小说存储目录: $novelsDirPath")

        // 发送开始导入的广播
        sendBroadcast(ACTION_IMPORT_STARTED, null)

        try {
            // 获取文件夹名称作为小说标题
            val folderName = getFolderName(folderUri)
            if (folderName.isNullOrEmpty()) {
                throw Exception("无法获取文件夹名称")
            }

            // 创建小说ID
            val novelId = UUID.randomUUID().toString()

            // 创建小说目录
            val novelDir = File(novelsDir, novelId)
            if (!FileUtil.createDirectoryIfNotExists(novelDir)) {
                throw Exception("无法创建小说目录")
            }

            // 查找封面图片
            var coverPath: String? = null
            val coverFile = File(novelDir, "cover.png")

            // 扫描文件夹中的所有.txt文件
            val txtFiles = mutableListOf<Uri>()

            // 检查是否有icon.png文件
            val iconUri = DocumentsContract.buildDocumentUriUsingTree(folderUri,
                DocumentsContract.getTreeDocumentId(folderUri) + "/icon.png")
            try {
                applicationContext.contentResolver.openInputStream(iconUri)?.use { inputStream ->
                    FileUtil.copyFileFromUri(applicationContext, iconUri, coverFile)
                    coverPath = coverFile.absolutePath
                }
            } catch (e: Exception) {
                // icon.png不存在，继续执行
                Log.e("ImportWorker", "未找到icon.png文件", e)
            }

            // 获取所有.txt文件
            txtFiles.addAll(getTxtFilesFromTreeUri(folderUri))

            Log.e("ImportWorker", "找到的.txt文件数量: ${txtFiles.size}")

            // 检查文件夹是否为空
            if (txtFiles.isEmpty() && coverPath == null) {
                throw Exception("空文件夹")
            } else if (txtFiles.isEmpty()) {
                throw Exception("没有找到.txt文件")
            }

            // 发送进度广播
            sendProgressBroadcast(30)

            // 复制.txt文件到小说目录
            val chapters = mutableListOf<Chapter>()
            txtFiles.forEachIndexed { index, documentUri ->
                try {
                    // 获取文件名
                    val fileName = getFileNameFromUri(documentUri)
                    val destFile = File(novelDir, fileName)

                    // 从Uri复制文件
                    FileUtil.copyFileFromUri(applicationContext, documentUri, destFile)

                    // 创建章节对象
                    val chapter = Chapter.createFromFile(novelId, fileName, destFile.absolutePath)
                    chapters.add(chapter)

                    // 发送进度广播
                    val progress = 30 + (index * 60) / txtFiles.size
                    sendProgressBroadcast(progress)
                } catch (e: Exception) {
                    Log.e("ImportWorker", "复制文件失败: $documentUri", e)
                }
            }

            // 发送进度广播
            sendProgressBroadcast(95)

            // 创建小说对象
            val novel = Novel(
                id = novelId,
                title = folderName,
                coverPath = coverPath,
                lastReadTime = java.time.LocalDateTime.now().format(Novel.formatter),
                totalChapters = chapters.size
            )

            Log.e("ImportWorker", "小说导入完成，章节数量: ${chapters.size}")
            Log.e("ImportWorker", "小说信息: $novel")

            // 发送完成广播
            val resultIntent = Intent(ACTION_IMPORT_COMPLETED)
            // 将对象序列化为JSON字符串
            val novelJson = Json.encodeToString(novel)
            val chaptersJson = Json.encodeToString(chapters)
            resultIntent.putExtra("novelJson", novelJson)
            resultIntent.putExtra("chaptersJson", chaptersJson)

            Log.e("ImportWorker", "发送完成广播")
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(resultIntent)

            return Result.success()
        } catch (e: Exception) {
            Log.e("ImportWorker", "导入小说失败", e)
            // 生成更详细的错误信息
            val detailedError = generateDetailedErrorMessage(e)
            sendErrorBroadcast(detailedError)
            return Result.failure()
        }
    }
    private fun getFolderName(uri: Uri): String? {
        // 从 treeUri 获取文档 ID
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        // 构建该文档本身的 Uri（不是整个树）
        val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        applicationContext.contentResolver.query(docUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return null
    }



    private fun sendBroadcast(action: String, extras: (Intent.() -> Unit)?) {
        val intent = Intent(action)
        extras?.invoke(intent)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun sendProgressBroadcast(progress: Int) {
        sendBroadcast(ACTION_IMPORT_PROGRESS) {
            putExtra(EXTRA_PROGRESS, progress)
        }
    }

    private fun sendErrorBroadcast(errorMessage: String) {
        sendBroadcast(ACTION_IMPORT_ERROR) {
            putExtra(EXTRA_ERROR, errorMessage)
        }
    }

    // 添加详细的错误信息生成方法
    private fun generateDetailedErrorMessage(exception: Exception): String {
        return when (exception.message) {
            "空文件夹" -> "选择的文件夹为空，请确保文件夹中包含.txt小说文件"
            "没有找到.txt文件" -> "文件夹中没有找到.txt小说文件，请确保小说文件格式正确"
            "无法获取文件夹名称" -> "无法获取文件夹名称，请选择一个有效的文件夹"
            "无法创建小说目录" -> "无法创建小说目录，请检查应用存储权限"
            else -> exception.message ?: "导入失败，请检查文件夹内容"
        }
    }

    private fun getTxtFilesFromTreeUri(treeUri: Uri, parentDocumentId: String? = null): List<Uri> {
        val txtFiles = mutableListOf<Uri>()
        val documentId = parentDocumentId ?: DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

        applicationContext.contentResolver.query(childrenUri, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val displayName = cursor.getString(1)
                val mimeType = cursor.getString(2)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                    // 递归扫描子文件夹，传入子文件夹的 documentId
                    txtFiles.addAll(getTxtFilesFromTreeUri(treeUri, docId))
                } else if (displayName?.endsWith(".txt", ignoreCase = true) == true) {
                    txtFiles.add(docUri)
                }
            }
        }
        return txtFiles
    }

    private fun getFileNameFromUri(uri: Uri): String {
        val cursor = applicationContext.contentResolver.query(uri, arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        ), null, null, null)

        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (nameIndex != -1) {
                    return it.getString(nameIndex)
                }
            }
        }

        // 如果无法获取文件名，使用URI的最后部分
        return uri.lastPathSegment ?: "unknown.txt"
    }
}