package open.source.iconfont

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.XmlRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.collection.ArrayMap
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.*

fun Context.getAppCompatDrawable(@XmlRes @DrawableRes resId: Int): Drawable? {
    return AppCompatResources.getDrawable(this, resId)
}

fun Resources.getAppCompatDrawable(@XmlRes @DrawableRes resId: Int): Drawable? {
    return AppCompatResources.getDrawable(InflateContext.obtain(this), resId)
}

private const val DEFAULT_ASSET_TYPEFACE = "icon_font/typeface.ttf"
private const val DEFAULT_ASSET_METADATA = "icon_font/metadata.json"
private val assetTypefaceLock = arrayOfNulls<WeakReference<Typeface>>(1)
private val assetMetadataLock = arrayOfNulls<Map<String, Char>>(1)

internal val AssetManager.assetTypeface: Typeface?
    get() {
        return synchronized(assetTypefaceLock) {
            var typeface = assetTypefaceLock[0]?.get()
            if (typeface == null) {
                typeface = runCatching {
                    Typeface.createFromAsset(this, DEFAULT_ASSET_TYPEFACE)
                }.getOrNull()
                if (typeface != null) {
                    assetTypefaceLock[0] = WeakReference(typeface)
                }
            }
            return@synchronized typeface
        }
    }

private val AssetManager.assetMetadata: Map<String, Char>
    get() {
        return synchronized(assetMetadataLock) {
            var metadata = assetMetadataLock[0]
            if (metadata == null) {
                metadata = runCatching {
                    val text = open(DEFAULT_ASSET_METADATA).use {
                        it.reader().readText()
                    }
                    val json = JSONObject(text)
                    json.getJSONObject(IconFontMetadata::icons.name)
                        .keys()
                        .asSequence()
                        .map {
                            it to json.getString(it).toInt(16).toChar()
                        }.toMap(ArrayMap(json.length()))
                }.getOrNull()
                assetMetadataLock[0] = metadata
            }
            return@synchronized metadata
        } ?: Collections.emptyMap()
    }

val AssetManager.assetIconNames: Set<String>
    get() = assetMetadata.keys

fun Context.getAssetIconDrawable(name: String): IconTextDrawable? {
    val c = assets.assetMetadata[name]
    val t = assets.assetTypeface
    return if (c != null && t != null) {
        IconTextDrawable().apply {
            code = c
            typeface = t
        }
    } else {
        null
    }
}

fun Resources.getAssetIconDrawable(name: String): IconTextDrawable? {
    val c = assets.assetMetadata[name]
    val t = assets.assetTypeface
    return if (c != null && t != null) {
        IconTextDrawable().apply {
            code = c
            typeface = t
        }
    } else {
        null
    }
}