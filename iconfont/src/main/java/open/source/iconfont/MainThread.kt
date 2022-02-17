package open.source.iconfont

import android.os.Handler
import android.os.Looper

internal object MainThread {
    val isMainThread: Boolean
        get() = Looper.myLooper() == handler.looper

    val handler by lazy { Handler(Looper.getMainLooper()) }

    fun post(runnable: Runnable) {
        if (isMainThread) {
            runnable.run()
        } else {
            handler.post(runnable)
        }
    }
}