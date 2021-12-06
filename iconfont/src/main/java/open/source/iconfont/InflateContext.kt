package open.source.iconfont

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import java.util.*

internal class InflateContext(
    base: Context,
    private val res: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources {
        return res
    }

    override fun getAssets(): AssetManager {
        return res.assets
    }

    companion object {
        private val caches = WeakHashMap<Resources, InflateContext>()

        fun obtain(r: Resources): Context {
            synchronized(caches) {
                return caches.getOrPut(r) {
                    InflateContext(AppCompatUtils.application, r)
                }
            }
        }
    }
}