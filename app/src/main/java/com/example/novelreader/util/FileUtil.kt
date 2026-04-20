package com.example.novelreader.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

object FileUtil {

    fun getRealPathFromUri(context: Context, uri: Uri): String? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10及以上，直接使用DocumentsContract
                getPathFromDocumentsContract(context, uri)
            }
            else -> {
                // Android 9及以下，使用传统方法
                getPathFromLegacyUri(context, uri)
            }
        }
    }

    private fun getPathFromDocumentsContract(context: Context, uri: Uri): String? {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val documentId = DocumentsContract.getDocumentId(uri)
            
            if (uri.authority?.contains("com.android.externalstorage.documents") == true) {
                // 外部存储文档
                val parts = documentId.split(":")
                if (parts.size == 2) {
                    val type = parts[0]
                    if (type == "primary") {
                        val dir = context.getExternalFilesDir(null)
                        val parent = dir?.parentFile?.parentFile?.parentFile?.parentFile?.absolutePath
                        return "$parent/$type/${parts[1]}"
                    }
                }
            }
        }
        return null
    }

    private fun getPathFromLegacyUri(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return cursor.getString(columnIndex)
            }
        }
        return null
    }

    fun copyFile(source: File, destination: File) {
        val parentDir = destination.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }
        FileInputStream(source).use { inputStream ->
            FileOutputStream(destination).use { outputStream ->
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
            }
        }
    }

    fun copyFileFromUri(context: Context, uri: Uri, destination: File) {
        val parentDir = destination.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(destination).use { outputStream ->
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
            }
        }
    }

    fun readTextFile(file: File): String {
        return file.readText(StandardCharsets.UTF_8)
    }

    fun readTextFileFromUri(context: Context, uri: Uri): String {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append('\n')
                }
            }
        }
        return stringBuilder.toString()
    }

    fun getTxtFilesInDirectory(directory: File): List<File> {
        return directory.listFiles { file ->
            file.isFile && file.name.endsWith(".txt", ignoreCase = true)
        }?.toList() ?: emptyList()
    }

    fun getIconFileInDirectory(directory: File): File? {
        val iconFile = File(directory, "icon.png")
        return if (iconFile.exists()) iconFile else null
    }

    fun createDirectoryIfNotExists(directory: File): Boolean {
        return if (directory.exists()) {
            directory.isDirectory
        } else {
            directory.mkdirs()
        }
    }

    fun deleteDirectory(directory: File): Boolean {
        if (!directory.exists()) return true
        
        if (directory.isDirectory) {
            val children = directory.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteDirectory(child)
                }
            }
        }
        
        return directory.delete()
    }
}