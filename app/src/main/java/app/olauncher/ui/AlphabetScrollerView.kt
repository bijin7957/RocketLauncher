package app.olauncher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import app.olauncher.R
import app.olauncher.helper.getColorFromAttr
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/**
 * Letter index along the edge of the home screen. Dragging along it bends the
 * letters into a curve around the finger, shows the current letter in a bubble
 * and reports it through [onLetterSelected].
 *
 * The view is meant to overlay the screen at full width so the curve has room to
 * draw, but it only claims touches that start inside the narrow edge strip.
 */
class AlphabetScrollerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")

        /** Index letter for an app label: its first letter without accents, or "#". */
        fun sectionLetter(label: String): String {
            val first = Normalizer.normalize(label.trim().take(1), Normalizer.Form.NFD)
                .replace(diacriticsRegex, "")
                .uppercase()
            return if (first.isNotEmpty() && first[0].isLetter()) first.take(1) else "#"
        }
    }

    var onLetterSelected: ((String) -> Unit)? = null

    /** Draw the index along the start edge instead of the end edge. */
    var alignStart = false
        set(value) {
            field = value
            invalidate()
        }

    private var letters: List<String> = emptyList()
    private var selectedIndex = -1
    private var touchY = 0f
    private var touching = false
    private var progress = 0f // 0 = idle column, 1 = fully curved
    private var animator: ValueAnimator? = null

    private val touchStripWidth = dp(40f)
    private val edgeMargin = dp(20f)
    private val maxLetterSpacing = dp(22f)
    private val maxCurveOffset = dp(84f)
    private val bubbleRadius = dp(30f)
    private val bubbleGap = dp(22f)

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(13f)
        color = context.getColorFromAttr(R.attr.primaryColor)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColorFromAttr(R.attr.primaryColor)
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(30f)
        color = context.getColorFromAttr(R.attr.primaryInverseColor)
    }

    fun setLetters(newLetters: List<String>) {
        if (newLetters == letters) return
        letters = newLetters
        selectedIndex = -1
        invalidate()
    }

    private val letterSpacing: Float
        get() {
            if (letters.isEmpty()) return 0f
            val available = height - paddingTop - paddingBottom - dp(16f)
            return min(maxLetterSpacing, available / letters.size)
        }

    private val columnTop: Float
        get() {
            val usable = height - paddingTop - paddingBottom
            return paddingTop + (usable - letterSpacing * letters.size) / 2f
        }

    private val columnX: Float
        get() = if (alignStart) edgeMargin else width - edgeMargin

    private fun letterY(index: Int): Float = columnTop + letterSpacing * (index + 0.5f)

    private fun indexAt(y: Float): Int {
        if (letters.isEmpty() || letterSpacing <= 0f) return -1
        return ((y - columnTop) / letterSpacing).toInt().coerceIn(0, letters.lastIndex)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (letters.isEmpty()) return

        val spacing = letterSpacing
        // The curve spans roughly half of the column on either side of the finger
        val curveRadius = (spacing * letters.size * 0.5f).coerceAtLeast(spacing * 6)
        val direction = if (alignStart) 1f else -1f
        val baseTextSize = sp(13f)
        val fontMetrics = letterPaint.fontMetrics
        val textCenterShift = (fontMetrics.ascent + fontMetrics.descent) / 2f

        for (i in letters.indices) {
            val y = letterY(i)
            val distance = abs(y - touchY)
            // Smooth bell-shaped falloff, 1 under the finger and 0 at curveRadius
            val weight = if (distance < curveRadius)
                ((cos(Math.PI * distance / curveRadius) + 1) / 2).toFloat()
            else 0f
            val x = columnX + direction * maxCurveOffset * weight * progress

            letterPaint.textSize = baseTextSize * (1f + 0.25f * weight * progress)
            letterPaint.alpha = (140 + 115 * progress).toInt()
            canvas.drawText(letters[i], x, y - textCenterShift, letterPaint)
        }

        if (selectedIndex in letters.indices && progress > 0f) {
            val bubbleX = columnX + direction * (maxCurveOffset + bubbleGap + bubbleRadius)
            val bubbleY = touchY.coerceIn(letterY(0), letterY(letters.lastIndex))
            bubblePaint.alpha = (255 * progress).toInt()
            bubbleTextPaint.alpha = (255 * progress).toInt()
            canvas.drawCircle(bubbleX, bubbleY, bubbleRadius * progress, bubblePaint)
            val bubbleMetrics = bubbleTextPaint.fontMetrics
            canvas.drawText(
                letters[selectedIndex],
                bubbleX,
                bubbleY - (bubbleMetrics.ascent + bubbleMetrics.descent) / 2f,
                bubbleTextPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (letters.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val inStrip = if (alignStart) event.x <= touchStripWidth
                else event.x >= width - touchStripWidth
                val inColumn = event.y >= columnTop - spacingSlop() &&
                        event.y <= columnTop + letterSpacing * letters.size + spacingSlop()
                if (!inStrip || !inColumn) return false

                parent?.requestDisallowInterceptTouchEvent(true)
                touching = true
                selectedIndex = -1 // Picking the same letter again still reports it
                animateProgress(1f)
                updateSelection(event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!touching) return false
                updateSelection(event.y)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!touching) return false
                touching = false
                parent?.requestDisallowInterceptTouchEvent(false)
                animateProgress(0f)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun spacingSlop() = dp(24f)

    private fun updateSelection(y: Float) {
        touchY = y
        val index = indexAt(y)
        if (index != selectedIndex && index >= 0) {
            selectedIndex = index
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onLetterSelected?.invoke(letters[index])
        }
        invalidate()
    }

    private fun animateProgress(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
