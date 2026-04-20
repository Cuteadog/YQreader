package com.example.novelreader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.lifecycle.lifecycleScope
import com.example.novelreader.data.dao.NovelDao
import com.example.novelreader.data.model.Chapter
import com.example.novelreader.data.model.Novel
import com.example.novelreader.data.repository.NovelRepository
import com.example.novelreader.databinding.ActivityMainBinding
import com.example.novelreader.ui.library.LibraryFragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var novelRepository: NovelRepository
    private lateinit var novelsDir: String

    private val importReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                com.example.novelreader.service.ImportWorker.ACTION_IMPORT_PROGRESS -> {
                    // 可以在这里添加进度条显示逻辑
                }
                com.example.novelreader.service.ImportWorker.ACTION_IMPORT_COMPLETED -> {
                    Log.e("MainActivity", "收到导入完成广播")

                    // 从Intent中获取序列化的数据
                    val novelJson = intent.getStringExtra("novelJson")
                    val chaptersJson = intent.getStringExtra("chaptersJson")

                    Log.e("MainActivity", "novelJson: $novelJson")
                    Log.e("MainActivity", "chaptersJson: $chaptersJson")

                    if (novelJson != null && chaptersJson != null) {
                        try {
                            // 使用Json.decodeFromString解析数据
                            val novel = Json.decodeFromString<Novel>(novelJson)
                            val chapters = Json.decodeFromString<List<Chapter>>(chaptersJson)

                            Log.e("MainActivity", "解析成功，小说标题: ${novel.title}，章节数: ${chapters.size}")

                            // 保存到数据库
                            lifecycleScope.launch {
                                Log.e("MainActivity", "开始保存到数据库")
                                novelRepository.addNovel(novel, chapters)
                                Log.e("MainActivity", "保存到数据库成功")

                                Log.d("MainActivity", "保存后，所有小说: ${novelRepository.novels.first().map { it.title }}")
                                val chaptersOfNovel = novelRepository.getChapters(novel.id).first()
                                Log.d("MainActivity", "保存后，章节数: ${chaptersOfNovel.size}")

                                // 显示详细的导入成功信息
                                showImportSuccessDialog(novel, chapters.size)
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "解析小说数据失败", e)
                            showImportErrorDialog("解析小说数据失败：${e.message}")
                        }
                    } else {
                        Log.e("MainActivity", "novelJson或chaptersJson为null")
                        showImportErrorDialog("导入数据不完整，请重试")
                    }
                }
                com.example.novelreader.service.ImportWorker.ACTION_IMPORT_ERROR -> {
                    val error = intent.getStringExtra(com.example.novelreader.service.ImportWorker.EXTRA_ERROR)
                    showImportErrorDialog(error ?: "导入失败，请检查文件夹内容")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化小说目录
        novelsDir = "${filesDir}/novels"
        val novelsDirFile = java.io.File(novelsDir)
        if (!novelsDirFile.exists()) {
            novelsDirFile.mkdirs()
        }

        // 初始化数据存储(使用 Application 的单例 DataStore)
        val dataStore = (application as MyApplication).dataStore
        val novelDao = NovelDao(dataStore)
        novelRepository = NovelRepository(novelDao)

        // 设置Fragment
        if (savedInstanceState == null) {
            val libraryFragment = LibraryFragment(novelRepository, novelsDir)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, libraryFragment)
                .commit()
        }

        // 注册广播接收器
        LocalBroadcastManager.getInstance(this).registerReceiver(
            importReceiver,
            IntentFilter().apply {
                addAction(com.example.novelreader.service.ImportWorker.ACTION_IMPORT_STARTED)
                addAction(com.example.novelreader.service.ImportWorker.ACTION_IMPORT_PROGRESS)
                addAction(com.example.novelreader.service.ImportWorker.ACTION_IMPORT_COMPLETED)
                addAction(com.example.novelreader.service.ImportWorker.ACTION_IMPORT_ERROR)
            }
        )

        // 显示导入提示
        showImportTip()
    }

    private fun showImportTip() {
        // 检查是否首次启动
        val sharedPreferences = getSharedPreferences("novel_reader_prefs", MODE_PRIVATE)
        val isFirstLaunch = sharedPreferences.getBoolean("is_first_launch", true)

        if (isFirstLaunch) {
            AlertDialog.Builder(this)
                .setTitle("📚 欢迎使用小说阅读器")
                .setMessage("""
您可以通过以下方式导入小说：

1. 点击底部的"导入"按钮
2. 选择包含.txt文件的小说文件夹
3. 等待导入完成后即可开始阅读

💡 提示：
• 支持多章节导入
• 自动记忆阅读进度
• 支持自定义阅读设置

祝您阅读愉快！
                """.trimIndent())
                .setPositiveButton("我知道了") { _, _ ->
                    sharedPreferences.edit { putBoolean("is_first_launch", false) }
                }
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 注销广播接收器
        LocalBroadcastManager.getInstance(this).unregisterReceiver(importReceiver)
    }

    private fun showImportSuccessDialog(novel: Novel, chapterCount: Int) {
        // 构建详细的成功信息
        val successMessage = """
📚 小说导入成功！

📖 小说信息：
• 标题：${novel.title}
• 章节数：$chapterCount 章
• 状态：${if (novel.coverPath != null) "已找到封面" else "使用默认封面"}
• 导入时间：${getCurrentTime()}

🎯 下一步：
• 点击小说开始阅读
• 支持章节跳转和阅读进度记忆
• 可在设置中调整阅读偏好

祝您阅读愉快！
        """.trimIndent()

        android.app.AlertDialog.Builder(this)
            .setTitle("🎉 导入成功")
            .setMessage(successMessage)
            .setPositiveButton("开始阅读") { _, _ ->
                // 直接跳转到阅读器
                startActivity(
                    Intent(this, com.example.novelreader.ui.reader.ReaderActivity::class.java).apply {
                        putExtra(com.example.novelreader.ui.reader.ReaderActivity.EXTRA_NOVEL, novel)
                    }
                )
            }
            .setNegativeButton("稍后阅读", null)
            .show()
    }

    private fun showImportErrorDialog(errorMessage: String) {
        // 分析错误类型，提供更具体的建议
        val suggestion = when {
            errorMessage.contains("空文件夹") -> "请选择包含.txt文件的小说文件夹"
            errorMessage.contains("没有找到.txt文件") -> "请确保文件夹中包含.txt格式的小说章节文件"
            errorMessage.contains("无法获取文件夹名称") -> "请选择一个有效的文件夹"
            errorMessage.contains("无法创建小说目录") -> "请检查应用存储权限"
            errorMessage.contains("解析") -> "小说文件格式可能有问题，请检查文件编码"
            else -> "请检查文件夹结构是否正确"
        }

        // 提供具体的解决方案
        val solution = when {
            errorMessage.contains("空文件夹") || errorMessage.contains("没有找到.txt文件") ->
                "解决方案：\n1. 创建包含.txt文件的文件夹\n2. 将小说章节保存为.txt格式\n3. 确保文件编码为UTF-8"
            errorMessage.contains("权限") ->
                "解决方案：\n1. 检查应用存储权限\n2. 重启应用后重试\n3. 清理应用缓存"
            else ->
                "解决方案：\n1. 检查文件夹结构\n2. 确保文件格式正确\n3. 尝试重新导入"
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("❌ 导入失败")
            .setMessage("""
$errorMessage

💡 建议：$suggestion

🔧 $solution

📝 提示：如需帮助，请点击"查看导入指南"
            """.trimIndent())
            .setPositiveButton("重试") { _, _ ->
                // 获取 LibraryFragment 实例并触发重试
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (fragment is LibraryFragment) {
                    fragment.retryImport()
                } else {
                    // 降级方案：通知用户手动点击导入按钮
                    Toast.makeText(this, "请手动点击底部“导入”按钮重试", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("查看导入指南") { _, _ ->
                showImportGuide()
            }
            .show()
    }

    private fun getCurrentTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    private fun showImportGuide() {
        android.app.AlertDialog.Builder(this)
            .setTitle("小说导入完全指南")
            .setMessage("""
📚 小说导入指南

🔍 文件夹要求：
• 必须包含至少一个.txt格式的章节文件
• 支持添加icon.png作为封面图片（可选）
• 文件夹名称将作为小说标题

📁 正确的文件夹结构：
小说名称/
├── 第一章.txt
├── 第二章.txt
├── 第三章.txt
└── icon.png (可选)

🚫 不支持的情况：
• 空文件夹
• 没有.txt文件的文件夹
• 压缩文件（.zip, .rar等）
• 其他格式的文档文件
• 文件夹套文件夹


💡 导入技巧：
1. 确保章节文件按顺序命名
2. 使用UTF-8编码的.txt文件获得最佳兼容性
3. 封面图片建议使用500x700像素的图片

❓ 常见问题：
• Q: 为什么导入失败？
  A: 请检查文件夹是否包含.txt文件
  
• Q: 如何批量导入多个小说？
  A: 目前需要一个一个文件夹导入
  
• Q: 支持哪些编码格式？
  A: 推荐使用UTF-8编码的.txt文件

如果问题仍然存在，请尝试重新整理文件夹结构后再次导入。
            """.trimIndent())
            .setPositiveButton("我知道了", null)
            .show()
    }
}