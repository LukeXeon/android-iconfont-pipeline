package open.source.iconfont

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.inspector.WindowInspector
import org.xmlpull.v1.XmlPullParser
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object IconFont {

    private const val RESOURCE_MANAGER_INTERNAL_CLASS =
        "androidx.appcompat.widget.ResourceManagerInternal"
    private const val INFLATE_DELEGATE_CLASS =
        "$RESOURCE_MANAGER_INTERNAL_CLASS\$InflateDelegate"
    private const val ACTIVITY_THREAD_CLASS = "android.app.ActivityThread"
    private const val SYSTEM_PROPERTIES_CLASS = "android.os.SystemProperties"
    private const val CURRENT_APPLICATION_METHOD = "currentApplication"
    private const val GET_METHOD = "get"
    private const val GET_BOOLEAN_METHOD = "getBoolean"
    private const val ADD_DELEGATE_METHOD = "addDelegate"
    private const val CREATE_FROM_XML_INNER_METHOD = "createFromXmlInner"
    private const val ON_ACTIVITY_RESUMED_METHOD = "onActivityResumed"
    private const val ICON_FONT_TAG = "icon-font"
    private const val LAYOUT_PROP = "debug.layout"
    private const val TAG = "IconFont"

    private val applicationLock = arrayOfNulls<Application>(1)
    private val systemPropertiesLock = arrayOfNulls<Any>(1)

    internal val mainThread by lazy { Handler(Looper.getMainLooper()) }

    internal fun runOnMainThread(runnable: Runnable) {
        if (isMainThread) {
            runnable.run()
        } else {
            mainThread.post(runnable)
        }
    }

    internal var application: Application
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
        private set(value) {
            synchronized(applicationLock) {
                applicationLock[0] = value
            }
        }

    internal var isShowingLayoutBounds: Boolean = false
        private set

    internal val isDebuggable: Boolean by lazy { application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    internal val isMainThread: Boolean
        get() = Looper.myLooper() == mainThread.looper

    @SuppressLint("PrivateApi")
    private fun fetchLayoutBoundsVisibility(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return WindowInspector.getGlobalWindowViews().any { it.isShowingLayoutBounds }
        }
        synchronized(systemPropertiesLock) {
            return try {
                val obj = systemPropertiesLock[0]
                if (obj is Unit) {
                    return false
                } else {
                    val method = if (obj == null) {
                        val systemPropertiesClass = Class.forName(
                            SYSTEM_PROPERTIES_CLASS
                        )
                        val method = systemPropertiesClass.getDeclaredMethod(
                            GET_BOOLEAN_METHOD,
                            String::class.java,
                            Boolean::class.java
                        )
                        systemPropertiesLock[0] = method
                        method
                    } else {
                        obj as Method
                    }
                    method.invoke(null, LAYOUT_PROP, false) as Boolean
                }
            } catch (e: Throwable) {
                systemPropertiesLock[0] = Unit
                false
            }
        }
    }

    @JvmStatic
    fun install(c: Context) {
        synchronized(applicationLock) {
            if (applicationLock[0] == null) {
                application = c.applicationContext as Application
            } else {
                return
            }
        }
        val result = runCatching {
            val start = SystemClock.uptimeMillis()
            val clazz = Class.forName(RESOURCE_MANAGER_INTERNAL_CLASS)
            val getMethod = clazz.getDeclaredMethod(GET_METHOD)
                .apply {
                    isAccessible = true
                }
            val instance = getMethod.invoke(null)!!
            val delegateClazz = Class.forName(INFLATE_DELEGATE_CLASS)
            val addDelegateMethod = clazz.getDeclaredMethod(
                ADD_DELEGATE_METHOD,
                String::class.java,
                delegateClazz
            ).apply {
                isAccessible = true
            }
            val proxy = Proxy.newProxyInstance(
                delegateClazz.classLoader,
                arrayOf(delegateClazz, Application.ActivityLifecycleCallbacks::class.java),
                object : InvocationHandler {
                    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                        return when {
                            method.declaringClass == Any::class.java -> method.invoke(
                                this,
                                args
                            )
                            method.name == CREATE_FROM_XML_INNER_METHOD && !args.isNullOrEmpty() -> {
                                val context = args[0] as Context
                                val parser = args[1] as XmlPullParser
                                val attrs = args[2] as AttributeSet
                                val theme = args[3] as? Resources.Theme
                                IconTextDrawable.createFromXmlInner(context, parser, attrs, theme)
                            }
                            method.name == ON_ACTIVITY_RESUMED_METHOD -> {
                                if (isDebuggable) {
                                    isShowingLayoutBounds = fetchLayoutBoundsVisibility()
                                }
                                null
                            }
                            else -> null
                        }
                    }
                }
            )
            synchronized(instance) {
                addDelegateMethod.invoke(instance, ICON_FONT_TAG, proxy)
            }
            application.registerActivityLifecycleCallbacks(proxy as Application.ActivityLifecycleCallbacks)
            Log.d(TAG, "install use time:" + (SystemClock.uptimeMillis() - start))
        }
        if (!result.isSuccess && isDebuggable) {
            throw ExceptionInInitializerError("️警告，${RESOURCE_MANAGER_INTERNAL_CLASS}已经发生break change，需要修改IconFont SDK代码做进一步适配，此异常只在Debug包抛出")
                .apply { initCause(result.exceptionOrNull()) }
        }
    }
}