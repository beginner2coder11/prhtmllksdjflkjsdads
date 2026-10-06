package com.web2app

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.web2app.data.AppRepository
import com.web2app.models.Splash
import com.web2app.models.parseAppConfig

/**
 * Launcher entry point. Reads the app configuration (from the builder's storage when
 * previewing, else assets/app_settings.json), shows the configured splash screen
 * (background colour + image + loader) for a short moment, then opens the onboarding
 * screens (when enabled) or [MainActivity].
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge-to-edge so the splash background fills behind the system bars.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_splash)

        // Preview mode forwards an app id; standalone builds read the bundled assets.
        val appId = intent.getStringExtra(MainActivity.EXTRA_APP_ID)
        val config = if (appId != null) {
            AppRepository(this).getApp(appId)
        } else {
            parseAppConfig(readJson(this, "app_settings.json"))
        }
        val splash = config?.splash ?: Splash()

        // When the app is built with push enabled, ask for the Android 13+ notification
        // permission so OneSignal alerts can be shown. No-op on older Android (granted
        // at install) and when push is off.
        if (config?.pushNotification == true && config.oneSignalAppId.isNotBlank()) {
            requestNotificationPermission()
        }

        val root = findViewById<View>(R.id.splashRoot)
        val image = findViewById<ImageView>(R.id.splashImage)
        val loaderHolder = findViewById<FrameLayout>(R.id.splashLoaderHolder)

        // Background: plain colour, or a top→bottom gradient from it to white.
        val bgColor = safeColor(splash.color, Color.WHITE)
        root.background = if (splash.gradient) {
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bgColor, Color.WHITE))
        } else {
            ColorDrawable(bgColor)
        }

        // Let the splash background show through the system bars: make them transparent
        // and flip their icons to match (top sits on bgColor; the bottom sits on white
        // when a gradient is used). Disable the 3-button contrast scrim too.
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val bottomColor = if (splash.gradient) Color.WHITE else bgColor
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars =
                    androidx.core.graphics.ColorUtils.calculateLuminance(bgColor) > 0.5
                isAppearanceLightNavigationBars =
                    androidx.core.graphics.ColorUtils.calculateLuminance(bottomColor) > 0.5
            }
        }

        // Splash image (fall back to the app icon, then the launcher drawable).
        val imageData = splash.image.takeIf { it.isNotBlank() } ?: config?.appIcon
        val bitmap = imageData?.let { Base64ImageUtil.base64ToBitmap(it) }
        if (bitmap != null) {
            image.setImageBitmap(bitmap)
        } else {
            image.setImageResource(R.drawable.app_icon)
        }
        val iconPx = dp(splash.iconSize.coerceIn(48, 240))
        image.layoutParams = image.layoutParams.apply { width = iconPx; height = iconPx }
        val radiusPx = dp(splash.iconCorner.coerceIn(0, 120)).toFloat()
        image.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        image.clipToOutline = radiusPx > 0f
        image.visibility = View.VISIBLE

        // Loader: built programmatically so its style + size can vary.
        (loaderHolder.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin =
            dp(splash.gap.coerceIn(0, 120))
        loaderHolder.removeAllViews()
        val themeColor = safeColor(config?.themeColor, DEFAULT_THEME)
        val sizePx = dp(splash.size.coerceIn(16, 120))
        val loader = makeLoader(splash.loader, sizePx, themeColor)
        if (loader != null) loaderHolder.addView(loader)

        // The gradient-fill loader is a 3s determinate progress; hold long enough for it
        // to visibly complete before navigating on.
        val holdMs = if (splash.loader == LOADER_GRADIENT_FILL) GRADIENT_FILL_MS else SPLASH_DURATION_MS

        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing) return@postDelayed
            // Intro/onboarding shows only on the first launch of an installed app; once
            // seen, later launches go straight to home. Builder preview (appId != null)
            // always shows it so it can be previewed repeatedly.
            val onboardingSeen = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_SEEN, false)
            val showOnboarding = config?.showOnboarding == true && config.onboarding.isNotEmpty() &&
                (appId != null || !onboardingSeen)
            val next = if (showOnboarding) OnboardingActivity::class.java else MainActivity::class.java
            startActivity(Intent(this, next).putExtra(MainActivity.EXTRA_APP_ID, appId))
            finish()
        }, holdMs)
    }

    /** Prompts for POST_NOTIFICATIONS on Android 13+ if not already granted. */
    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val perm = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(perm), 1001)
        }
    }

    /** Builds a [ProgressBar] for the chosen [style], sized [sizePx] px, or null for "None". */
    private fun makeLoader(style: Int, sizePx: Int, themeColor: Int): ProgressBar? {
        if (style == LOADER_GRADIENT_FILL) return makeGradientFillLoader(sizePx, themeColor)
        val styleAttr = when (style) {
            1 -> android.R.attr.progressBarStyle
            2 -> android.R.attr.progressBarStyleHorizontal
            3 -> android.R.attr.progressBarStyleLarge
            4 -> android.R.attr.progressBarStyleSmall
            else -> return null // 0 = None
        }
        val bar = ProgressBar(ContextThemeWrapper(this, 0), null, styleAttr).apply {
            isIndeterminate = true
        }
        val w = if (style == 2) dp(220) else sizePx
        bar.layoutParams = FrameLayout.LayoutParams(w, if (style == 2) ViewGroup.LayoutParams.WRAP_CONTENT else sizePx)
        return bar
    }

    /**
     * A determinate, pill-shaped bar whose fill is a horizontal gradient of [themeColor].
     * Animates 0→100% over [GRADIENT_FILL_MS]. The size slider drives the bar width.
     */
    private fun makeGradientFillLoader(sizePx: Int, themeColor: Int): ProgressBar {
        val heightPx = (sizePx * 0.22f).toInt().coerceAtLeast(dp(7))
        val widthPx = (sizePx * 4)
        val radius = heightPx / 2f

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 1000
            progressDrawable = gradientFillDrawable(radius, themeColor)
            layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        }
        ObjectAnimator.ofInt(bar, "progress", 0, bar.max).apply {
            duration = GRADIENT_FILL_MS
            interpolator = LinearInterpolator()
            start()
        }
        return bar
    }

    /** Track (faint) + clipped gradient fill, both pill-rounded to [radius]. */
    private fun gradientFillDrawable(radius: Float, themeColor: Int) = LayerDrawable(
        arrayOf(
            GradientDrawable().apply {
                cornerRadius = radius
                setColor(lighten(themeColor, 0.82f))
            },
            ClipDrawable(
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(lighten(themeColor, 0.45f), themeColor)
                ).apply { cornerRadius = radius },
                Gravity.START,
                ClipDrawable.HORIZONTAL
            )
        )
    ).apply {
        setId(0, android.R.id.background)
        setId(1, android.R.id.progress)
    }

    /** Blends [color] toward white by [amount] (0..1). */
    private fun lighten(color: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * amount).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * amount).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt()
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun safeColor(hex: String?, default: Int): Int {
        return try {
            if (hex.isNullOrBlank()) default else Color.parseColor(hex)
        } catch (e: Exception) {
            default
        }
    }

    companion object {
        private const val SPLASH_DURATION_MS = 1800L
        /** Hold time + animation duration for the gradient-fill loader. */
        private const val GRADIENT_FILL_MS = 3000L
        /** Loader style index for the gradient-fill bar. */
        private const val LOADER_GRADIENT_FILL = 5
        private const val DEFAULT_THEME = 0xFF5B5BD6.toInt()

        /** Prefs storing whether the intro/onboarding has been seen on this install. */
        const val PREFS = "web2app_prefs"
        const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    }
}
