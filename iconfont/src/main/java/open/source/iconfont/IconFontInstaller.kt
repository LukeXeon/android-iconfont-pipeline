package open.source.iconfont

import android.content.Context
import androidx.startup.Initializer

internal class IconFontInstaller : Initializer<IconFont> {

    override fun create(context: Context): IconFont {
        IconFont.install(context)
        return IconFont
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}