package com.web2app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.json.JSONArray

/**
 * Lucide icon set (https://lucide.dev), bundled as the `lucide.ttf` icon font plus
 * a name/codepoint/tags index in `assets/lucide_icons.json`. Tab icons reference a
 * Lucide icon as `lucide:<name>` (e.g. `lucide:house`); glyphs are rendered to a
 * tintable [Drawable] on demand, so all ~1900 icons cost one font file, not 1900 XMLs.
 */
object LucideIcons {

    const val PREFIX = "lucide:"

    data class Icon(val name: String, val code: String, val tags: String)

    private var icons: List<Icon>? = null
    private var byName: Map<String, Icon>? = null
    private var cachedTypeface: Typeface? = null

    fun isLucide(icon: String): Boolean = icon.startsWith(PREFIX)

    fun name(icon: String): String = icon.removePrefix(PREFIX)

    fun typeface(context: Context): Typeface =
        cachedTypeface ?: Typeface.createFromAsset(context.assets, "lucide.ttf")
            .also { cachedTypeface = it }

    fun all(context: Context): List<Icon> {
        ensureLoaded(context)
        return icons!!
    }

    /** Icons whose name or tags contain [query] (case-insensitive); all when blank. */
    fun search(context: Context, query: String): List<Icon> {
        ensureLoaded(context)
        val q = query.trim().lowercase()
        if (q.isEmpty()) return icons!!
        return icons!!.filter { it.name.contains(q) || it.tags.contains(q) }
    }

    /** The glyph string for a Lucide icon name, or null if unknown. */
    fun glyph(context: Context, name: String): String? {
        ensureLoaded(context)
        val code = byName!![name]?.code ?: return null
        return String(Character.toChars(code.toInt(16)))
    }

    /** Renders [name] to a [sizePx]² drawable in [color]. Tintable (alpha glyph). */
    fun drawable(context: Context, name: String, sizePx: Int, color: Int): Drawable? {
        val g = glyph(context, name) ?: return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface(context)
            textSize = sizePx * 0.92f
            this.color = color
            textAlign = Paint.Align.CENTER
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fm = paint.fontMetrics
        canvas.drawText(g, sizePx / 2f, sizePx / 2f - (fm.ascent + fm.descent) / 2f, paint)
        return BitmapDrawable(context.resources, bmp)
    }

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (icons != null) return
        val text = context.assets.open("lucide_icons.json")
            .bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        val list = ArrayList<Icon>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.getJSONArray(i)
            list.add(Icon(a.getString(0), a.getString(1), a.optString(2, "")))
        }
        icons = list
        byName = list.associateBy { it.name }
    }
}
