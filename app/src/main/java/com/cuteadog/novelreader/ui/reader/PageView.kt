package com.cuteadog.novelreader.ui.reader

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

    private var textSize: Float = 42f
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
    private val zoomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val magnifierBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFBBBBBB.toInt()
        strokeWidth = 2f
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
    private var draggingHandle: Int = -1 // 0 for start, 1 for end, -1 for none
    private var isZoomed: Boolean = false
    private var zoomScale: Float = 1.5f
    private var zoomCenterX: Float = 0f
    private var zoomCenterY: Float = 0f

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
    var onSelectionClearedListener: (() -> Unit)? = null
    var onHandleDragListener: ((isDragging: Boolean) -> Unit)? = null

    private val gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (isAnimating || isScrolling || hasMoved || isInSelectionMode) return
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

    fun isInSelectionMode(): Boolean = isInSelectionMode

    fun clearSelection() {
        val wasInSelection = isInSelectionMode
        isInSelectionMode = false
        selectionStart = -1
        selectionEnd = -1
        draggingHandle = -1
        isZoomed = false
        invalidate()
        if (wasInSelection) {
            onSelectionClearedListener?.invoke()
        }
    }

    private fun handleLongPress(touchX: Float, touchY: Float) {
        val layout = currentPageContent ?: return
        val layoutText = layout.text.toString()
        if (layoutText.isBlank()) return

        val x = (touchX - PADDING_LEFT).coerceAtLeast(0f)
        val y = (touchY - PADDING_TOP).coerceAtLeast(0f)
        val line = layout.getLineForVertical(y.toInt())
        val offset = layout.getOffsetForHorizontal(line, x).coerceIn(0, layoutText.length)

        // Check if offset falls within an existing highlight - auto-select entire highlight
        val matchingHighlight = pageHighlights.find { offset >= it.start && offset < it.end }
        if (matchingHighlight != null) {
            selectionStart = matchingHighlight.start
            selectionEnd = matchingHighlight.end
        } else {
            selectionStart = offset
            selectionEnd = (offset + 1).coerceAtMost(layoutText.length)
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
        // Draw magnifier overlay on top when dragging handles
        if (isZoomed && currentPageContent != null && isInSelectionMode) {
            drawMagnifier(canvas, currentPageContent!!)
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

            // Clip for text and highlights
            save()
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
            }

            content.draw(this)
            restore()

            // Draw handles outside clip so they're not cut off at edges
            if (content === currentPageContent && isInSelectionMode
                && selectionStart >= 0 && selectionEnd > selectionStart) {
                translate(PADDING_LEFT, PADDING_TOP)
                drawSelectionHandles(this, content)
            }
        }
    }

    private fun drawTextHighlight(canvas: Canvas, layout: StaticLayout, start: Int, end: Int, color: Int) {
        val textLen = layout.text.length
        val safeStart = start.coerceIn(0, textLen)
        val safeEnd = end.coerceIn(0, textLen)
        if (safeStart >= safeEnd) return

        highlightPaint.color = color
        val startLine = layout.getLineForOffset(safeStart)
        val endLine = layout.getLineForOffset((safeEnd - 1).coerceAtLeast(safeStart))
        for (line in startLine..endLine) {
            val lineTop = layout.getLineTop(line).toFloat()
            val lineBottom = layout.getLineBottom(line).toFloat()
            val left = if (line == startLine) layout.getPrimaryHorizontal(safeStart) else 0f
            val right = if (line == endLine) {
                val lineEnd = layout.getLineEnd(endLine)
                if (safeEnd >= lineEnd) layout.getLineRight(endLine)
                else layout.getPrimaryHorizontal(safeEnd)
            } else layout.getLineWidth(line)
            if (right > left) {
                canvas.drawRect(left, lineTop, right, lineBottom, highlightPaint)
            }
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
        // Use e-1 to keep end handle on the correct line at line breaks
        val endLine = layout.getLineForOffset((e - 1).coerceAtLeast(0))
        if (startLine < 0 || endLine >= layout.lineCount) return

        val startLineStart = layout.getLineStart(startLine)
        val startLineEnd = layout.getLineEnd(startLine)
        val safeS = s.coerceIn(startLineStart, startLineEnd - 1)
        val sx = layout.getPrimaryHorizontal(safeS)
        val sy = layout.getLineBottom(startLine).toFloat()

        val endLineEnd = layout.getLineEnd(endLine)
        val ex = if (e >= endLineEnd) {
            layout.getLineRight(endLine)
        } else {
            layout.getPrimaryHorizontal(e)
        }
        val ey = layout.getLineBottom(endLine).toFloat()

        val handleR = 20f
        val stemH = 6f

        // Start handle: stem + circle offset LEFT
        canvas.drawRect(sx - 1.5f, sy, sx + 1.5f, sy + stemH, handlePaint)
        canvas.drawCircle(sx - handleR * 0.5f, sy + stemH + handleR, handleR, handlePaint)

        // End handle: stem + circle offset RIGHT
        canvas.drawRect(ex - 1.5f, ey, ex + 1.5f, ey + stemH, handlePaint)
        canvas.drawCircle(ex + handleR * 0.5f, ey + stemH + handleR, handleR, handlePaint)
    }

    private fun getTouchedHandle(touchX: Float, touchY: Float): Int {
        val layout = currentPageContent ?: return -1
        val textLen = layout.text.length
        if (textLen == 0 || selectionStart < 0 || selectionEnd < 0) return -1

        val s = selectionStart.coerceIn(0, textLen - 1)
        val e = selectionEnd.coerceIn(s + 1, textLen)

        val startLine = layout.getLineForOffset(s)
        // Use e-1 to keep end handle on the correct line at line breaks
        val endLine = layout.getLineForOffset((e - 1).coerceAtLeast(0))

        val handleR = 20f
        val stemH = 6f

        // Start handle center (view coords): offset LEFT
        val sx = layout.getPrimaryHorizontal(s) + PADDING_LEFT - handleR * 0.5f
        val sy = layout.getLineBottom(startLine).toFloat() + PADDING_TOP + stemH + handleR

        // End handle center (view coords): offset RIGHT
        val endLineEnd = layout.getLineEnd(endLine)
        val rawEx = if (e >= endLineEnd) {
            layout.getLineRight(endLine)
        } else {
            layout.getPrimaryHorizontal(e)
        }
        val ex = rawEx + PADDING_LEFT + handleR * 0.5f
        val ey = layout.getLineBottom(endLine).toFloat() + PADDING_TOP + stemH + handleR

        val touchRadius = 40f
        if ((touchX - sx) * (touchX - sx) + (touchY - sy) * (touchY - sy) <= touchRadius * touchRadius) {
            return 0 // Start handle
        }
        if ((touchX - ex) * (touchX - ex) + (touchY - ey) * (touchY - ey) <= touchRadius * touchRadius) {
            return 1 // End handle
        }
        return -1
    }

    private fun updateSelectionFromDrag(touchX: Float, touchY: Float) {
        val layout = currentPageContent ?: return
        val textLen = layout.text.length
        if (textLen == 0) return

        val x = (touchX - PADDING_LEFT).coerceAtLeast(0f)
        val y = (touchY - PADDING_TOP).coerceAtLeast(0f)
        val line = layout.getLineForVertical(y.toInt())
        val offset = layout.getOffsetForHorizontal(line, x).coerceIn(0, textLen)

        // Check if drag offset lands inside an existing highlight — snap to boundary
        val overlapping = pageHighlights.find { offset >= it.start && offset < it.end }

        when (draggingHandle) {
            0 -> { // Dragging start handle
                val snapOffset = if (overlapping != null) overlapping.start else offset
                selectionStart = snapOffset.coerceAtMost(selectionEnd - 1)
            }
            1 -> { // Dragging end handle
                if (overlapping != null) {
                    selectionEnd = overlapping.end.coerceAtLeast(selectionStart + 1).coerceAtMost(textLen)
                } else {
                    selectionEnd = (offset + 1).coerceAtLeast(selectionStart + 1).coerceAtMost(textLen)
                }
            }
        }
    }

    private fun isTouchInsideSelection(touchX: Float, touchY: Float): Boolean {
        val layout = currentPageContent ?: return false
        if (selectionStart < 0 || selectionEnd <= selectionStart) return false

        val startLine = layout.getLineForOffset(selectionStart)
        val endLine = layout.getLineForOffset(selectionEnd - 1)

        for (line in startLine..endLine) {
            val lineTop = layout.getLineTop(line).toFloat() + PADDING_TOP
            val lineBottom = layout.getLineBottom(line).toFloat() + PADDING_TOP
            if (touchY < lineTop || touchY > lineBottom) continue

            val left = if (line == startLine) layout.getPrimaryHorizontal(selectionStart) + PADDING_LEFT else PADDING_LEFT
            val right = if (line == endLine) layout.getPrimaryHorizontal(selectionEnd - 1) + PADDING_LEFT else pageWidth - PADDING_LEFT

            if (touchX >= left && touchX <= right) return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isAnimating) return true
                startX = event.x
                currentX = startX
                hasMoved = false
                longPressConsumed = false

                // Check if touching a selection handle
                if (isInSelectionMode) {
                    draggingHandle = getTouchedHandle(event.x, event.y)
                    if (draggingHandle != -1) {
                        onHandleDragListener?.invoke(true)
                        return true // Consume touch for dragging
                    }
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isAnimating) return true
                if (longPressConsumed) return true
                currentX = event.x

                // Handle dragging selection handles
                if (draggingHandle != -1 && isInSelectionMode) {
                    updateSelectionFromDrag(event.x, event.y)
                    isZoomed = true
                    zoomCenterX = event.x
                    zoomCenterY = event.y
                    invalidate()
                    return true
                }

                // Prevent scrolling during selection
                if (isInSelectionMode) return true

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

                // Finish dragging
                if (draggingHandle != -1) {
                    draggingHandle = -1
                    isZoomed = false
                    invalidate() // 立即隐藏放大镜
                    onHandleDragListener?.invoke(false)
                    // Update selection text after dragging
                    if (isInSelectionMode && selectionStart >= 0 && selectionEnd > selectionStart) {
                        val layout = currentPageContent ?: return true
                        val selectedText = layout.text.substring(selectionStart, selectionEnd)
                        val anchorX = (layout.getPrimaryHorizontal(selectionStart.coerceIn(0, layout.text.length - 1)) + PADDING_LEFT)
                        val anchorY = layout.getLineTop(layout.getLineForOffset(selectionStart)).toFloat() + PADDING_TOP
                        onTextSelectedListener?.invoke(selectedText, selectionStart, selectionEnd, anchorX, anchorY)
                    }
                    return true
                }

                if (!hasMoved || scrollOffset == 0f) {
                    // Check if clicked outside selection to cancel
                    if (isInSelectionMode && !isTouchInsideSelection(event.x, event.y)) {
                        clearSelection()
                        return true
                    }

                    if (isInSelectionMode) {
                        // Stay in selection mode, don't trigger page click
                        return true
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
                // 仅回弹动画重置 isScrolling；成功翻页由 applyPageTransition 重置
                if (to == 0f) isScrolling = false
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

    private fun drawMagnifier(canvas: Canvas, content: StaticLayout) {
        val magnifierW = 240f
        val magnifierH = 120f
        val cornerR = 8f
        val offsetAbove = 90f

        // Position magnifier above touch point, clamped to view bounds
        val cx = zoomCenterX.coerceIn(magnifierW / 2, pageWidth.toFloat() - magnifierW / 2)
        var top = zoomCenterY - offsetAbove - magnifierH
        if (top < 4f) {
            // If too close to top, show below touch point instead
            top = zoomCenterY + 40f
        }
        val rect = RectF(cx - magnifierW / 2, top, cx + magnifierW / 2, top + magnifierH)

        // Clip to rounded rect and draw magnified content
        canvas.save()
        val path = android.graphics.Path()
        path.addRoundRect(rect, cornerR, cornerR, android.graphics.Path.Direction.CW)
        canvas.clipPath(path)

        // Background
        canvas.drawRect(rect, backgroundPaint)

        // Zoom transform: map touch point to magnifier center
        canvas.translate(rect.centerX(), rect.centerY())
        canvas.scale(zoomScale, zoomScale)
        canvas.translate(-zoomCenterX, -zoomCenterY)

        // Draw page content at its normal position
        canvas.translate(PADDING_LEFT, PADDING_TOP)

        // Draw highlights in magnifier
        for (ph in pageHighlights) {
            drawTextHighlight(canvas, content, ph.start, ph.end, ph.color)
        }
        if (selectionStart >= 0 && selectionEnd > selectionStart) {
            drawTextHighlight(canvas, content, selectionStart, selectionEnd, 0x662196F3.toInt())
        }

        content.draw(canvas)
        canvas.restore()

        // Draw border on top
        canvas.drawRoundRect(rect, cornerR, cornerR, magnifierBorderPaint)
    }

    companion object {
        const val PAGE_PREVIOUS = -1
        const val PAGE_NEXT = 1
        const val PADDING_LEFT = 40f
        const val PADDING_TOP = 24f
    }
}
