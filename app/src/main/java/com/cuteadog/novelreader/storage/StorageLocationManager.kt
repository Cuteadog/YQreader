package com.cuteadog.novelreader.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.cuteadog.novelreader.MyApplication
import com.cuteadog.novelreader.data.dao.NovelDao
import com.cuteadog.novelreader.util.FileUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 统一管理"小说"和"分享背景"的目录位置。两种位置：
 *  - 内置：filesDir/novels、filesDir/shareBackgrounds
 *  - 外部：getExternalFilesDir(null)/novels、getExternalFilesDir(null)/shareBackgrounds
 *
 * 切换时负责物理迁移文件并改写数据库中保存的绝对路径。
 */
object StorageLocationManager {

    private val LOCATION_EXTERNAL_KEY = booleanPreferencesKey("storage_location_external")

    const val DIR_NOVELS = "novels"
    const val DIR_SHARE_BACKGROUNDS = "shareBackgrounds"

    @Volatile
    private var initialized = false

    @Volatile
    private var externalSelected: Boolean = false

    fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext as MyApplication
            try {
                runBlocking {
                    val prefs = app.dataStore.data.first()
                    externalSelected = prefs[LOCATION_EXTERNAL_KEY] ?: false
                }
            } catch (_: Exception) {
                externalSelected = false
            }
            initialized = true
        }
    }

    fun isExternal(): Boolean = externalSelected

    /** 当前小说储存目录（保证存在）。 */
    fun novelsDir(context: Context): File {
        val dir = File(rootDir(context), DIR_NOVELS)
        FileUtil.createDirectoryIfNotExists(dir)
        return dir
    }

    /** 当前分享背景储存目录（保证存在）。 */
    fun shareBackgroundsDir(context: Context): File {
        val dir = File(rootDir(context), DIR_SHARE_BACKGROUNDS)
        FileUtil.createDirectoryIfNotExists(dir)
        return dir
    }

    /** 给定位置下的小说目录（不创建）。 */
    fun novelsDirFor(context: Context, external: Boolean): File =
        File(rootDirFor(context, external), DIR_NOVELS)

    /** 给定位置下的分享背景目录（不创建）。 */
    fun shareBackgroundsDirFor(context: Context, external: Boolean): File =
        File(rootDirFor(context, external), DIR_SHARE_BACKGROUNDS)

    private fun rootDir(context: Context): File = rootDirFor(context, externalSelected)

    private fun rootDirFor(context: Context, external: Boolean): File {
        val ctx = context.applicationContext
        return if (external) {
            // /storage/emulated/0/Android/data/<package>/files
            ctx.getExternalFilesDir(null) ?: ctx.filesDir
        } else {
            ctx.filesDir
        }
    }

    /**
     * 迁移文件并改写 DataStore 内的绝对路径。失败时尽量回滚（删除已复制到目标的文件）。
     * 必须在 IO 线程调用。
     *
     * @return MigrationResult 描述结果
     */
    suspend fun migrateTo(context: Context, targetExternal: Boolean): MigrationResult {
        ensureInit(context)
        if (targetExternal == externalSelected) {
            return MigrationResult.NoChange
        }
        val ctx = context.applicationContext

        val srcNovels = novelsDirFor(ctx, externalSelected)
        val dstNovels = novelsDirFor(ctx, targetExternal)
        val srcBg = shareBackgroundsDirFor(ctx, externalSelected)
        val dstBg = shareBackgroundsDirFor(ctx, targetExternal)

        FileUtil.createDirectoryIfNotExists(dstNovels)
        FileUtil.createDirectoryIfNotExists(dstBg)

        // 1) 复制小说目录树
        val copiedNovelRoots = mutableListOf<File>()
        try {
            srcNovels.listFiles()?.forEach { child ->
                val target = File(dstNovels, child.name)
                copyRecursive(child, target)
                copiedNovelRoots += target
            }
            // 2) 复制分享背景目录
            srcBg.listFiles()?.forEach { child ->
                val target = File(dstBg, child.name)
                copyRecursive(child, target)
            }
        } catch (e: Exception) {
            // 回滚：删除目标已复制的小说文件
            copiedNovelRoots.forEach { FileUtil.deleteDirectory(it) }
            return MigrationResult.Failed(e.message ?: "未知错误")
        }

        // 3) 改写 DataStore 中的绝对路径
        val app = ctx as MyApplication
        try {
            val novelDao = NovelDao(app.dataStore)
            val srcPrefix = srcNovels.absolutePath
            val dstPrefix = dstNovels.absolutePath
            novelDao.rewritePathPrefix(srcPrefix, dstPrefix)
        } catch (e: Exception) {
            return MigrationResult.Failed("改写小说路径失败：${e.message}")
        }

        // 4) 持久化新位置
        try {
            app.dataStore.edit { it[LOCATION_EXTERNAL_KEY] = targetExternal }
            externalSelected = targetExternal
        } catch (e: Exception) {
            return MigrationResult.Failed("保存新位置失败：${e.message}")
        }

        // 5) 删除旧位置文件
        try {
            srcNovels.listFiles()?.forEach { FileUtil.deleteDirectory(it) }
            srcBg.listFiles()?.forEach { FileUtil.deleteDirectory(it) }
        } catch (_: Exception) { /* 不影响结果 */ }

        return MigrationResult.Success
    }

    private fun copyRecursive(src: File, dst: File) {
        if (src.isDirectory) {
            if (!dst.exists()) dst.mkdirs()
            src.listFiles()?.forEach { child ->
                copyRecursive(child, File(dst, child.name))
            }
        } else if (src.isFile) {
            FileUtil.copyFile(src, dst)
        }
    }

    sealed class MigrationResult {
        object Success : MigrationResult()
        object NoChange : MigrationResult()
        data class Failed(val message: String) : MigrationResult()
    }
}
