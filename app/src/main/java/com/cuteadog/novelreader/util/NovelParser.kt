package com.cuteadog.novelreader.util

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.cuteadog.novelreader.ui.reader.PageView
import java.io.File

class NovelParser(private val context: Context) {

    data class Page(
        val content: String,
        val startPos: Int,
        val endPos: Int
    )

    fun parseTextFile(file: File, pageWidth: Int, pageHeight: Int, textSize: Float, typeface: Typeface, color: Int): List<StaticLayout> {
        val text = FileUtil.readTextFile(file)
        return paginateText(text, pageWidth, pageHeight, textSize, typeface, color)
    }

    fun parseTextFromUri(context: Context, uri: Uri, pageWidth: Int, pageHeight: Int, textSize: Float, typeface: Typeface, color: Int): List<StaticLayout> {
        val text = FileUtil.readTextFileFromUri(context, uri)
        return paginateText(text, pageWidth, pageHeight, textSize, typeface, color)
    }

    fun paginateText(text: String, pageWidth: Int, pageHeight: Int, textSize: Float, typeface: Typeface, color: Int): List<StaticLayout> {
        val pages = mutableListOf<StaticLayout>()
        val textPaint = TextPaint().apply {
            this.textSize = textSize
            this.typeface = typeface
            this.color = color
            this.isAntiAlias = true
        }

        // 计算可用范围
        val availableWidth = pageWidth - PageView.PADDING_LEFT.toInt() * 2
        val availableHeight = (pageHeight - PageView.PADDING_TOP.toInt() * 2).coerceAtLeast(1)

        var currentPos = 0
        val textLength = text.length

        // 创建一个完整的 StaticLayout，不限制行数，用于测量
        val fullLayout = StaticLayout.Builder.obtain(
            text, 0, textLength, textPaint, availableWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(false)
            .setEllipsize(null)
            .build()

        val totalLines = fullLayout.lineCount
        var currentLine = 0

        while (currentPos < textLength && currentLine < totalLines) {
            // 找到当前起始行
            val startLine = currentLine
            // 从 startLine 开始累加行高，直到超过可用高度
            var accumulatedHeight = 0f
            var endLine = startLine
            while (endLine < totalLines) {
                val lineTop = fullLayout.getLineTop(endLine)
                val lineBottom = fullLayout.getLineBottom(endLine)
                val lineHeight = lineBottom - lineTop
                if (accumulatedHeight + lineHeight > availableHeight) {
                    break
                }
                accumulatedHeight += lineHeight
                endLine++
            }

            if (endLine == startLine) {
                // 单行都无法容纳，强制取一行
                endLine = startLine + 1
            }

            val startPos = fullLayout.getLineStart(startLine)
            val endPos = if (endLine < totalLines) {
                fullLayout.getLineStart(endLine)
            } else {
                textLength
            }

            // 为当前页创建一个新的 StaticLayout，仅包含该页的文本
            val pageText = text.substring(startPos, endPos)
            val pageLayout = StaticLayout.Builder.obtain(
                pageText, 0, pageText.length, textPaint, availableWidth
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)   // 设置行间距
                .setIncludePad(false)
                .setEllipsize(null)         // 不添加省略号
                .build()

            pages.add(pageLayout)

            currentPos = endPos
            currentLine = endLine
        }

        return pages
    }

    private fun isBreakablePosition(char: Char): Boolean {
        return char.isWhitespace() || char in ",.!?;:，。！？；："
    }

    fun getPageLayouts(
        text: String,
        pageWidth: Int,
        pageHeight: Int,
        textSize: Float,
        typeface: Typeface,
        color: Int
    ): List<StaticLayout> {
        return paginateText(text, pageWidth, pageHeight, textSize, typeface, color)
    }
}