package open.source.iconfont

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.io.File
import java.util.*
import kotlin.math.min

/**
 * 用于绘制IconFont的边界，这个类应该保证只在Debug包才工作
 */
@SuppressLint("MemberVisibilityCanBePrivate")
class DebugDraw : DrawableExtension {

    companion object : LifecycleEventObserver {
        private const val BORDER_COLOR = -0x66010000
        private const val CORNER_COLOR = -0xffff01
        private val checker by lazy {
            File(ApplicationContext.current.cacheDir, DebugDraw::class.java.name)
        }
        private val drawables by lazy {
            Collections.newSetFromMap(WeakHashMap<Drawable, Boolean>())
        }
        private val invalidateRunnable = Runnable {
            for (drawable in drawables) {
                drawable.invalidateSelf()
            }
        }

        @JvmStatic
        var isAlwaysShowLayoutBounds: Boolean
            get() = checker.exists()
            set(value) {
                if (value) {
                    checker.createNewFile()
                } else {
                    checker.delete()
                }
                MainThread.execute(invalidateRunnable)
            }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_RESUME) {
                invalidateRunnable.run()
            }
        }

        init {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }
    }

    private lateinit var mountBoundsRect: Rect
    private lateinit var mountBoundsBorderPaint: Paint
    private lateinit var mountBoundsCornerPaint: Paint

    override fun draw(drawable: Drawable, canvas: Canvas) {
        if (ApplicationContext.isDebuggable && MainThread.isMainThread) {
            drawables.add(drawable)
            if (isAlwaysShowLayoutBounds || ViewCompat.isShowingLayoutBounds) {
                highlightMountBounds(drawable, canvas)
            }
        }
    }

    private fun highlightMountBounds(host: Drawable, canvas: Canvas) {
        val dm = ApplicationContext.current.resources.displayMetrics
        if (!::mountBoundsRect.isInitialized) {
            mountBoundsRect = Rect()
        }
        if (!::mountBoundsBorderPaint.isInitialized) {
            mountBoundsBorderPaint = Paint()
            mountBoundsBorderPaint.style = Paint.Style.STROKE
            mountBoundsBorderPaint.strokeWidth = dipToPixels(dm, 1).toFloat()
        }
        if (!::mountBoundsCornerPaint.isInitialized) {
            mountBoundsCornerPaint = Paint()
            mountBoundsCornerPaint.style = Paint.Style.FILL
            mountBoundsCornerPaint.strokeWidth = dipToPixels(dm, 2).toFloat()
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
                dipToPixels(dm, 12)
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

    private fun dipToPixels(dm: DisplayMetrics, dips: Int): Int {
        val scale = dm.density
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