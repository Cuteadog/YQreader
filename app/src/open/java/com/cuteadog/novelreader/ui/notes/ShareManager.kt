package com.cuteadog.novelreader.ui.notes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cuteadog.novelreader.R
import com.cuteadog.novelreader.data.model.Highlight
import com.cuteadog.novelreader.storage.StorageLocationManager
import com.cuteadog.novelreader.ui.theme.ThemeManager
import com.cuteadog.novelreader.ui.theme.ThemePalette
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 笔记详情分享。提供两种分享方式：
 *  - 文本：按 "『高亮』 / 随笔 / ——《书名》" 格式
 *  - 图片：以卡片形式渲染（支持模板背景、隐藏想法开关，可保存 / 分享）
 */
class ShareManager(
    private val activity: AppCompatActivity,
    private val highlight: Highlight,
    private val novelTitle: String
) {

    /** 一个模板条目（主题色 / 内置资源 / 用户导入的本地文件 / 导入入口） */
    private sealed class Template {
        object ThemeDefault : Template()
        data class Asset(val assetPath: String, val label: String) : Template()
        /** 用户导入的本地文件。label 形如"本地1" */
        data class UserImage(val file: File, val label: String) : Template()
        object Import : Template()
    }

    private val presetTemplates: List<Template.Asset> = listOf(
        Template.Asset("share_bg/bg_01_blue_gradient.png", "清爽"),
        Template.Asset("share_bg/bg_02_blue_green_flowers.jpg", "春花"),
        Template.Asset("share_bg/bg_03_green_summer_a.png", "夏绿"),
        Template.Asset("share_bg/bg_04_green_summer_b.png", "盛夏"),
        Template.Asset("share_bg/bg_05_stripe_shading.png", "条纹"),
        Template.Asset("share_bg/bg_06_light_blue_striped.png", "蓝条"),
        Template.Asset("share_bg/bg_07_autumn.png", "秋意"),
        Template.Asset("share_bg/bg_08_winter_snow.png", "冬雪"),
        Template.Asset("share_bg/bg_09_yellow_flower.png", "暖花")
    )

    // 运行期状态：下标 0 固定为"主题默认"；末尾为"导入"入口；中间为预设 + 用户导入的图片
    private val templates: MutableList<Template> = mutableListOf<Template>().apply {
        add(Template.ThemeDefault)
        addAll(presetTemplates)
        addAll(loadUserTemplatesFromDisk())
        add(Template.Import)
    }
    private var selectedIndex: Int = 0

    /** 扫描分享背景目录，按"本地N"顺序加载已持久化的导入图。 */
    private fun loadUserTemplatesFromDisk(): List<Template.UserImage> {
        val dir = StorageLocationManager.shareBackgroundsDir(activity)
        val files = dir.listFiles { f ->
            f.isFile && LOCAL_FILE_REGEX.matches(f.name)
        } ?: return emptyList()
        return files.sortedBy { extractLocalIndex(it.name) }
            .map { Template.UserImage(it, it.nameWithoutExtension) }
    }

    private fun extractLocalIndex(fileName: String): Int {
        val match = LOCAL_FILE_REGEX.matchEntire(fileName) ?: return Int.MAX_VALUE
        return match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun nextLocalIndex(): Int {
        val dir = StorageLocationManager.shareBackgroundsDir(activity)
        val used = (dir.listFiles { f -> LOCAL_FILE_REGEX.matches(f.name) } ?: emptyArray())
            .mapNotNull {
                LOCAL_FILE_REGEX.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
            }
            .toSet()
        var i = 1
        while (i in used) i++
        return i
    }
    private var hideThoughts: Boolean = false
    private var shareCardView: View? = null
    private var templateSection: View? = null
    private var templateRow: LinearLayout? = null

    // ---- Entry ----

    fun showShareSheet() {
        val sheet = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_share, null)
        sheet.setContentView(view)

        val palette = ThemeManager.currentPalette()
        applyShareSheetTheme(view, palette)

        view.findViewById<TextView>(R.id.btn_share_as_text).setOnClickListener {
            sheet.dismiss()
            shareAsText()
        }
        view.findViewById<TextView>(R.id.btn_share_as_image).setOnClickListener {
            sheet.dismiss()
            showImagePreviewDialog()
        }
        view.findViewById<TextView>(R.id.btn_share_cancel).setOnClickListener {
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun applyShareSheetTheme(view: View, palette: ThemePalette) {
        view.findViewById<View>(R.id.share_sheet_root)?.setBackgroundColor(palette.pageBg)
        view.findViewById<TextView>(R.id.tv_share_sheet_title)?.setTextColor(palette.textSecondary)
        view.findViewById<View>(R.id.share_sheet_divider_top)?.setBackgroundColor(palette.dividerColor)
        view.findViewById<View>(R.id.share_sheet_divider_bottom)?.setBackgroundColor(palette.dividerColor)
        view.findViewById<TextView>(R.id.btn_share_as_text)?.setTextColor(palette.textPrimary)
        view.findViewById<TextView>(R.id.btn_share_as_image)?.setTextColor(palette.textPrimary)
        view.findViewById<TextView>(R.id.btn_share_cancel)?.setTextColor(palette.textSecondary)
        (view.parent as? View)?.setBackgroundColor(palette.pageBg)
    }

    // ---- Text Share ----

    private fun shareAsText() {
        val text = buildShareText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_title)))
    }

    private fun buildShareText(): String {
        val sb = StringBuilder()
        sb.append("『").append(highlight.selectedText.trim()).append("』")
        if (highlight.isNote) {
            sb.append("\n\n").append(highlight.noteContent.trim())
        }
        sb.append("\n\n——《").append(novelTitle).append("》")
        return sb.toString()
    }

    // ---- Image Share ----

    private fun showImagePreviewDialog() {
        val dialog = android.app.Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_share_image, null)
        dialog.setContentView(view)

        val window = dialog.window
        if (window != null) {
            val lp = window.attributes
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = (activity.resources.displayMetrics.heightPixels * 0.90f).toInt()
            window.attributes = lp
            window.setGravity(android.view.Gravity.CENTER)
        }

        shareCardView = view.findViewById(R.id.share_card)
        templateSection = view.findViewById(R.id.share_template_section)
        templateRow = view.findViewById(R.id.share_template_row)

        val palette = ThemeManager.currentPalette()
        applyImageDialogTheme(view, palette)

        // 初始内容（使用当前主题色）
        renderCardContent(palette)
        applyBackgroundTemplate(templates[selectedIndex])

        // 模板按钮
        view.findViewById<TextView>(R.id.btn_image_template).setOnClickListener {
            val section = templateSection ?: return@setOnClickListener
            section.visibility = if (section.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (section.visibility == View.VISIBLE && templateRow?.childCount == 0) {
                populateTemplateRow()
            }
        }

        view.findViewById<TextView>(R.id.btn_image_save).setOnClickListener {
            saveImage()
        }
        view.findViewById<TextView>(R.id.btn_image_share).setOnClickListener {
            shareImage()
        }
        view.findViewById<TextView>(R.id.btn_image_cancel).setOnClickListener {
            dialog.dismiss()
        }

        val hideSwitch = view.findViewById<SwitchCompat>(R.id.switch_hide_thoughts)
        hideSwitch.isChecked = hideThoughts
        hideSwitch.setOnCheckedChangeListener { _, checked ->
            hideThoughts = checked
            // 重新渲染文字（当前是主题默认还是图片背景，按当前模板决定）
            if (templates[selectedIndex] is Template.ThemeDefault) {
                renderCardContent(ThemeManager.currentPalette())
            } else {
                renderCardContentForImageBg()
            }
        }

        dialog.show()
    }

    private fun applyImageDialogTheme(view: View, palette: ThemePalette) {
        view.findViewById<View>(R.id.share_image_root)?.setBackgroundColor(palette.pageBg)
        view.findViewById<View>(R.id.share_preview_container)?.setBackgroundColor(palette.pageBg)
        view.findViewById<View>(R.id.share_template_section)?.setBackgroundColor(palette.groupBg)
        view.findViewById<View>(R.id.share_template_divider_top)?.setBackgroundColor(palette.dividerColor)
        view.findViewById<View>(R.id.share_action_divider)?.setBackgroundColor(palette.dividerColor)
        view.findViewById<View>(R.id.share_action_bar)?.setBackgroundColor(palette.surfaceBg)
        view.findViewById<TextView>(R.id.tv_hide_thoughts_label)?.setTextColor(palette.textPrimary)

        listOf(R.id.btn_image_template, R.id.btn_image_save, R.id.btn_image_share).forEach { id ->
            view.findViewById<TextView>(id)?.setTextColor(palette.textPrimary)
        }
        view.findViewById<TextView>(R.id.btn_image_cancel)?.setTextColor(palette.textSecondary)
    }

    /** 在当前主题下绘制卡片文字部分（不含背景）。 */
    private fun renderCardContent(palette: ThemePalette) {
        val root = shareCardView ?: return
        val tvDateTime = root.findViewById<TextView>(R.id.tv_card_datetime)
        val tvNote = root.findViewById<TextView>(R.id.tv_card_note)
        val tvQuote = root.findViewById<TextView>(R.id.tv_card_quote_mark)
        val tvHighlight = root.findViewById<TextView>(R.id.tv_card_highlight)
        val tvBook = root.findViewById<TextView>(R.id.tv_card_book)
        val divider = root.findViewById<View>(R.id.share_card_divider)
        val tvFooter = root.findViewById<TextView>(R.id.tv_card_footer)

        val showNote = !hideThoughts && highlight.isNote
        val labelRes = if (showNote) R.string.share_written_on else R.string.share_recorded_on
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        tvDateTime.text = "${activity.getString(labelRes)} ${dateFmt.format(Date(highlight.timestamp))}"

        if (showNote) {
            tvNote.text = highlight.noteContent.trim()
            tvNote.visibility = View.VISIBLE
        } else {
            tvNote.visibility = View.GONE
        }

        tvHighlight.text = highlight.selectedText.trim()
        tvBook.text = "——《$novelTitle》"

        // 主题色：浅底用深字，深底用浅字（以 pageBg 亮度判断）
        val isDark = isColorDark(palette.pageBg)
        val primary = if (isDark) 0xFFF0F0F0.toInt() else 0xFF222222.toInt()
        val secondary = if (isDark) 0xFFB0B0B0.toInt() else 0xFF666666.toInt()
        val dividerColor = if (isDark) 0x55FFFFFF.toInt() else 0x55000000.toInt()

        tvDateTime.setTextColor(secondary)
        tvNote.setTextColor(primary)
        tvQuote.setTextColor(primary)
        tvHighlight.setTextColor(primary)
        tvBook.setTextColor(primary)
        divider.setBackgroundColor(dividerColor)
        tvFooter.setTextColor(secondary)
    }

    private fun isColorDark(color: Int): Boolean {
        val r = (color shr 16) and 0xff
        val g = (color shr 8) and 0xff
        val b = color and 0xff
        val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return lum < 0.5
    }

    /** 把目标 Drawable 贴到卡片 FrameLayout 的背景上。 */
    private fun applyBackgroundTemplate(template: Template) {
        val root = shareCardView ?: return
        // ThemeDefault：直接用当前主题底色
        if (template is Template.ThemeDefault) {
            root.background = android.graphics.drawable.ColorDrawable(ThemeManager.currentPalette().pageBg)
            renderCardContent(ThemeManager.currentPalette())
            return
        }
        val drawable: Drawable? = when (template) {
            is Template.Asset -> loadAssetAsTilingDrawable(template.assetPath, root.width)
            is Template.UserImage -> loadFileAsTilingDrawable(template.file, root.width)
            else -> null
        }
        if (drawable != null) {
            root.background = drawable
        }
        // 使用非默认背景时，文字按浅底（深字）渲染——与主题脱钩
        renderCardContentForImageBg()
        // 若卡片尚未测量，延后到布局完成再贴一次（这样能知道 width）
        if (root.width == 0) {
            root.post {
                val d = when (template) {
                    is Template.Asset -> loadAssetAsTilingDrawable(template.assetPath, root.width)
                    is Template.UserImage -> loadFileAsTilingDrawable(template.file, root.width)
                    else -> null
                }
                if (d != null) root.background = d
            }
        }
    }

    /** 图片背景下用固定的浅底/深字配色渲染卡片文字。 */
    private fun renderCardContentForImageBg() {
        val root = shareCardView ?: return
        val tvDateTime = root.findViewById<TextView>(R.id.tv_card_datetime)
        val tvNote = root.findViewById<TextView>(R.id.tv_card_note)
        val tvQuote = root.findViewById<TextView>(R.id.tv_card_quote_mark)
        val tvHighlight = root.findViewById<TextView>(R.id.tv_card_highlight)
        val tvBook = root.findViewById<TextView>(R.id.tv_card_book)
        val divider = root.findViewById<View>(R.id.share_card_divider)
        val tvFooter = root.findViewById<TextView>(R.id.tv_card_footer)

        val showNote = !hideThoughts && highlight.isNote
        val labelRes = if (showNote) R.string.share_written_on else R.string.share_recorded_on
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        tvDateTime.text = "${activity.getString(labelRes)} ${dateFmt.format(Date(highlight.timestamp))}"
        if (showNote) {
            tvNote.text = highlight.noteContent.trim()
            tvNote.visibility = View.VISIBLE
        } else {
            tvNote.visibility = View.GONE
        }
        tvHighlight.text = highlight.selectedText.trim()
        tvBook.text = "——《$novelTitle》"

        // 图片背景 → 卡片用浅底深字的固定风格，保证对任何底图都能读清
        val primary = 0xFF222222.toInt()
        val secondary = 0xFF555555.toInt()
        val dividerColor = 0x55000000.toInt()
        tvDateTime.setTextColor(secondary)
        tvNote.setTextColor(primary)
        tvQuote.setTextColor(primary)
        tvHighlight.setTextColor(primary)
        tvBook.setTextColor(primary)
        divider.setBackgroundColor(dividerColor)
        tvFooter.setTextColor(secondary)
    }

    /**
     * 按目标宽度等比缩放位图，并用 MIRROR 方式沿 Y 轴平铺 —— 这样卡片变高时
     * 背景可以"自动填充"而无需拉伸，也避免 centerCrop 的放大裁切。
     */
    private fun loadAssetAsTilingDrawable(assetPath: String, viewWidth: Int): BitmapDrawable? {
        return try {
            activity.assets.open(assetPath).use { stream ->
                val bmp = BitmapFactory.decodeStream(stream) ?: return null
                makeTilingDrawable(bmp, viewWidth)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadFileAsTilingDrawable(file: File, viewWidth: Int): BitmapDrawable? {
        return try {
            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            makeTilingDrawable(bmp, viewWidth)
        } catch (e: Exception) {
            null
        }
    }

    private fun makeTilingDrawable(bmp: Bitmap, targetWidth: Int): BitmapDrawable {
        val width = if (targetWidth > 0) targetWidth else bmp.width
        val scale = width.toFloat() / bmp.width
        val height = (bmp.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale == 1f) bmp else Bitmap.createScaledBitmap(bmp, width, height, true)
        return BitmapDrawable(activity.resources, scaled).apply {
            setTileModeXY(Shader.TileMode.CLAMP, Shader.TileMode.MIRROR)
        }
    }

    // ---- Template Row ----

    private fun populateTemplateRow() {
        val row = templateRow ?: return
        row.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        templates.forEachIndexed { index, template ->
            val itemView = inflater.inflate(R.layout.item_share_template, row, false)
            bindTemplateItem(itemView, template, index)
            row.addView(itemView)
        }
    }

    private fun bindTemplateItem(itemView: View, template: Template, index: Int) {
        val thumb = itemView.findViewById<ImageView>(R.id.iv_template_thumb)
        val plus = itemView.findViewById<TextView>(R.id.tv_template_plus)
        val border = itemView.findViewById<View>(R.id.v_template_border)
        val label = itemView.findViewById<TextView>(R.id.tv_template_label)
        val removeBtn = itemView.findViewById<TextView>(R.id.btn_template_remove)

        when (template) {
            is Template.ThemeDefault -> {
                thumb.setImageDrawable(null)
                thumb.setBackgroundColor(ThemeManager.currentPalette().pageBg)
                plus.visibility = View.GONE
                removeBtn.visibility = View.GONE
                label.text = "默认"
            }
            is Template.Asset -> {
                thumb.setImageBitmap(decodeAssetThumb(template.assetPath))
                thumb.background = null
                plus.visibility = View.GONE
                removeBtn.visibility = View.GONE
                label.text = template.label
            }
            is Template.UserImage -> {
                thumb.setImageBitmap(decodeFileThumb(template.file))
                thumb.background = null
                plus.visibility = View.GONE
                removeBtn.visibility = View.VISIBLE
                label.text = template.label
            }
            is Template.Import -> {
                thumb.setImageDrawable(null)
                thumb.setBackgroundColor(0xFFEEEEEE.toInt())
                plus.visibility = View.VISIBLE
                removeBtn.visibility = View.GONE
                label.text = "导入"
            }
        }

        val palette = ThemeManager.currentPalette()
        label.setTextColor(palette.textPrimary)

        updateSelectionBorder(border, index == selectedIndex)

        itemView.setOnClickListener {
            when (template) {
                is Template.Import -> launchImport()
                else -> {
                    if (selectedIndex != index) {
                        val oldIndex = selectedIndex
                        selectedIndex = index
                        refreshBorderAt(oldIndex)
                        refreshBorderAt(index)
                        applyBackgroundTemplate(template)
                    }
                }
            }
        }

        if (template is Template.UserImage) {
            removeBtn.setOnClickListener { removeUserTemplate(template) }
        }
    }

    /** 从磁盘删除该用户模板，并刷新模板栏；若被删除的项是当前选中，则回退到主题默认。 */
    private fun removeUserTemplate(template: Template.UserImage) {
        try {
            template.file.delete()
        } catch (_: Exception) { /* 忽略 */ }

        val removedIndex = templates.indexOf(template)
        if (removedIndex < 0) return
        val wasSelected = removedIndex == selectedIndex
        templates.removeAt(removedIndex)
        if (wasSelected) {
            selectedIndex = 0
            applyBackgroundTemplate(templates[0])
        } else if (selectedIndex > removedIndex) {
            selectedIndex -= 1
        }
        populateTemplateRow()
    }

    private fun updateSelectionBorder(borderView: View, selected: Boolean) {
        if (selected) {
            borderView.background = ContextCompat.getDrawable(activity, R.drawable.bg_template_border)
        } else {
            borderView.background = null
        }
    }

    private fun refreshBorderAt(index: Int) {
        val row = templateRow ?: return
        val child = row.getChildAt(index) ?: return
        val border = child.findViewById<View>(R.id.v_template_border) ?: return
        updateSelectionBorder(border, index == selectedIndex)
    }

    private fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            activity.startActivityForResult(intent, REQ_PICK_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(activity, "打开文件选择器失败", Toast.LENGTH_SHORT).show()
        }
    }

    /** 由宿主 Activity 在 onActivityResult 中转发 */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_PICK_IMAGE) return
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return

        // 持久化到 shareBackgrounds 目录，命名 "本地N.png"
        val persisted = try {
            persistImportedImage(uri)
        } catch (e: Exception) {
            Toast.makeText(activity, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
            return
        } ?: run {
            Toast.makeText(activity, "导入失败", Toast.LENGTH_SHORT).show()
            return
        }

        // 在 Import 项之前插入新模板并选中
        val importIdx = templates.indexOfFirst { it is Template.Import }
        val newTpl = Template.UserImage(persisted, persisted.nameWithoutExtension)
        if (importIdx >= 0) {
            templates.add(importIdx, newTpl)
            selectedIndex = importIdx
        } else {
            templates.add(newTpl)
            selectedIndex = templates.size - 1
        }
        populateTemplateRow()
        applyBackgroundTemplate(newTpl)
    }

    /** 把用户挑选的图片复制到分享背景目录，命名为 "本地N.png"。返回写入后的文件。 */
    private fun persistImportedImage(uri: Uri): File? {
        val dir = StorageLocationManager.shareBackgroundsDir(activity)
        val index = nextLocalIndex()
        val target = File(dir, "本地$index.png")
        activity.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(8 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } > 0) {
                    output.write(buffer, 0, read)
                }
            }
        } ?: return null
        return target
    }

    private fun decodeAssetThumb(assetPath: String): Bitmap? {
        return try {
            activity.assets.open(assetPath).use { stream ->
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeFileThumb(file: File): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            null
        }
    }

    // ---- Render to Bitmap ----

    private fun renderShareBitmap(): Bitmap? {
        val card = shareCardView ?: return null
        if (card.width == 0 || card.height == 0) return null
        val bmp = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        card.draw(canvas)
        return bmp
    }

    private fun writeBitmapToFile(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun saveImage() {
        val bmp = renderShareBitmap() ?: run {
            Toast.makeText(activity, "生成图片失败", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val fileName = "YQreader_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png"
            val savedUri = saveToPictures(bmp, fileName)
            if (savedUri != null) {
                Toast.makeText(activity, "已保存到相册", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "保存失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 优先使用 MediaStore（API 29+，扫描可见，无需权限）；其它情况落到 app 外部私有目录。 */
    private fun saveToPictures(bmp: Bitmap, fileName: String): Uri? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YQreader")
                }
                val resolver = activity.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                    return uri
                }
            } catch (_: Exception) { /* 落到兜底路径 */ }
        }
        // API < Q 或 MediaStore 失败：写到 app 外部私有目录（不要求运行时权限）
        return try {
            val dir = File(activity.getExternalFilesDir(null), "Pictures").apply { mkdirs() }
            val file = File(dir, fileName)
            writeBitmapToFile(bmp, file)
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    private fun shareImage() {
        val bmp = renderShareBitmap() ?: run {
            Toast.makeText(activity, "生成图片失败", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cacheDir = File(activity.cacheDir, "share").apply { mkdirs() }
            val file = File(cacheDir, "share_${System.currentTimeMillis()}.png")
            writeBitmapToFile(bmp, file)
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_title)))
        } catch (e: Exception) {
            Toast.makeText(activity, "分享失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val REQ_PICK_IMAGE = 9911

        // 用于匹配 "本地1.png"、"本地23.jpg" 之类持久化后的导入背景文件名
        private val LOCAL_FILE_REGEX = Regex("^本地(\\d+)\\.[A-Za-z0-9]+$")
    }
}
