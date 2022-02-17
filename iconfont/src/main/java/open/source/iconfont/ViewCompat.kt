package open.source.iconfont

import android.annotation.SuppressLint
import android.os.Build
import android.view.inspector.WindowInspector
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.lang.reflect.Method

internal object ViewCompat {

    private val impl by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ViewCompatImplApiR
        } else {
            ViewCompatImplLowApi
        }
    }

    val isShowingLayoutBounds: Boolean
        get() = impl.isShowingLayoutBounds

    private interface ViewCompatImpl {
        val isShowingLayoutBounds: Boolean
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private object ViewCompatImplApiR : ViewCompatImpl, LifecycleEventObserver {
        private val stateLock = arrayOf(false)

        init {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }

        override val isShowingLayoutBounds: Boolean
            get() {
                synchronized(stateLock) {
                    return stateLock[0]
                }
            }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_RESUME) {
                val views = WindowInspector.getGlobalWindowViews()
                if (views.isNotEmpty()) {
                    synchronized(stateLock) {
                        stateLock[0] = WindowInspector.getGlobalWindowViews()
                            .any { it.isShowingLayoutBounds }
                    }
                } else {
                    synchronized(stateLock) {
                        stateLock[0] = false
                    }
                }
            }
        }
    }

    @SuppressLint("PrivateApi")
    private object ViewCompatImplLowApi : ViewCompatImpl {

        private val systemPropertiesLock = arrayOfNulls<Any>(1)

        override val isShowingLayoutBounds: Boolean
            get() {
                synchronized(systemPropertiesLock) {
                    return try {
                        val obj = systemPropertiesLock[0]
                        if (obj is Unit) {
                            return false
                        } else {
                            val method = if (obj == null) {
                                val systemPropertiesClass = Class
                                    .forName("android.os.SystemProperties")
                                val method = systemPropertiesClass.getDeclaredMethod(
                                    "getBoolean",
                                    String::class.java,
                                    Boolean::class.java
                                )
                                systemPropertiesLock[0] = method
                                method
                            } else {
                                obj as Method
                            }
                            method.invoke(null, "debug.layout", false) as Boolean
                        }
                    } catch (e: Throwable) {
                        systemPropertiesLock[0] = Unit
                        false
                    }
                }
            }

    }
}