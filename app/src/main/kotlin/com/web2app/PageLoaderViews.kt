package com.web2app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PorterDuff
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.google.android.material.card.MaterialCardView

/**
 * Builds the in-page loading indicator shown while web pages load inside the WebView.
 * Shared by the builder's live preview and the runtime ([MainActivity]) so both render
 * an identical loader. Branding styles embed the app icon and honour [Opts].
 */
object PageLoaderViews {

    const val CIRCULAR = 0
    const val LINEAR = 1
    const val BRAND_RING = 2
    const val BRAND_BAR = 3

    /** Styles 2–3 embed the app icon and are gated behind Pro. */
    fun isBranding(style: Int): Boolean = style >= BRAND_RING

    /** Branding customization: card + sizing. dp values are in dp; [cardColor] is an ARGB int. */
    data class Opts(
        val card: Boolean = true,
        val cardColor: Int = Color.WHITE,
        val cardOpacity: Int = 100,
        val cardRadius: Int = 12,
        val gap: Int = 12,
        val loaderWidth: Int = 78,
        val loaderThickness: Int = 7
    )

    /**
     * Creates a loader view for [style], tinted with [themeColor]. Branding styles use
     * [icon] (falling back to the launcher icon when null) and the supplied [opts].
     */
    fun create(
        context: Context,
        style: Int,
        icon: Bitmap?,
        themeColor: Int,
        opts: Opts = Opts()
    ): View = when (style) {
        LINEAR -> linear(context, themeColor)
        BRAND_RING -> wrap(context, brandRing(context, icon, themeColor, opts), opts)
        BRAND_BAR -> wrap(context, brandBar(context, icon, themeColor, opts), opts)
        else -> circular(context, themeColor)
    }

    // ─── Free loaders ────────────────────────────────────────────────────────

    private fun circular(context: Context, themeColor: Int): View =
        spinner(context, dp(context, 44f), themeColor)

    private fun linear(context: Context, themeColor: Int): View =
        ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(themeColor)
            layoutParams = ViewGroup.LayoutParams(dp(context, 160f), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

    // ─── Branding loaders (Pro) ──────────────────────────────────────────────

    /** App icon centred inside a circular spinner; [Opts.gap] insets the icon, [Opts.loaderThickness] the ring band. */
    private fun brandRing(context: Context, icon: Bitmap?, themeColor: Int, opts: Opts): View {
        val ringDp = opts.loaderWidth.toFloat().coerceAtLeast(40f)
        val iconDp = (ringDp - opts.gap * 2f).coerceAtLeast(16f)
        val ringPx = dp(context, ringDp)
        val thickPx = dp(context, opts.loaderThickness.toFloat().coerceAtLeast(1f))
            .toFloat().coerceAtMost(ringPx / 2f - dp(context, 1f))
        val frame = FrameLayout(context)
        val ring = GradientLoaders.Ring(context, ringPx, thickPx, themeColor, lightTrack(themeColor)).apply {
            layoutParams = FrameLayout.LayoutParams(ringPx, ringPx, Gravity.CENTER)
        }
        frame.addView(ring)
        frame.addView(brandIcon(context, icon, iconDp).apply {
            layoutParams = FrameLayout.LayoutParams(dp(context, iconDp), dp(context, iconDp), Gravity.CENTER)
        })
        return frame
    }

    /** App icon above an indeterminate bar; [Opts.loaderWidth] is the bar width, [Opts.loaderThickness] its height. */
    private fun brandBar(context: Context, icon: Bitmap?, themeColor: Int, opts: Opts): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        column.addView(brandIcon(context, icon, 56f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 56f), dp(context, 56f))
        })
        val thickPx = dp(context, opts.loaderThickness.toFloat().coerceAtLeast(1f)).toFloat()
        val barWidth = dp(context, opts.loaderWidth.toFloat().coerceAtLeast(40f))
        column.addView(GradientLoaders.Bar(context, barWidth, thickPx, themeColor, lightTrack(themeColor)).apply {
            layoutParams = LinearLayout.LayoutParams(
                barWidth, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, opts.gap.toFloat()) }
        })
        return column
    }

    // ─── Building blocks ─────────────────────────────────────────────────────

    /** Optionally wraps [content] in a rounded, tinted card per [opts]. */
    private fun wrap(context: Context, content: View, opts: Opts): View {
        if (!opts.card) return content
        return MaterialCardView(context).apply {
            radius = dp(context, opts.cardRadius.toFloat()).toFloat()
            // Flat, borderless card. The Material3 default card adds a light-grey outline
            // stroke and the elevation shadow blurs the rounded corners into a grey haze —
            // clear both so the corners stay crisp.
            cardElevation = 0f
            strokeWidth = 0
            setStrokeColor(Color.TRANSPARENT)
            isClickable = false
            isFocusable = false
            val alpha = (opts.cardOpacity.coerceIn(0, 100) * 255 / 100)
            setCardBackgroundColor((opts.cardColor and 0x00FFFFFF) or (alpha shl 24))
            val pad = dp(context, 14f)
            addView(FrameLayout(context).apply {
                setPadding(pad, pad, pad, pad)
                addView(content)
            })
        }
    }

    /** An indeterminate circular spinner of [sizePx] px tinted [themeColor]. */
    private fun spinner(context: Context, sizePx: Int, themeColor: Int): ProgressBar =
        ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateDrawable?.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
            layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
        }

    /** The app icon clipped to a circle (or a placeholder when [icon] is null). */
    private fun brandIcon(context: Context, icon: Bitmap?, sizeDp: Float): ImageView {
        val size = dp(context, sizeDp)
        return ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (icon != null) setImageBitmap(icon) else setImageResource(R.drawable.app_icon)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
        }
    }

    /** A light version of [color] (blended ~82% toward white) for the indicator track. */
    private fun lightTrack(color: Int): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * 0.82f).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * 0.82f).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * 0.82f).toInt()
    )

    private fun dp(context: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
    ).toInt()
}
