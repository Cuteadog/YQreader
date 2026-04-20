package com.cuteadog.novelreader.ui.settings

import android.app.Activity
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.cuteadog.novelreader.BuildConfig
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.ui.system.SystemUiHelper
import com.cuteadog.novelreader.ui.theme.ThemeManager
import com.cuteadog.novelreader.ui.theme.ThemePalette

internal fun settingsPalette(): ThemePalette =
    if (BuildConfig.FLAVOR == "personal") ThemePalette.forTheme(ThemeManager.THEME_DAY)
    else ThemeManager.currentPalette()

internal fun settingsFollowsReaderTheme(): Boolean = BuildConfig.FLAVOR != "personal"

internal fun Activity.applyEmptyPageEdgeToEdge() {
    SystemUiHelper.applyEdgeToEdge(
        activity = this,
        topInsetView = findViewById(R.id.title_bar),
        bottomInsetView = findViewById(R.id.content_container),
        referenceBgColor = settingsPalette().titleBarBg
    )
}

internal fun Activity.applyEmptyPageTheme(palette: ThemePalette = settingsPalette()) {
    findViewById<View>(R.id.root_layout)?.setBackgroundColor(palette.pageBg)
    findViewById<View>(R.id.title_bar)?.setBackgroundColor(palette.titleBarBg)
    findViewById<TextView>(R.id.tv_title)?.setTextColor(palette.titleBarFg)
    findViewById<ImageButton>(R.id.btn_back)?.setColorFilter(palette.titleBarFg)
    findViewById<View>(R.id.content_container)?.setBackgroundColor(palette.pageBg)
    SystemUiHelper.updateStatusBarIcons(this, palette.titleBarBg)
}
