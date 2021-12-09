package open.source.iconfont

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.content.res.TypedArray
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.Orientation
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.util.Xml
import androidx.annotation.*
import androidx.collection.LruCache
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.TintAwareDrawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.lang.ref.WeakReference
import kotlin.math.min

@SuppressLint("RestrictedApi", "MemberVisibilityCanBePrivate")
class IconTextDrawable : Drawable, TintAwareDrawable {

    companion object {

        private const val TAG = "IconTextDrawable"

        private const val FONT_RES_TYPE = "font"

        private const val GRADIENT_TAG = "gradient"

        private const val CODE_CACHE_SIZE = 128

        private val codes = object : LruCache<String, String>(CODE_CACHE_SIZE) {
            override fun create(key: String): String {
                return key.toInt(16).toChar().toString()
            }
        }

        @JvmStatic
        fun create(
            context: Context,
            @DrawableRes @XmlRes resId: Int,
            theme: Theme?
        ): IconTextDrawable? {
            try {
                val parser = context.resources.getXml(resId)
                val attrs = Xml.asAttributeSet(parser)
                var type: Int
                while (parser.next().also { type = it } != XmlPullParser.START_TAG &&
                    type != XmlPullParser.END_DOCUMENT) {
                    // Empty loop
                }
                if (type != XmlPullParser.START_TAG) {
                    throw XmlPullParserException("No start tag found")
                }
                return createFromXmlInner(context, parser, attrs, theme)
            } catch (e: XmlPullParserException) {
                Log.e(TAG, "parser error", e)
            } catch (e: IOException) {
                Log.e(TAG, "parser error", e)
            }
            return null
        }

        @JvmStatic
        fun createFromXmlInner(
            context: Context,
            parser: XmlPullParser,
            attrs: AttributeSet,
            theme: Theme?
        ): IconTextDrawable {
            return IconTextDrawable().apply { inflate(context, parser, attrs, theme) }
        }

        /**
         * Parses a [android.graphics.PorterDuff.Mode] from a tintMode
         * attribute's enum value.
         */
        private fun parseTintMode(value: Int): PorterDuff.Mode {
            return when (value) {
                3 -> PorterDuff.Mode.SRC_OVER
                5 -> PorterDuff.Mode.SRC_IN
                9 -> PorterDuff.Mode.SRC_ATOP
                14 -> PorterDuff.Mode.MULTIPLY
                15 -> PorterDuff.Mode.SCREEN
                16 -> PorterDuff.Mode.ADD
                else -> PorterDuff.Mode.SRC_IN
            }
        }

        /**
         * Obtains styled attributes from the theme, if available, or unstyled
         * resources if the theme is null.
         */
        private fun obtainAttributes(
            res: Resources,
            theme: Theme?,
            set: AttributeSet,
            attrs: IntArray
        ): TypedArray {
            if (theme != null) {
                return theme.obtainStyledAttributes(set, attrs, 0, 0)
            }
            return res.obtainAttributes(
                set,
                attrs
            )
        }

        private fun TypedArray.getFloatOrFraction(index: Int, defaultValue: Float): Float {
            val tv = peekValue(index)
            var v = defaultValue
            if (tv != null) {
                val vIsFraction = tv.type == TypedValue.TYPE_FRACTION
                v = if (vIsFraction) tv.getFraction(1.0f, 1.0f) else tv.float
            }
            return v
        }

        private val IntArray.hasCenterColor: Boolean
            get() {
                return size == 3
            }
    }

    private val updateLock = arrayOfNulls<UpdateCallback>(1)
    private val textBounds = Rect()
    private val gradientBounds = RectF()
    private var tintFilter: ColorFilter? = null
    private var mutated = false
    private var gradientShader: Shader? = null
    private var gradientIsDirty: Boolean = true
    private var state: IconTextState

    private constructor(
        otherState: IconTextState
    ) : super() {
        state = IconTextState(otherState)
        tintFilter = updateTintFilter()
    }

    constructor() : super() {
        state = IconTextState()
    }

    @IntDef(
        GradientDrawable.LINEAR_GRADIENT,
        GradientDrawable.RADIAL_GRADIENT,
        GradientDrawable.SWEEP_GRADIENT
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class GradientType

    private class IconTextState() : ConstantState() {

        @Px
        var size: Int = -1

        @Size(min = 1, max = 1)
        var text: String? = null
        var tint: ColorStateList? = null
        var tintMode: PorterDuff.Mode? = null

        @Px
        var shadowDx: Float = 0f

        @Px
        var shadowDy: Float = 0f

        @ColorInt
        var shadowColor: Int = Color.TRANSPARENT

        @Px
        var shadowRadius: Float = 0f
        val paint = Paint()
        val padding = Rect()

        //gradient
        var centerX: Float = 0f
        var centerY: Float = 0f
        var useLevel: Boolean = false
        var angle: Int = 0
        var orientation = Orientation.TOP_BOTTOM

        @ColorInt
        var gradientColors: IntArray? = null

        @ColorInt
        var tempGradientColors: IntArray? = null
        var positions: FloatArray? = null
        var tempPositions: FloatArray? = null

        @GradientType
        var gradientType: Int = GradientDrawable.LINEAR_GRADIENT
        var gradientRadius: Float = 0f

        init {
            paint.isFakeBoldText = false
            paint.textSkewX = 0f
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.isUnderlineText = false
            paint.color = Color.WHITE
            paint.isAntiAlias = true
            paint.isDither = true
        }

        constructor(
            other: IconTextState
        ) : this() {
            size = other.size
            text = other.text
            tint = other.tint
            tintMode = other.tintMode
            shadowDx = other.shadowDx
            shadowDy = other.shadowDy
            shadowColor = other.shadowColor
            shadowRadius = other.shadowRadius
            paint.set(other.paint)
            padding.set(other.padding)
            centerX = other.centerX
            centerY = other.centerY
            useLevel = other.useLevel
            angle = other.angle
            orientation = other.orientation
            gradientColors = other.gradientColors
            positions = other.positions
            gradientType = other.gradientType
            gradientRadius = other.gradientRadius
        }

        override fun newDrawable(): Drawable {
            return IconTextDrawable(this)
        }

        override fun getChangingConfigurations(): Int = 0

    }

    private class UpdateCallback(drawable: IconTextDrawable) : ResourcesCompat.FontCallback() {

        @Volatile
        var drawable: WeakReference<IconTextDrawable>? = WeakReference(drawable)

        private fun applyTypeface(typeface: Typeface?) {
            val drawable = drawable?.get() ?: return
            synchronized(drawable.updateLock) {
                val callback = drawable.updateLock[0]
                if (callback == this) {
                    drawable.updateLock[0] = null
                    drawable.typeface = typeface
                }
            }
        }

        override fun onFontRetrieved(typeface: Typeface) {
            applyTypeface(typeface)
        }

        override fun onFontRetrievalFailed(reason: Int) {
            applyTypeface(null)
        }
    }

    override fun getConstantState(): ConstantState {
        return state
    }

    override fun mutate(): Drawable {
        if (!mutated && super.mutate() == this) {
            state = IconTextState(state)
            mutated = true
        }
        return this
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val height = bounds.height()
        val text = state.text
        if (height > 0 && !text.isNullOrEmpty() && ensureValidRect()) {
            state.paint.shader = gradientShader
            val colorFilter = state.paint.colorFilter
            if (colorFilter == null) {
                state.paint.colorFilter = tintFilter
            }
            state.paint.textSize = height.toFloat()
            state.paint.getTextBounds(state.text, 0, 1, textBounds)
            val textHeight = textBounds.height()
            val textBottom =
                bounds.top + (height - textHeight) / 2f + textHeight - textBounds.bottom
            canvas.drawText(text, bounds.exactCenterX(), textBottom, state.paint)
            state.paint.colorFilter = colorFilter
            state.paint.shader = null
        }
        DebugDraw.draw(this, canvas)
    }

    override fun setAlpha(alpha: Int) {
        state.paint.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int {
        return state.paint.alpha
    }

    override fun inflate(
        r: Resources,
        parser: XmlPullParser,
        attrs: AttributeSet
    ) {
        inflate(r, parser, attrs, null)
    }

    override fun inflate(
        r: Resources,
        parser: XmlPullParser,
        attrs: AttributeSet,
        theme: Theme?
    ) {
        inflate(InflateContext.obtain(r), parser, attrs, theme)
    }

    fun inflate(
        context: Context,
        parser: XmlPullParser,
        attrs: AttributeSet,
        theme: Resources.Theme?
    ) {
        val array = obtainAttributes(context.resources, theme, attrs, R.styleable.IconTextDrawable)
        try {
            setVisible(array.getBoolean(R.styleable.IconTextDrawable_visible, true), false)
            val text = array.getString(R.styleable.IconTextDrawable_code)
            if (!text.isNullOrEmpty()) {
                state.text = codes[text]
            } else {
                throw XmlPullParserException("<icon-font> tag requires code not null")
            }
            val fontId = array.getResourceId(R.styleable.IconTextDrawable_font, 0)
            synchronized(updateLock) {
                var callback = updateLock[0]
                updateLock[0] = null
                callback?.drawable = null
                if (fontId != 0 && context.resources.getResourceTypeName(fontId) == FONT_RES_TYPE) {
                    callback = UpdateCallback(this)
                    updateLock[0] = callback
                    ResourcesCompat.getFont(
                        context,
                        fontId,
                        callback,
                        AppCompatUtils.mainThread
                    )
                } else {
                    state.paint.typeface = context.assets.assetTypeface
                }
            }
            state.paint.color = array.getColor(
                R.styleable.IconTextDrawable_color,
                Color.WHITE
            )
            val tintMode = array.getInt(R.styleable.IconTextDrawable_tintMode, -1)
            if (tintMode != -1) {
                state.tintMode = parseTintMode(tintMode)
            }
            val tint = array.getColorStateList(R.styleable.IconTextDrawable_tint)
            if (tint != null) {
                state.tint = tint
            }
            state.size = if (array.hasValue(R.styleable.IconTextDrawable_intrinsicSize)) {
                array.getDimensionPixelSize(
                    R.styleable.IconTextDrawable_intrinsicSize,
                    0
                )
            } else {
                -1
            }
            state.paint.alpha = array.getInt(R.styleable.IconTextDrawable_alpha, 255)
            state.shadowDx = array.getFloat(R.styleable.IconTextDrawable_shadowDx, 0f)
            state.shadowDy = array.getFloat(R.styleable.IconTextDrawable_shadowDy, 0f)
            state.shadowRadius = array.getFloat(R.styleable.IconTextDrawable_shadowRadius, 0f)
            state.shadowColor = array.getColor(
                R.styleable.IconTextDrawable_shadowColor,
                Color.TRANSPARENT
            )
            state.paint.setShadowLayer(
                state.shadowRadius,
                state.shadowDx,
                state.shadowDy,
                state.shadowColor
            )
            state.padding.set(
                array.getDimensionPixelSize(
                    R.styleable.IconTextDrawable_paddingLeft,
                    min(0, state.padding.left)
                ),
                array.getDimensionPixelSize(
                    R.styleable.IconTextDrawable_paddingTop,
                    min(0, state.padding.top)
                ),
                array.getDimensionPixelSize(
                    R.styleable.IconTextDrawable_paddingRight,
                    min(0, state.padding.right)
                ),
                array.getDimensionPixelSize(
                    R.styleable.IconTextDrawable_paddingBottom,
                    min(0, state.padding.bottom)
                )
            )
            tintFilter = updateTintFilter()
        } catch (e: NumberFormatException) {
            throw XmlPullParserException("code is illegal", parser, e)
        } finally {
            array.recycle()
        }
        inflateChildElements(context.resources, parser, attrs, theme)
    }

    private fun inflateChildElements(
        r: Resources,
        parser: XmlPullParser,
        attrs: AttributeSet,
        theme: Theme?
    ) {
        var type: Int
        val innerDepth = parser.depth + 1
        var depth = 0
        while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT
            && (parser.depth.also { depth = it } >= innerDepth
                    || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG) {
                continue
            }
            if (depth > innerDepth) {
                continue
            }
            val name = parser.name
            if (name == GRADIENT_TAG) {
                val a = obtainAttributes(r, theme, attrs, R.styleable.IconTextDrawable_Gradient)
                updateGradientDrawableGradient(r, a)
                a.recycle()
            } else {
                Log.w(TAG, "Bad element under <icon-font>: $name")
            }
        }
    }

    private fun updateGradientDrawableGradient(r: Resources, a: TypedArray) {
        state.centerX = a.getFloatOrFraction(
            R.styleable.IconTextDrawable_Gradient_centerX,
            state.centerX
        )
        state.centerY = a.getFloatOrFraction(
            R.styleable.IconTextDrawable_Gradient_centerY,
            state.centerY
        )
        state.useLevel = a.getBoolean(
            R.styleable.IconTextDrawable_Gradient_useLevel,
            state.useLevel
        )
        state.gradientType = a.getInt(
            R.styleable.IconTextDrawable_Gradient_type,
            state.gradientType
        )
        var hasGradientCenter = false
        var prevStart = 0
        var prevCenter = 0
        var prevEnd = 0
        val gradientColors = state.gradientColors
        if (gradientColors != null) {
            if (gradientColors.hasCenterColor) {
                hasGradientCenter = true
                prevStart = gradientColors[0]
                prevCenter = gradientColors[1]
                prevEnd = gradientColors[2]
            } else {
                prevStart = gradientColors.first()
                prevEnd = gradientColors.last()
            }
        }
        val startColor = a.getColor(
            R.styleable.IconTextDrawable_Gradient_startColor,
            prevStart
        )
        val hasCenterColor = a.hasValue(
            R.styleable.IconTextDrawable_Gradient_centerColor
        ) || hasGradientCenter
        val centerColor = a.getColor(
            R.styleable.IconTextDrawable_Gradient_centerColor,
            prevCenter
        )
        val endColor = a.getColor(
            R.styleable.IconTextDrawable_Gradient_endColor,
            prevEnd
        )
        if (hasCenterColor) {
            state.gradientColors = intArrayOf(startColor, centerColor, endColor)
            state.positions = floatArrayOf(
                0f,
                if (state.centerX != 0.5f) state.centerX else state.centerY,
                1f
            )
        } else {
            state.gradientColors = intArrayOf(startColor, endColor)
        }
        val angle = a.getFloat(
            R.styleable.IconTextDrawable_Gradient_angle,
            state.angle.toFloat()
        ).toInt()
        state.angle = (angle % 360 + 360) % 360
        if (state.angle >= 0) {
            when (state.angle) {
                0 -> state.orientation = Orientation.LEFT_RIGHT
                45 -> state.orientation = Orientation.BL_TR
                90 -> state.orientation = Orientation.BOTTOM_TOP
                135 -> state.orientation = Orientation.BR_TL
                180 -> state.orientation = Orientation.RIGHT_LEFT
                225 -> state.orientation = Orientation.TR_BL
                270 -> state.orientation = Orientation.TOP_BOTTOM
                315 -> state.orientation = Orientation.TL_BR
            }
        } else {
            state.orientation = Orientation.TOP_BOTTOM
        }
        val tv = a.peekValue(R.styleable.IconTextDrawable_Gradient_gradientRadius)
        if (tv != null) {
            val radius = if (tv.type == TypedValue.TYPE_DIMENSION) {
                tv.getDimension(r.displayMetrics)
            } else {
                tv.float
            }
            state.gradientRadius = radius
        }
    }

    /**
     * This checks mGradientIsDirty, and if it is true, recomputes both our drawing
     * rectangle (mRect) and the gradient itself, since it depends on our
     * rectangle too.
     * @return true if the resulting rectangle is not empty, false otherwise
     */
    private fun ensureValidRect(): Boolean {
        if (gradientIsDirty) {
            gradientIsDirty = false
            val rect = gradientBounds
            gradientBounds.set(bounds)
            val gradientColors = state.gradientColors
            if (gradientColors != null) {
                val r: RectF = rect
                val x0: Float
                val x1: Float
                val y0: Float
                val y1: Float
                when (state.gradientType) {
                    GradientDrawable.LINEAR_GRADIENT -> {
                        val level = if (state.useLevel) level / 10000.0f else 1.0f
                        when (state.orientation) {
                            Orientation.TOP_BOTTOM -> {
                                x0 = r.left
                                y0 = r.top
                                x1 = x0
                                y1 = level * r.bottom
                            }
                            Orientation.TR_BL -> {
                                x0 = r.right
                                y0 = r.top
                                x1 = level * r.left
                                y1 = level * r.bottom
                            }
                            Orientation.RIGHT_LEFT -> {
                                x0 = r.right
                                y0 = r.top
                                x1 = level * r.left
                                y1 = y0
                            }
                            Orientation.BR_TL -> {
                                x0 = r.right
                                y0 = r.bottom
                                x1 = level * r.left
                                y1 = level * r.top
                            }
                            Orientation.BOTTOM_TOP -> {
                                x0 = r.left
                                y0 = r.bottom
                                x1 = x0
                                y1 = level * r.top
                            }
                            Orientation.BL_TR -> {
                                x0 = r.left
                                y0 = r.bottom
                                x1 = level * r.right
                                y1 = level * r.top
                            }
                            Orientation.LEFT_RIGHT -> {
                                x0 = r.left
                                y0 = r.top
                                x1 = level * r.right
                                y1 = y0
                            }
                            else -> {
                                x0 = r.left
                                y0 = r.top
                                x1 = level * r.right
                                y1 = level * r.bottom
                            }
                        }
                        gradientShader = LinearGradient(
                            x0, y0, x1, y1,
                            gradientColors,
                            state.positions,
                            Shader.TileMode.CLAMP
                        )
                    }
                    GradientDrawable.RADIAL_GRADIENT -> {
                        x0 = r.left + (r.right - r.left) * state.centerX
                        y0 = r.top + (r.bottom - r.top) * state.centerY
                        var radius: Float = state.gradientRadius
                        if (state.useLevel) {
                            radius *= level / 10000.0f
                        }
                        gradientRadius = radius
                        if (radius <= 0) {
                            // We can't have a shader with non-positive radius, so
                            // let's have a very, very small radius.
                            radius = 0.001f
                        }
                        gradientShader = RadialGradient(
                            x0, y0, radius, gradientColors, null, Shader.TileMode.CLAMP
                        )
                    }
                    GradientDrawable.SWEEP_GRADIENT -> {
                        x0 = r.left + (r.right - r.left) * state.centerX
                        y0 = r.top + (r.bottom - r.top) * state.centerY
                        var tempColors = gradientColors
                        var tempPositions: FloatArray? = null
                        if (state.useLevel) {
                            tempColors = state.tempGradientColors
                            val length = gradientColors.size
                            if (tempColors == null || tempColors.size != length + 1) {
                                tempColors = IntArray(length + 1)
                                state.tempGradientColors = tempColors
                            }
                            System.arraycopy(gradientColors, 0, tempColors, 0, length)
                            tempColors[length] = gradientColors[length - 1]
                            tempPositions = state.tempPositions
                            val fraction = 1.0f / (length - 1)
                            if (tempPositions == null || tempPositions.size != length + 1) {
                                tempPositions = FloatArray(length + 1)
                                state.tempPositions = tempPositions
                            }
                            val level = level / 10000.0f
                            for (i in 0 until length) {
                                tempPositions[i] = i * fraction * level
                            }
                            tempPositions[length] = 1.0f
                        }
                        gradientShader = SweepGradient(x0, y0, tempColors, tempPositions)
                    }
                }
            }
        }
        return !bounds.isEmpty
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        state.paint.colorFilter = colorFilter
        invalidateSelf()
    }

    private fun updateTintFilter(): ColorFilter? {
        val mode = state.tintMode
        val color = state.tint
        return if (mode == null || color == null) {
            null
        } else {
            PorterDuffColorFilter(
                color.getColorForState(getState(), Color.TRANSPARENT),
                mode
            )
        }
    }

    override fun setTint(@ColorInt tintColor: Int) {
        setTintList(ColorStateList.valueOf(tintColor))
    }

    override fun setTintList(tint: ColorStateList?) {
        state.tint = tint
        tintFilter = updateTintFilter()
        invalidateSelf()
    }

    override fun onStateChange(state: IntArray?): Boolean {
        val filter = updateTintFilter()
        if (filter != tintFilter) {
            tintFilter = filter
            return true
        }
        return false
    }

    override fun isStateful(): Boolean {
        return state.tint?.isStateful ?: false
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        state.tintMode = tintMode
        tintFilter = updateTintFilter()
        invalidateSelf()
    }

    @Px
    override fun getIntrinsicHeight(): Int {
        return intrinsicSize
    }

    @Px
    override fun getIntrinsicWidth(): Int {
        return intrinsicSize
    }

    var color: Int
        @ColorInt
        get() = state.paint.color
        set(@ColorInt value) {
            state.paint.color = value
            invalidateSelf()
        }

    var typeface: Typeface?
        get() = state.paint.typeface
        set(value) {
            synchronized(updateLock) {
                updateLock[0]?.drawable = null
                updateLock[0] = null
                state.paint.typeface = value
            }
            invalidateSelf()
        }

    var code: Char?
        get() = state.text?.get(0)
        set(value) {
            state.text = value?.toString()
            invalidateSelf()
        }

    var intrinsicSize: Int
        @Px
        get() = state.size
        set(@Px value) {
            state.size = value
        }

    var shadowDx: Float
        @Px
        get() = state.shadowDx
        set(@Px value) {
            state.shadowDx = value
            state.paint.setShadowLayer(state.shadowRadius, value, state.shadowDy, state.shadowColor)
            invalidateSelf()
        }
    var shadowDy: Float
        @Px
        get() = state.shadowDy
        set(@Px value) {
            state.shadowDy = value
            state.paint.setShadowLayer(state.shadowRadius, state.shadowDx, value, state.shadowColor)
            invalidateSelf()
        }
    var shadowColor: Int
        @ColorInt
        get() = state.shadowColor
        set(@ColorInt value) {
            state.shadowColor = value
            state.paint.setShadowLayer(state.shadowRadius, state.shadowDx, state.shadowDy, value)
            invalidateSelf()
        }
    var shadowRadius: Float
        @Px
        get() = state.shadowRadius
        set(@Px value) {
            state.shadowRadius = value
            state.paint.setShadowLayer(value, state.shadowDx, state.shadowDy, state.shadowColor)
            invalidateSelf()
        }

    var gradientRadius: Float = 0f
        @Px
        get() {
            if (state.gradientType != GradientDrawable.RADIAL_GRADIENT) {
                return 0f
            }
            ensureValidRect()
            return field
        }
        set(@Px value) {
            if (Throwable().stackTrace.first().className != IconTextDrawable::class.java.name) {
                state.gradientRadius = value
                gradientIsDirty = true
                invalidateSelf()
            } else {
                field = value
            }
        }

    var gradientType: Int
        @GradientType
        get() {
            return state.gradientType
        }
        set(@GradientType value) {
            state.gradientType = value
            gradientIsDirty = true
            invalidateSelf()
        }

    var colors: IntArray
        @ColorInt
        get() {
            val colors = state.gradientColors
            return if (colors == null || colors.isEmpty()) {
                intArrayOf(state.paint.color)
            } else {
                colors.copyOf()
            }
        }
        set(@ColorInt value) {
            state.gradientColors = value.copyOf()
            gradientIsDirty = true
            invalidateSelf()
        }

    fun setColors(@ColorInt colors: IntArray, offset: FloatArray) {
        state.gradientColors = colors.copyOf()
        state.positions = offset.copyOf()
        gradientIsDirty = true
        invalidateSelf()
    }

    var orientation: Orientation
        get() = state.orientation
        set(value) {
            state.orientation = value
            gradientIsDirty = true
            invalidateSelf()
        }

    var useLevel: Boolean
        get() = state.useLevel
        set(value) {
            state.useLevel = value
            gradientIsDirty = true
            invalidateSelf()
        }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun onLevelChange(level: Int): Boolean {
        gradientIsDirty = true
        invalidateSelf()
        return true
    }

    override fun onBoundsChange(bounds: Rect?) {
        gradientIsDirty = true
        invalidateSelf()
    }

    fun setPadding(padding: Rect) {
        state.padding.set(padding)
        invalidateSelf()
    }

    override fun getPadding(padding: Rect): Boolean {
        padding.set(state.padding)
        return true
    }
}