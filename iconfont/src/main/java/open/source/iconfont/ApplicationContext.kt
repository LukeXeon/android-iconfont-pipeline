package open.source.iconfont

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo

internal object ApplicationContext {
    private const val ACTIVITY_THREAD_CLASS = "android.app.ActivityThread"
    private const val CURRENT_APPLICATION_METHOD = "currentApplication"

    private val applicationLock = arrayOfNulls<Application>(1)

    var current: Application
        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        get() {
            synchronized(applicationLock) {
                var context = applicationLock[0]
                if (context == null) {
                    val clazz = Class.forName(ACTIVITY_THREAD_CLASS)
                    val method = clazz.getDeclaredMethod(CURRENT_APPLICATION_METHOD)
                        .apply {
                            isAccessible = true
                        }
                    context = method.invoke(null) as Application
                    applicationLock[0] = context
                }
                return context
            }
        }
        set(value) {
            synchronized(applicationLock) {
                applicationLock[0] = value
            }
        }

    val isDebuggable: Boolean by lazy { current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
}