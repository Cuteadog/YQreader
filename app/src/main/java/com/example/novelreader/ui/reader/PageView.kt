package com.example.novelreader.ui.reader

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.TextView
import androidx.core.graphics.withTranslation
import androidx.core.view.GestureDetectorCompat
import kotlin.math.abs

class PageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentPageContent: StaticLayout? = null
    private var nextPageContent: StaticLayout? = null
    private var prevPageContent: StaticLayout? = null

    private var textSize: Float = 32f
    private var textColor: Int = Color.BLACK
    private var backgroundColor: Int = Color.WHITE

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x662196F3.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt()
    }

    // Scroll/flip state
    private var startX: Float = 0f
    private var currentX: Float = 0f
    private var scrollOffset: Float = 0f
    private var isScrolling: Boolean = false
    private var hasMoved = false
    private var currentAnimation: Animation? = null
    private var isAnimating = false

    private var pageWidth: Int = 0
    private var pageHeight: Int = 0

    // Selection state
    private var isInSelectionMode: Boolean = false
    private var selectionStart: Int = -1   // page-local offset
    private var selectionEnd: Int = -1     // page-local offset
    private var longPressConsumed = false

    // Highlights to draw on current page (page-local offsets)
    data class PageHighlight(val start: Int, val end: Int, val color: Int)
    private var pageHighlights: List<PageHighlight> = emptyList()

    // Callbacks
    // Keep legacy listener setters for compatibility
    var onPageClickListener: (() -> Unit)? = null
    var onPageChangeListener: ((direction: Int) -> Unit)? = null
    // Fires when user long-presses: (selectedText, pageLocalStart, pageLocalEnd, popupAnchorX, popupAnchorY)
    var onTextSelectedListener: ((String, Int, Int, Float, Float) -> Unit)? = null
    // Fires when user taps an existing highlight: (highlightIndex)
    var onHighlightTappedListener: ((Int) -> Unit)? = null

    private val gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (isAnimating || isScrolling || hasMoved) return
            longPressConsumed = true
            handleLongPress(e.x, e.y)
        }
    })

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        textPaint.textSize = textSize
        textPaint.color = textColor
        textPaint.textAlign = Paint.Align.LEFT
        backgroundPaint.color = backgroundColor
        textPaint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }

    fun getBackgroundColor(): Int = backgroundColor
    fun getTextColor(): Int = textColor

    fun setTextSize(size: Float) {
        textSize = size
        textPaint.textSize = size
    }

    fun setTextColor(color: Int) {
        textColor = color
        textPaint.color = color
        invalidate()
    }

    override fun setBackgroundColor(color: Int) {
        backgroundColor = color
        backgroundPaint.color = color
        invalidate()
    }

    fun setPageContent(currentPage: StaticLayout?, nextPage: StaticLayout?, prevPage: StaticLayout?) {
        currentPageContent = currentPage
        nextPageContent = nextPage
        prevPageContent = prevPage
        clearSelection()
        invalidate()
    }

    fun setPageHighlights(highlights: List<PageHighlight>) {
        Log.d("PageView", "setPageHighlights: size=${highlights.size}, highlights=$highlights")
        pageHighlights = highlights
        invalidate()
    }

    fun clearSelection() {
        isInSelectionMode = false
        selectionStart = -1
        selectionEnd = -1
        invalidate()
    }

    private fun handleLongPress(touchX: Float, touchY: Float) {
        val layout = currentPageContent ?: return
        val layoutText = layout.text.toString()
        if (layoutText.isBlank()) return

        val x = (touchX - PADDING_LEFT).coerceAtLeast(0f)
        val y = (touchY - PADDING_TOP).coerceAtLeast(0f)
        val line = layout.getLineForVertical(y.toInt())
        val offset = layout.getOffsetForHorizontal(line, x).coerceIn(0, layoutText.length)

        // Expand to sentence boundaries
        val sentenceBreakers = "。！？!?\n"
        var start = offset
        var end = offset
        while (start > 0 && layoutText[start - 1] !in sentenceBreakers) start--
        while (end < layoutText.length && layoutText[end] !in sentenceBreakers) end++
        if (end < layoutText.length) end++  // include the breaker char

        selectionStart = start.coerceIn(0, layoutText.length)
        selectionEnd = end.coerceIn(0, layoutText.length)
        if (selectionStart >= selectionEnd) {
            // fallback: select the word/line
            selectionStart = layout.getLineStart(line)
            selectionEnd = layout.getLineEnd(line)
        }
        isInSelectionMode = true
        invalidate()

        val selectedText = layoutText.substring(selectionStart, selectionEnd)
        // Anchor popup above the selection start line
        val safeSelectionStart = selectionStart.coerceIn(0, layoutText.length - 1)
        val anchorX = (layout.getPrimaryHorizontal(safeSelectionStart) + PADDING_LEFT)
            .coerceIn(0f, pageWidth.toFloat())
        val anchorY = layout.getLineTop(layout.getLineForOffset(selectionStart)).toFloat() + PADDING_TOP
        onTextSelectedListener?.invoke(selectedText, selectionStart, selectionEnd, anchorX, anchorY)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pageWidth = w
        pageHeight = h
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isScrolling) {
            drawScrollingPage(canvas)
        } else {
            drawPage(canvas, currentPageContent, 0f)
        }
    }

    private fun drawScrollingPage(canvas: Canvas) {
        val offset = scrollOffset
        if (offset == 0f) {
            drawPage(canvas, currentPageContent, 0f)
            return
        } else if (offset > 0) {
            if (prevPageContent != null) {
                drawPage(canvas, currentPageContent, 0f)
                drawPage(canvas, prevPageContent, offset - pageWidth)
            }
        } else if (offset < 0) {    // 向左滑
            if (nextPageContent != null) {
                drawPage(canvas, nextPageContent, 0f)      // 下一页不动
                drawPage(canvas, currentPageContent, offset) // 当前页向左滑动
            }
        }
    }

    private fun drawPage(canvas: Canvas, content: StaticLayout?, xOffset: Float) {
        if (content == null) return

        val availableWidth = pageWidth - PADDING_LEFT * 2
        val availableHeight = pageHeight - PADDING_TOP * 2
        if (availableWidth <= 0 || availableHeight <= 0) return

        canvas.withTranslation(xOffset, 0f) {
            drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), backgroundPaint)
            clipRect(PADDING_LEFT, PADDING_TOP, pageWidth - PADDING_LEFT, pageHeight - PADDING_TOP)
            translate(PADDING_LEFT, PADDING_TOP)

            // Draw persisted highlights
            if (content === currentPageContent) {
                for (ph in pageHighlights) {
                    drawTextHighlight(this, content, ph.start, ph.end, ph.color)
                }
            }

            // Draw current selection overlay
            if (content === currentPageContent && isInSelectionMode
                && selectionStart >= 0 && selectionEnd > selectionStart) {
                drawTextHighlight(this, content, selectionStart, selectionEnd, 0x662196F3.toInt())
                // Draw handles
                drawSelectionHandles(this, content)
            }

            content.draw(this)
        }
    }

    private fun drawTextHighlight(canvas: Canvas, layout: StaticLayout, start: Int, end: Int, color: Int) {
        val textLen = layout.text.length
        val safeStart = start.coerceIn(0, textLen)
        val safeEnd = end.coerceIn(0, textLen)
        if (safeStart >= safeEnd) return

        highlightPaint.color = color
        val startLine = layout.getLineForOffset(safeStart)
        val endLine = layout.getLineForOffset(safeEnd)
        for (line in startLine..endLine) {
            val lineTop = layout.getLineTop(line).toFloat()
            val lineBottom = layout.getLineBottom(line).toFloat()
            val left = if (line == startLine) layout.getPrimaryHorizontal(safeStart) else 0f
            val right = if (line == endLine) layout.getPrimaryHorizontal(safeEnd)
            else layout.getLineWidth(line)
            canvas.drawRect(left, lineTop, right.coerceAtLeast(left + 1f), lineBottom, highlightPaint)
        }
    }

    private fun drawSelectionHandles(canvas: Canvas, layout: StaticLayout) {
        if (selectionStart < 0 || selectionEnd < 0) return
        val textLen = layout.text.length
        if (textLen == 0) return
        if (selectionStart >= textLen || selectionEnd > textLen) {
            clearSelection()
            return
        }

        var s = selectionStart
        var e = selectionEnd
        s = s.coerceIn(0, textLen - 1)
        e = e.coerceIn(s + 1, textLen)

        val startLine = layout.getLineForOffset(s)
        val endLine = layout.getLineForOffset(if (e == textLen) textLen - 1 else e)
        if (startLine < 0 || endLine >= layout.lineCount) return

        val startLineStart = layout.getLineStart(startLine)
        val startLineEnd = layout.getLineEnd(startLine)
        val safeS = s.coerceIn(startLineStart, startLineEnd - 1)
        val sx = layout.getPrimaryHorizontal(safeS)
        val sy = layout.getLineBottom(startLine).toFloat()

        val endLineStart = layout.getLineStart(endLine)
        val endLineEnd = layout.getLineEnd(endLine)
        val ex = if (e == textLen) {
            layout.getLineRight(endLine)
        } else {
            val safeE = e.coerceIn(endLineStart, endLineEnd)
            layout.getPrimaryHorizontal(safeE)
        }
        val ey = layout.getLineBottom(endLine).toFloat()

        canvas.drawCircle(sx, sy + 8f, 8f, handlePaint)
        canvas.drawCircle(ex, ey + 8f, 8f, handlePaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
//                if (isAnimating) {
//                    cancelAnimation()
//                    scrollOffset = 0f
//                    isScrolling = false
//                    invalidate()
//                }
                if (isAnimating) return true
                startX = event.x
                currentX = startX
                hasMoved = false
                longPressConsumed = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isAnimating) return true
                if (longPressConsumed) return true
                currentX = event.x
                scrollOffset = currentX - startX
                if (abs(scrollOffset) > 10f) {
                    if (!hasMoved) {
                        hasMoved = true
                        isScrolling = true
                    }
                    if (scrollOffset > 0 && prevPageContent == null) {
                        scrollOffset = 0f
                    } else if (scrollOffset < 0 && nextPageContent == null) {
                        scrollOffset = 0f
                    } else {
                        scrollOffset = scrollOffset.coerceIn(-pageWidth.toFloat(), pageWidth.toFloat())
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isAnimating) return true
                if (longPressConsumed) {
                    longPressConsumed = false
                    return true
                }
                if (!hasMoved || scrollOffset == 0f) {
                    isScrolling = false
                    if (isInSelectionMode) {
                        clearSelection()
                    } else {
                        onPageClickListener?.invoke()
                    }
                    performClick()
                    return true
                }
                // 翻页阈值
                val shouldChangePage = abs(scrollOffset) > pageWidth * 0.2f
                if (shouldChangePage) {
                    if (scrollOffset > 0 && prevPageContent != null) {
                        startPageAnimation(scrollOffset, pageWidth.toFloat()) {
                            onPageChangeListener?.invoke(PAGE_PREVIOUS)
                        }
                    } else if (scrollOffset < 0 && nextPageContent != null) {
                        startPageAnimation(scrollOffset, -pageWidth.toFloat()) {
                            onPageChangeListener?.invoke(PAGE_NEXT)
                        }
                    } else {
                        startPageAnimation(scrollOffset, 0f) {}
                    }
                } else {
                    startPageAnimation(scrollOffset, 0f) {}
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun cancelAnimation() {
        if (currentAnimation != null) {
            clearAnimation()
            currentAnimation = null
        }
        isAnimating = false
        setHasTransientState(false)
    }

    fun applyPageTransition(newCurrent: StaticLayout?, newPrev: StaticLayout?, newNext: StaticLayout?) {
        currentPageContent = newCurrent
        prevPageContent = newPrev
        nextPageContent = newNext
        isScrolling = false
        scrollOffset = 0f
        clearSelection()
    }

    private fun startPageAnimation(from: Float, to: Float, onAnimationEnd: () -> Unit) {
        cancelAnimation()
        val animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                scrollOffset = from + (to - from) * interpolatedTime
                invalidate()
            }
        }
        animation.duration = 300
        animation.interpolator = AccelerateDecelerateInterpolator()
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                isAnimating = true
                isScrolling = true
                setHasTransientState(true)
            }
            override fun onAnimationEnd(animation: Animation) {
                isAnimating = false
                currentAnimation = null
                scrollOffset = to
                invalidate()
                setHasTransientState(false)
                post { onAnimationEnd() }
            }
            override fun onAnimationRepeat(animation: Animation) {}
        })
        currentAnimation = animation
        startAnimation(animation)
    }

    fun animateBackgroundColor(view: View, fromColor: Int, toColor: Int, duration: Long = 500) {
        val animator = ValueAnimator.ofArgb(fromColor, toColor)
        animator.duration = duration
        animator.addUpdateListener {
            val color = it.animatedValue as Int
            view.setBackgroundColor(color)
        }
        animator.start()
    }

    fun animateTextColor(view: Any, fromColor: Int, toColor: Int, duration: Long = 500) {
        val animator = ValueAnimator.ofArgb(fromColor, toColor)
        animator.duration = duration
        animator.addUpdateListener {
            val color = it.animatedValue as Int
            when (view) {
                is PageView -> view.setTextColor(color)
                is TextView -> view.setTextColor(color)
                else -> throw IllegalArgumentException("Unsupported view type")
            }
        }
        animator.start()
    }

    companion object {
        const val PAGE_PREVIOUS = -1
        const val PAGE_NEXT = 1
        const val PADDING_LEFT = 40f
        const val PADDING_TOP = 30f
    }
}
