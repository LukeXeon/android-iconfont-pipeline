package open.source.iconfont

import android.graphics.Canvas
import android.graphics.drawable.Drawable

internal interface DrawableExtension {
    fun draw(drawable: Drawable, canvas: Canvas)
}