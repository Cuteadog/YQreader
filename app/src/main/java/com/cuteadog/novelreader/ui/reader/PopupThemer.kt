package com.cuteadog.novelreader.ui.reader

import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.cuteadog.novelreader.ui.theme.ThemePalette

internal fun applyPopupTheme(view: View, palette: ThemePalette) {
    (view.background as? GradientDrawable)?.setColor(palette.popupBg)
    tintPopupChildren(view, palette)
}

private fun tintPopupChildren(view: View, palette: ThemePalette) {
    when (view) {
        is TextView -> view.setTextColor(palette.popupFg)
        is ImageView -> view.setColorFilter(palette.popupFg)
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            tintPopupChildren(view.getChildAt(i), palette)
        }
    }
}

/**
 * 把 AlertDialog 的标题栏/按钮条/背景按主题着色。必须在 dialog.show() 之后调用。
 */
internal fun AlertDialog.applyDialogChromeTheme(palette: ThemePalette) {
    window?.setBackgroundDrawable(ColorDrawable(palette.pageBg))

    // 标题 - androidx.appcompat 里 AlertDialog 的标题 id 是 alertTitle
    findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(palette.textPrimary)
    findViewById<TextView>(android.R.id.title)?.setTextColor(palette.textPrimary)
    findViewById<TextView>(android.R.id.message)?.setTextColor(palette.textPrimary)

    // 标题下方分割线（系统绘制）颜色可通过 alertDialogStyle 控制，这里不处理

    getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(palette.buttonBg)
    getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(palette.textSecondary)
    getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(palette.textSecondary)
}
