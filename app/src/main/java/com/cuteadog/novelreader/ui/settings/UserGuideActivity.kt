package com.cuteadog.novelreader.ui.settings

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.ui.theme.ThemeManager

class UserGuideActivity : AppCompatActivity() {

    private val themeListener: (Int) -> Unit = { applyEmptyPageTheme() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_empty_page)

        applyEmptyPageEdgeToEdge()

        findViewById<TextView>(R.id.tv_title).setText(R.string.settings_user_guide)
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        applyEmptyPageTheme()
        if (settingsFollowsReaderTheme()) {
            ThemeManager.addListener(themeListener)
        }
    }

    override fun onResume() {
        super.onResume()
        applyEmptyPageTheme()
    }

    override fun onDestroy() {
        if (settingsFollowsReaderTheme()) {
            ThemeManager.removeListener(themeListener)
        }
        super.onDestroy()
    }
}
