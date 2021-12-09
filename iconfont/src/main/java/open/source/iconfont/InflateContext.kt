package open.source.iconfont

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import java.lang.ref.WeakReference
import java.util.*

internal class InflateContext(
    base: Context,
    private val res: Resources
) : ContextWrapper(base) {

    override fun getResources(): Resources {
        return res
    }

    override fun getAssets(): AssetManager {
        return resources.assets
    }

    companion object {
        private val caches = LinkedList<WeakReference<InflateContext>>()

        fun obtain(r: Resources): Context {
            synchronized(caches) {
                val it = caches.iterator()
                var context: InflateContext? = null
                loop@ while (it.hasNext()) {
                    context = it.next().get()
                    when {
                        context == null -> {
                            it.remove()
                        }
                        context.res == r -> {
                            break@loop
                        }
                    }
                }
                if (context == null) {
                    context = InflateContext(AppCompatUtils.application, r)
                    caches.add(WeakReference(context))
                }
                return context
            }
        }
    }
}