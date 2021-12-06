package open.source.iconfont

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import java.lang.ref.WeakReference
import java.util.*

internal class InflateContext(
    base: Context,
    res: Resources
) : ContextWrapper(base) {

    private val reference = WeakReference(res)

    override fun getResources(): Resources {
        return reference.get() ?: super.getResources()
    }

    override fun getAssets(): AssetManager {
        return resources.assets
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