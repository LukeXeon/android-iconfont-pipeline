package open.source.iconfont

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.XmlRes
import androidx.appcompat.content.res.AppCompatResources

fun Context.getAppCompatDrawable(@XmlRes @DrawableRes resId: Int): Drawable? {
    return AppCompatResources.getDrawable(this, resId)
}

fun Resources.getAppCompatDrawable(@XmlRes @DrawableRes resId: Int): Drawable? {
    return AppCompatResources.getDrawable(InflateContext.obtain(this), resId)
}