package open.source.iconfont

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.inspector.WindowInspector
import androidx.annotation.RequiresApi
import java.lang.reflect.Method

object ViewCompat {

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
    private object ViewCompatImplApiR : ViewCompatImpl {
        private val stateLock = arrayOfNulls<Boolean>(1)

        override val isShowingLayoutBounds: Boolean
            get() {
                synchronized(stateLock) {
                    var value = stateLock[0]
                    if (value == null) {
                        val views = WindowInspector.getGlobalWindowViews()
                        if (views.isEmpty()) {
                            return false
                        } else {
                            ApplicationUtils.application.registerActivityLifecycleCallbacks(object :
                                Application.ActivityLifecycleCallbacks {
                                override fun onActivityCreated(
                                    activity: Activity,
                                    savedInstanceState: Bundle?
                                ) {

                                }

                                override fun onActivityStarted(activity: Activity) {

                                }

                                override fun onActivityResumed(activity: Activity) {
                                    synchronized(stateLock) {
                                        stateLock[0] = WindowInspector.getGlobalWindowViews()
                                            .any { it.isShowingLayoutBounds }
                                    }
                                }

                                override fun onActivityPaused(activity: Activity) {

                                }

                                override fun onActivityStopped(activity: Activity) {

                                }

                                override fun onActivitySaveInstanceState(
                                    activity: Activity,
                                    outState: Bundle
                                ) {

                                }

                                override fun onActivityDestroyed(activity: Activity) {

                                }
                            })
                            value = views.any { it.isShowingLayoutBounds }
                            stateLock[0] = value
                            return value
                        }
                    } else {
                        return value
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