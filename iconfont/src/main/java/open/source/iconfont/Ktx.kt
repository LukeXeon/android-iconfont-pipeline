package open.source.iconfont

import android.content.Context
import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.XmlRes
import androidx.appcompat.content.res.AppCompatResources

fun Context.getAppCompatDrawable(@XmlRes @DrawableRes resId: Int) {
    AppCompatResources.getDrawable(this, resId)
}

fun Resources.getAppCompatDrawable(@XmlRes @DrawableRes resId: Int) {
    AppCompatResources.getDrawable(InflateContext.obtain(this), resId)
}