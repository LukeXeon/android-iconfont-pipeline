package open.source.iconfont

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import java.util.*
import kotlin.math.min

/**
 * 用于绘制IconFont的边界，这个类应该保证只在Debug包才工作
 */
@SuppressLint("MemberVisibilityCanBePrivate")
object DebugDraw {

    private const val DEBUG_DRAW_KEY = "debug_draw"
    private const val DEBUG_PREFERENCES = "icon_font_debug_settings"
    private const val BORDER_COLOR = -0x66010000
    private const val CORNER_COLOR = -0xffff01

    @JvmStatic
    var isAlwaysShowLayoutBounds: Boolean
        get() = preferences.getBoolean(DEBUG_DRAW_KEY, false)
        set(value) {
            preferences.edit().putBoolean(DEBUG_DRAW_KEY, value).apply()
            IconFont.runOnMainThread(invalidateRunnable)
        }
    private val preferences: SharedPreferences by lazy {
        IconFont.application.getSharedPreferences(
            DEBUG_PREFERENCES,
            Context.MODE_PRIVATE
        )
    }
    private val invalidateRunnable = Runnable {
        for (drawable in drawableRefs) {
            drawable.invalidateSelf()
        }
    }
    private val drawableRefs: MutableSet<Drawable> = Collections.newSetFromMap(WeakHashMap())
    private lateinit var mountBoundsRect: Rect
    private lateinit var mountBoundsBorderPaint: Paint
    private lateinit var mountBoundsCornerPaint: Paint

    internal fun draw(host: Drawable, canvas: Canvas) {
        if (IconFont.isDebuggable && IconFont.isMainThread) {
            drawableRefs.add(host)
            if (isAlwaysShowLayoutBounds || IconFont.isShowingLayoutBounds) {
                highlightMountBounds(host, canvas)
            }
        }
    }

    private fun highlightMountBounds(host: Drawable, canvas: Canvas) {
        val resources = Resources.getSystem()
        if (!::mountBoundsRect.isInitialized) {
            mountBoundsRect = Rect()
        }
        if (!::mountBoundsBorderPaint.isInitialized) {
            mountBoundsBorderPaint = Paint()
            mountBoundsBorderPaint.style = Paint.Style.STROKE
            mountBoundsBorderPaint.strokeWidth = dipToPixels(resources, 1).toFloat()
        }
        if (!::mountBoundsCornerPaint.isInitialized) {
            mountBoundsCornerPaint = Paint()
            mountBoundsCornerPaint.style = Paint.Style.FILL
            mountBoundsCornerPaint.strokeWidth = dipToPixels(resources, 2).toFloat()
        }
        mountBoundsRect.set(host.bounds)
        mountBoundsBorderPaint.color = BORDER_COLOR
        drawMountBoundsBorder(canvas, mountBoundsBorderPaint, mountBoundsRect)
        mountBoundsCornerPaint.color = CORNER_COLOR
        drawMountBoundsCorners(
            canvas,
            mountBoundsCornerPaint,
            mountBoundsRect,
            mountBoundsCornerPaint.strokeWidth.toInt(),
            min(
                min(mountBoundsRect.width(), mountBoundsRect.height()) / 3,
                dipToPixels(resources, 12)
            )
        )
    }

    private fun drawMountBoundsBorder(canvas: Canvas, paint: Paint, bounds: Rect) {
        val inset = paint.strokeWidth.toInt() / 2
        canvas.drawRect(
            (bounds.left + inset).toFloat(),
            (bounds.top + inset).toFloat(),
            (bounds.right - inset).toFloat(),
            (bounds.bottom - inset).toFloat(),
            paint
        )
    }

    private fun drawMountBoundsCorners(
        canvas: Canvas, paint: Paint, bounds: Rect, cornerLength: Int, cornerWidth: Int
    ) {
        drawCorner(canvas, paint, bounds.left, bounds.top, cornerLength, cornerLength, cornerWidth)
        drawCorner(
            canvas,
            paint,
            bounds.left,
            bounds.bottom,
            cornerLength,
            -cornerLength,
            cornerWidth
        )
        drawCorner(
            canvas,
            paint,
            bounds.right,
            bounds.top,
            -cornerLength,
            cornerLength,
            cornerWidth
        )
        drawCorner(
            canvas,
            paint,
            bounds.right,
            bounds.bottom,
            -cornerLength,
            -cornerLength,
            cornerWidth
        )
    }

    private fun dipToPixels(res: Resources, dips: Int): Int {
        val scale = res.displayMetrics.density
        return (dips * scale + 0.5f).toInt()
    }

    private fun sign(x: Float): Int {
        return if (x >= 0) 1 else -1
    }

    private fun drawCorner(
        c: Canvas, paint: Paint, x: Int, y: Int, dx: Int, dy: Int, cornerWidth: Int
    ) {
        drawCornerLine(c, paint, x, y, x + dx, y + cornerWidth * sign(dy.toFloat()))
        drawCornerLine(c, paint, x, y, x + cornerWidth * sign(dx.toFloat()), y + dy)
    }

    private fun drawCornerLine(
        canvas: Canvas, paint: Paint, left: Int, top: Int, right: Int, bottom: Int
    ) {
        var l = left
        var t = top
        var r = right
        var b = bottom
        if (l > r) {
            val tmp = l
            l = r
            r = tmp
        }
        if (t > b) {
            val tmp = t
            t = b
            b = tmp
        }
        canvas.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), paint)
    }

}