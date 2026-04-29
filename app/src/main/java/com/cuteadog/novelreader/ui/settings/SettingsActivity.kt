package com.cuteadog.novelreader.ui.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.cuteadog.novelreader.BuildConfig
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.storage.StorageLocationManager
import com.cuteadog.novelreader.ui.reader.applyDialogChromeTheme
import com.cuteadog.novelreader.ui.system.SystemUiHelper
import com.cuteadog.novelreader.ui.theme.ThemeManager
import com.cuteadog.novelreader.ui.theme.ThemePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    // personal 风味设置页固定为日间主题，不跟随阅读页主题变化
    private val followsReaderTheme: Boolean = settingsFollowsReaderTheme()

    private val themeListener: (Int) -> Unit = {
        applyTheme(settingsPalette())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Edge-to-Edge：标题栏吸收状态栏高度，滚动视图吸收底部导航栏高度
        SystemUiHelper.applyEdgeToEdge(
            activity = this,
            topInsetView = findViewById(R.id.title_bar),
            bottomInsetView = findViewById(R.id.scroll_view),
            referenceBgColor = settingsPalette().titleBarBg
        )

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // 一、导入设置
        findViewById<View>(R.id.row_storage_location)?.setOnClickListener {
            showStoragePicker()
        }
        findViewById<View>(R.id.row_import_mode)?.setOnClickListener {
            Toast.makeText(this, "功能即将推出", Toast.LENGTH_SHORT).show()
        }
        updateStorageValueLabel()

        // 二、阅读设置（仅 open 布局存在；personal 下 findViewById 返回 null）
        findViewById<View>(R.id.row_reading_mode)?.setOnClickListener {
            Toast.makeText(this, "功能即将推出", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.row_auto_switch_chapter)?.setOnClickListener {
            Toast.makeText(this, "功能即将推出", Toast.LENGTH_SHORT).show()
        }

        // 三、帮助
        findViewById<View>(R.id.row_user_guide)?.setOnClickListener {
            startActivity(Intent(this, UserGuideActivity::class.java))
        }
        findViewById<View>(R.id.row_about_us)?.setOnClickListener {
            startActivity(Intent(this, AboutUsActivity::class.java))
        }

        // 更新日志（open 专有，通过反射跳转）
        val changelogRow = findViewById<View>(R.id.row_changelog)
        if (changelogRow != null) {
            findViewById<TextView>(R.id.tv_changelog_version)?.text = BuildConfig.VERSION_NAME
            changelogRow.setOnClickListener {
                try {
                    val cls = Class.forName(
                        "com.cuteadog.novelreader.ui.settings.ChangelogActivity"
                    )
                    startActivity(Intent(this, cls))
                } catch (_: ClassNotFoundException) {
                    Toast.makeText(this, "功能即将推出", Toast.LENGTH_SHORT).show()
                }
            }
        }

        applyTheme(settingsPalette())
        if (followsReaderTheme) {
            ThemeManager.addListener(themeListener)
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme(settingsPalette())
    }

    override fun onDestroy() {
        if (followsReaderTheme) {
            ThemeManager.removeListener(themeListener)
        }
        super.onDestroy()
    }

    private fun applyTheme(palette: ThemePalette) {
        findViewById<View>(R.id.root_layout)?.setBackgroundColor(palette.pageBg)
        findViewById<View>(R.id.title_bar)?.setBackgroundColor(palette.titleBarBg)
        findViewById<TextView>(R.id.tv_title)?.setTextColor(palette.titleBarFg)
        findViewById<ImageButton>(R.id.btn_back)?.setColorFilter(palette.titleBarFg)
        SystemUiHelper.updateStatusBarIcons(this, palette.titleBarBg)

        findViewById<View>(R.id.content_container)?.setBackgroundColor(palette.pageBg)

        applySectionHeader(R.id.tv_section_import, palette)
        applySectionHeader(R.id.tv_section_help, palette)
        applySectionHeader(R.id.section_reading, palette)

        findViewById<View>(R.id.group_import)?.setBackgroundColor(palette.surfaceBg)
        findViewById<View>(R.id.group_help)?.setBackgroundColor(palette.surfaceBg)
        findViewById<View>(R.id.group_reading)?.setBackgroundColor(palette.surfaceBg)

        applyDivider(R.id.divider_import, palette)
        applyDivider(R.id.divider_help, palette)
        applyDivider(R.id.divider_reading, palette)
        applyDivider(R.id.divider_changelog, palette)

        applyRowLabel(R.id.tv_storage_location_label, palette)
        applyRowLabel(R.id.tv_import_mode_label, palette)
        applyRowLabel(R.id.tv_user_guide_label, palette)
        applyRowLabel(R.id.tv_about_us_label, palette)
        applyRowLabel(R.id.tv_reading_mode_label, palette)
        applyRowLabel(R.id.tv_auto_switch_label, palette)
        applyRowLabel(R.id.tv_changelog_label, palette)

        applyRowValue(R.id.tv_storage_location_value, palette)
        applyRowValue(R.id.tv_import_mode_value, palette)
        applyRowValue(R.id.tv_reading_mode_value, palette)
        applyRowValue(R.id.tv_changelog_version, palette)

        applyChevron(R.id.iv_storage_chevron, palette)
        applyChevron(R.id.iv_import_chevron, palette)
        applyChevron(R.id.iv_user_guide_chevron, palette)
        applyChevron(R.id.iv_about_us_chevron, palette)
        applyChevron(R.id.iv_reading_mode_chevron, palette)
        applyChevron(R.id.iv_changelog_chevron, palette)

        findViewById<SwitchCompat>(R.id.switch_auto_switch_chapter)?.let { sw ->
            val thumb = ColorStateList.valueOf(palette.buttonBg)
            val track = ColorStateList.valueOf(palette.textSecondary)
            sw.thumbTintList = thumb
            sw.trackTintList = track
        }
    }

    private fun applySectionHeader(id: Int, palette: ThemePalette) {
        findViewById<TextView>(id)?.setTextColor(palette.textSecondary)
    }

    private fun applyRowLabel(id: Int, palette: ThemePalette) {
        findViewById<TextView>(id)?.setTextColor(palette.textPrimary)
    }

    private fun applyRowValue(id: Int, palette: ThemePalette) {
        findViewById<TextView>(id)?.setTextColor(palette.textSecondary)
    }

    private fun applyChevron(id: Int, palette: ThemePalette) {
        findViewById<ImageView>(id)?.setColorFilter(palette.textSecondary)
    }

    private fun applyDivider(id: Int, palette: ThemePalette) {
        findViewById<View>(id)?.setBackgroundColor(palette.dividerColor)
    }

    private fun updateStorageValueLabel() {
        StorageLocationManager.ensureInit(this)
        val resId = if (StorageLocationManager.isExternal())
            R.string.settings_storage_external
        else
            R.string.settings_storage_internal
        findViewById<TextView>(R.id.tv_storage_location_value)?.setText(resId)
    }

    private fun showStoragePicker() {
        StorageLocationManager.ensureInit(this)
        val palette = settingsPalette()
        val currentIndex = if (StorageLocationManager.isExternal()) 1 else 0
        val items = arrayOf(
            getString(R.string.settings_storage_internal_desc),
            getString(R.string.settings_storage_external_desc)
        )

        val density = resources.displayMetrics.density
        val hPad = (24 * density).toInt()
        val vPad = (16 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val selectableBgRes = android.util.TypedValue().also {
            theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        val rows = mutableListOf<Pair<View, Int>>()
        for (i in items.indices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(hPad, vPad, hPad, vPad)
                isClickable = true
                isFocusable = true
                if (selectableBgRes != 0) setBackgroundResource(selectableBgRes)
            }
            val radio = View(this).apply {
                val size = (18 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (16 * density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke((2 * density).toInt(), palette.textSecondary)
                    setColor(if (i == currentIndex) palette.textSecondary else 0x00000000)
                }
            }
            val label = TextView(this).apply {
                text = items[i]
                setTextColor(palette.textPrimary)
                textSize = 16f
            }
            row.addView(radio)
            row.addView(label)
            container.addView(row)
            rows.add(row to i)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_storage_dialog_title)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .create()

        for ((row, which) in rows) {
            row.setOnClickListener {
                dialog.dismiss()
                if (which == currentIndex) {
                    Toast.makeText(
                        this,
                        R.string.settings_storage_no_change,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    performMigration(targetExternal = which == 1)
                }
            }
        }

        dialog.show()
        dialog.applyDialogChromeTheme(palette)
    }

    private fun performMigration(targetExternal: Boolean) {
        val palette = settingsPalette()
        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.settings_storage_migrating)
            .setCancelable(false)
            .create()
        progress.show()
        progress.applyDialogChromeTheme(palette)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                StorageLocationManager.migrateTo(this@SettingsActivity, targetExternal)
            }
            progress.dismiss()
            when (result) {
                is StorageLocationManager.MigrationResult.Success -> {
                    Toast.makeText(
                        this@SettingsActivity,
                        R.string.settings_storage_migrate_success,
                        Toast.LENGTH_SHORT
                    ).show()
                    updateStorageValueLabel()
                }
                is StorageLocationManager.MigrationResult.NoChange -> {
                    Toast.makeText(
                        this@SettingsActivity,
                        R.string.settings_storage_no_change,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is StorageLocationManager.MigrationResult.Failed -> {
                    val failedDialog = AlertDialog.Builder(this@SettingsActivity)
                        .setTitle(R.string.settings_storage_migrate_failed)
                        .setMessage(result.message)
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                    failedDialog.show()
                    failedDialog.applyDialogChromeTheme(palette)
                }
            }
        }
    }
}
