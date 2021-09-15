package dev.pratyush.qrdemo.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Size
import android.view.View
import dev.pratyush.qrdemo.util.isPortrait
import dev.pratyush.qrdemo.util.px
import kotlin.math.min

class ScannerOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val transparentPaint: Paint by lazy {
        Paint().apply {
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }

    private val strokePaint: Paint by lazy {
        Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            strokeWidth = context.px(3f)
            style = Paint.Style.STROKE
        }
    }

    var drawBlueRect: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val blueColor = Color.BLUE

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#88000000"))

        val radius = context.px(4f)
        val rectF = scanRect
        canvas.drawRoundRect(rectF, radius, radius, transparentPaint)
        strokePaint.color = if (drawBlueRect) blueColor else Color.WHITE
        canvas.drawRoundRect(rectF, radius, radius, strokePaint)
    }

    val size: Size
        get() = Size(width, height)

    val scanRect: RectF
        get() = if (context.isPortrait()) {
            val min = min(width, height).toFloat()
            val size = min - min.div(10)
            val l = (width - size) / 2
            val r = width - l
            val t = height * 0.15f
            val b = t + size
            RectF(l, t, r, b)
        } else {
            val min = min(width, height).toFloat()
            val size = min - min.div(10)
            val l = width * 0.05f
            val r = l + size
            val t = height * 0.05f
            val b = t + size
            RectF(l, t, r, b)
        }


}