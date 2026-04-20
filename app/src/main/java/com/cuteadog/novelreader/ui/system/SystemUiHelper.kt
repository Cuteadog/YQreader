package com.cuteadog.novelreader.ui.system

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding

/**
 * 统一处理两种系统栏模式：
 *   - Edge-to-Edge：状态栏/导航栏透明，应用内容覆盖到屏幕边缘。顶部栏通过 insets 自动补 padding 避免被遮挡
 *   - Immersive Mode：用于阅读页，隐藏系统栏，边缘滑动临时显示
 */
object SystemUiHelper {

    /**
     * 让界面延伸到系统栏之下，并让状态栏 / 导航栏透明。
     * @param activity 目标 Activity
     * @param topInsetView 需要吸收状态栏高度作为顶部内边距的视图（通常是自定义标题栏）。传 null 则不设置
     * @param bottomInsetView 需要吸收导航栏高度作为底部内边距的视图。传 null 则不设置
     * @param referenceBgColor 用于决定状态栏图标颜色（深色图标 or 浅色图标）的参考底色
     */
    fun applyEdgeToEdge(
        activity: Activity,
        topInsetView: View? = null,
        bottomInsetView: View? = null,
        referenceBgColor: Int = Color.WHITE
    ) {
        val window = activity.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            window.navigationBarDividerColor = Color.TRANSPARENT
        }

        updateStatusBarIcons(activity, referenceBgColor)

        topInsetView?.let { view ->
            val initialTop = view.paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(top = initialTop + bars.top)
                insets
            }
            if (view.isAttachedToWindow) ViewCompat.requestApplyInsets(view)
        }

        bottomInsetView?.let { view ->
            val initialBottom = view.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(bottom = initialBottom + bars.bottom)
                insets
            }
            if (view.isAttachedToWindow) ViewCompat.requestApplyInsets(view)
        }
    }

    /** 根据背景色亮度选择状态栏图标色；背景较亮使用深色图标，较暗则用浅色图标 */
    fun updateStatusBarIcons(activity: Activity, bgColor: Int) {
        val window = activity.window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val lightBg = ColorUtils.calculateLuminance(bgColor) > 0.5
        controller.isAppearanceLightStatusBars = lightBg
        controller.isAppearanceLightNavigationBars = lightBg
    }

    /**
     * 进入沉浸模式：隐藏状态栏与导航栏，允许用户从边缘短暂唤出
     */
    fun enterImmersive(activity: Activity) {
        val window = activity.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** 暂时显示系统栏（例如菜单弹出时） */
    fun showSystemBars(activity: Activity) {
        val window = activity.window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    /** 再次隐藏系统栏 */
    fun hideSystemBars(activity: Activity) {
        val window = activity.window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
