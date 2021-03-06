package open.source.iconfont

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.annotation.XmlRes
import androidx.appcompat.content.res.AppCompatResources
import org.xmlpull.v1.XmlPullParser
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

object IconFont {

    private const val RESOURCE_MANAGER_INTERNAL_CLASS =
        "androidx.appcompat.widget.ResourceManagerInternal"
    private const val INFLATE_DELEGATE_CLASS =
        "$RESOURCE_MANAGER_INTERNAL_CLASS\$InflateDelegate"
    private const val GET_METHOD = "get"
    private const val ADD_DELEGATE_METHOD = "addDelegate"
    private const val CREATE_FROM_XML_INNER_METHOD = "createFromXmlInner"
    private const val ICON_FONT_TAG = "icon-font"
    private val isInstalled = AtomicBoolean()

    @JvmStatic
    fun install(c: Context) {
        if (!isInstalled.compareAndSet(false, true)) {
            return
        }
        ApplicationContext.current = c.applicationContext as Application
        val result = runCatching {
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
                arrayOf(delegateClazz),
                object : InvocationHandler {
                    override fun invoke(
                        proxy: Any?,
                        method: Method,
                        args: Array<out Any>?
                    ): Any? {
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
                                IconTextDrawable.createFromXmlInner(
                                    context,
                                    parser,
                                    attrs,
                                    theme
                                )
                            }
                            else -> null
                        }
                    }
                }
            )
            synchronized(instance) {
                addDelegateMethod.invoke(instance, ICON_FONT_TAG, proxy)
            }
        }
        if (!result.isSuccess && ApplicationContext.isDebuggable) {
            throw AssertionError(
                c.getString(
                    R.string.icon_font_appcompat_break_change_warning,
                    RESOURCE_MANAGER_INTERNAL_CLASS
                )
            ).apply { initCause(result.exceptionOrNull()) }
        }
    }

    @JvmStatic
    fun getDrawable(c: Context, @XmlRes @DrawableRes resId: Int): Drawable? {
        return AppCompatResources.getDrawable(c, resId)
    }
}