package com.cuteadog.novelreader.ui.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.ui.theme.ThemeManager
import com.cuteadog.novelreader.ui.theme.ThemePalette

class ChangelogActivity : AppCompatActivity() {

    private val themeListener: (Int) -> Unit = { applyChangelogTheme() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_empty_page)

        applyEmptyPageEdgeToEdge()

        findViewById<TextView>(R.id.tv_title).setText(R.string.settings_changelog)
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val container = findViewById<FrameLayout>(R.id.content_container)
        layoutInflater.inflate(R.layout.content_changelog, container, true)

        applyChangelogTheme()
        ThemeManager.addListener(themeListener)
    }

    override fun onResume() {
        super.onResume()
        applyChangelogTheme()
    }

    override fun onDestroy() {
        ThemeManager.removeListener(themeListener)
        super.onDestroy()
    }

    private fun applyChangelogTheme(palette: ThemePalette = ThemeManager.currentPalette()) {
        applyEmptyPageTheme(palette)
        findViewById<View>(R.id.changelog_container)?.setBackgroundColor(palette.pageBg)
        findViewById<View>(R.id.changelog_container)?.let { tintChangelog(it as ViewGroup, palette) }
    }

    private fun tintChangelog(root: ViewGroup, palette: ThemePalette) {
        for (i in 0 until root.childCount) {
            when (val child = root.getChildAt(i)) {
                is TextView -> {
                    val color = when (child.tag) {
                        "date" -> palette.textSecondary
                        else -> palette.textPrimary
                    }
                    child.setTextColor(color)
                }
                is ViewGroup -> tintChangelog(child, palette)
                else -> if (child.tag == "divider") child.setBackgroundColor(palette.dividerColor)
            }
        }
    }
}
