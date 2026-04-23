package com.cuteadog.novelreader.ui.theme

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.cuteadog.novelreader.MyApplication
import com.cuteadog.novelreader.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object ThemeManager {
    const val THEME_DAY = 0
    const val THEME_EYE = 1
    const val THEME_NIGHT = 2

    val THEME_KEY = intPreferencesKey("theme")

    @Volatile
    private var cachedTheme: Int = THEME_DAY

    @Volatile
    private var initialized = false

    private val listeners = mutableListOf<(Int) -> Unit>()
    private val paletteTickListeners = mutableListOf<(ThemePalette) -> Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 主题切换动画时长，与 MainActivity animateBackground 原有时长保持一致 */
    const val THEME_TRANSITION_DURATION_MS = 400L

    fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext as MyApplication
            try {
                runBlocking {
                    val prefs = app.dataStore.data.first()
                    cachedTheme = prefs[THEME_KEY] ?: THEME_DAY
                }
            } catch (_: Exception) {
                cachedTheme = THEME_DAY
            }
            initialized = true
        }
    }

    fun current(): Int = cachedTheme

    fun currentPalette(): ThemePalette = ThemePalette.forTheme(cachedTheme)

    fun cycleAndSave(context: Context): Int {
        val next = when (cachedTheme) {
            THEME_DAY -> THEME_EYE
            THEME_EYE -> THEME_NIGHT
            else -> THEME_DAY
        }
        val from = ThemePalette.forTheme(cachedTheme)
        val to = ThemePalette.forTheme(next)
        val hasTickSubscribers = synchronized(paletteTickListeners) { paletteTickListeners.isNotEmpty() }
        if (hasTickSubscribers) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = THEME_TRANSITION_DURATION_MS
                addUpdateListener {
                    val f = it.animatedValue as Float
                    notifyTick(ThemePalette.blend(from, to, f))
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        setAndSave(context, next)
                    }
                })
                start()
            }
        } else {
            setAndSave(context, next)
        }
        return next
    }

    fun setAndSave(context: Context, theme: Int) {
        cachedTheme = theme
        val app = context.applicationContext as MyApplication
        scope.launch {
            app.dataStore.edit { it[THEME_KEY] = theme }
        }
        notifyListeners()
    }

    fun addListener(listener: (Int) -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: (Int) -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun addPaletteTickListener(listener: (ThemePalette) -> Unit) {
        synchronized(paletteTickListeners) { paletteTickListeners.add(listener) }
    }

    fun removePaletteTickListener(listener: (ThemePalette) -> Unit) {
        synchronized(paletteTickListeners) { paletteTickListeners.remove(listener) }
    }

    private fun notifyListeners() {
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it(cachedTheme) }
    }

    private fun notifyTick(palette: ThemePalette) {
        val snapshot = synchronized(paletteTickListeners) { paletteTickListeners.toList() }
        snapshot.forEach { it(palette) }
    }
}

data class ThemePalette(
    val theme: Int,
    val pageBg: Int,
    val surfaceBg: Int,
    val groupBg: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val titleBarBg: Int,
    val titleBarFg: Int,
    val titleBarFgMuted: Int,
    val dividerColor: Int,
    val buttonBg: Int,
    val buttonFg: Int,
    val popupBg: Int,
    val popupFg: Int,
    val iconRes: Int
) {
    companion object {
        private val argbEvaluator = ArgbEvaluator()

        private fun lerpColor(from: Int, to: Int, fraction: Float): Int =
            argbEvaluator.evaluate(fraction, from, to) as Int

        /** 在两个主题间按 [fraction]∈[0,1] 做颜色插值；非颜色字段（如 iconRes、theme）直接取终值。 */
        fun blend(from: ThemePalette, to: ThemePalette, fraction: Float): ThemePalette = ThemePalette(
            theme = if (fraction >= 1f) to.theme else from.theme,
            pageBg = lerpColor(from.pageBg, to.pageBg, fraction),
            surfaceBg = lerpColor(from.surfaceBg, to.surfaceBg, fraction),
            groupBg = lerpColor(from.groupBg, to.groupBg, fraction),
            textPrimary = lerpColor(from.textPrimary, to.textPrimary, fraction),
            textSecondary = lerpColor(from.textSecondary, to.textSecondary, fraction),
            titleBarBg = lerpColor(from.titleBarBg, to.titleBarBg, fraction),
            titleBarFg = lerpColor(from.titleBarFg, to.titleBarFg, fraction),
            titleBarFgMuted = lerpColor(from.titleBarFgMuted, to.titleBarFgMuted, fraction),
            dividerColor = lerpColor(from.dividerColor, to.dividerColor, fraction),
            buttonBg = lerpColor(from.buttonBg, to.buttonBg, fraction),
            buttonFg = lerpColor(from.buttonFg, to.buttonFg, fraction),
            popupBg = lerpColor(from.popupBg, to.popupBg, fraction),
            popupFg = lerpColor(from.popupFg, to.popupFg, fraction),
            iconRes = if (fraction >= 0.5f) to.iconRes else from.iconRes
        )

        fun forTheme(theme: Int): ThemePalette = when (theme) {
            ThemeManager.THEME_EYE -> ThemePalette(
                theme = theme,
                pageBg = Color.parseColor("#FFF5E6"),
                surfaceBg = Color.parseColor("#FFF5E6"),
                groupBg = Color.parseColor("#F0E6D2"),
                textPrimary = Color.parseColor("#000000"),
                textSecondary = Color.parseColor("#8C7A5E"),
                titleBarBg = Color.parseColor("#FFDEAD"),
                titleBarFg = Color.parseColor("#000000"),
                titleBarFgMuted = Color.parseColor("#80000000"),
                dividerColor = Color.parseColor("#E0D5C0"),
                buttonBg = Color.parseColor("#FFDEAD"),
                buttonFg = Color.parseColor("#000000"),
                popupBg = Color.parseColor("#DD5C4A2E"),
                popupFg = Color.parseColor("#FFFFFF"),
                iconRes = R.drawable.ic_theme_eye
            )
            ThemeManager.THEME_NIGHT -> ThemePalette(
                theme = theme,
                pageBg = Color.parseColor("#202020"),
                surfaceBg = Color.parseColor("#2C2C2C"),
                groupBg = Color.parseColor("#1A1A1A"),
                textPrimary = Color.parseColor("#80808D"),
                textSecondary = Color.parseColor("#606067"),
                titleBarBg = Color.parseColor("#445566"),
                titleBarFg = Color.parseColor("#2196F3"),
                titleBarFgMuted = Color.parseColor("#802196F3"),
                dividerColor = Color.parseColor("#404040"),
                buttonBg = Color.parseColor("#445566"),
                buttonFg = Color.parseColor("#2196F3"),
                popupBg = Color.parseColor("#DD1A1A2E"),
                popupFg = Color.parseColor("#FFFFFF"),
                iconRes = R.drawable.ic_theme_night
            )
            else -> ThemePalette(
                theme = theme,
                pageBg = Color.parseColor("#FFFFFF"),
                surfaceBg = Color.parseColor("#FFFFFF"),
                groupBg = Color.parseColor("#F5F5F5"),
                textPrimary = Color.parseColor("#212121"),
                textSecondary = Color.parseColor("#757575"),
                titleBarBg = Color.parseColor("#4DABF5"),   // equal to #CC2196F3
                titleBarFg = Color.parseColor("#FFFFFF"),
                titleBarFgMuted = Color.parseColor("#B3FFFFFF"),
                dividerColor = Color.parseColor("#E0E0E0"),
                buttonBg = Color.parseColor("#4DABF5"),
                buttonFg = Color.parseColor("#FFFFFF"),
                popupBg = Color.parseColor("#DD1A1A2E"),
                popupFg = Color.parseColor("#FFFFFF"),
                iconRes = R.drawable.ic_theme_day
            )
        }
    }
}