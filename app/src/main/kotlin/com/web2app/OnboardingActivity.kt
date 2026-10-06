package com.web2app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.web2app.data.AppRepository
import com.web2app.models.AppConfig
import com.web2app.models.OnboardingScreen
import com.web2app.models.parseAppConfig
import kotlin.math.abs

/**
 * Intro/onboarding screens shown before the web view. The page frame (coloured panel /
 * card) stays fixed; pressing Next or swiping animates only the content (image, title,
 * description) and the indicator dots, then hands off to [MainActivity].
 */
class OnboardingActivity : AppCompatActivity() {

    private var appId: String? = null
    private lateinit var config: AppConfig
    private lateinit var screens: List<OnboardingScreen>
    private lateinit var page: View
    private var index = 0
    private var animating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge-to-edge so the onboarding background fills behind the system bars,
        // matching SplashActivity.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_onboarding)
        makeSystemBarsTransparent()

        val id = intent.getStringExtra(MainActivity.EXTRA_APP_ID)
        appId = id
        val cfg = if (id != null) AppRepository(this).getApp(id)
        else parseAppConfig(readJson(this, "app_settings.json"))
        if (cfg == null || !cfg.showOnboarding || cfg.onboarding.isEmpty()) {
            goToApp()
            return
        }
        config = cfg
        screens = cfg.onboarding

        val host = findViewById<FrameLayout>(R.id.onbHost)
        val layout = if (config.onboardingStyle == 1) R.layout.onb_page_style2
        else R.layout.onb_page_style1
        page = LayoutInflater.from(this).inflate(layout, host, false)
        host.addView(page)
        applyContentInsets(page)

        // The frame (panel / card / forward button colours) is set once and never moves.
        applyBackground(page, accent())
        page.findViewById<View>(R.id.onbSkip)?.setOnClickListener { goToApp() }
        val advance = View.OnClickListener { goNext() }
        page.findViewById<View>(R.id.onbNextFab)?.setOnClickListener(advance)
        page.findViewById<View>(R.id.onbNext)?.setOnClickListener(advance)

        attachSwipe(host)

        bindContent(0)
        revealPage()
    }

    private fun accent() = parseColor(config.onboardingGradientColor, R.color.colorPrimary)

    /**
     * Makes the status + navigation bars transparent (and drops the 3-button contrast
     * scrim), letting the onboarding background show through — same as SplashActivity.
     * The onboarding surfaces are light, so the bar icons are drawn dark.
     */
    @Suppress("DEPRECATION")
    private fun makeSystemBarsTransparent() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    /**
     * Pads the page content by the system-bar insets so it stays clear of the (now
     * transparent) bars, while the page background still fills the whole window.
     */
    private fun applyContentInsets(page: View) {
        val (l, t, r, b) = listOf(
            page.paddingLeft, page.paddingTop, page.paddingRight, page.paddingBottom
        )
        ViewCompat.setOnApplyWindowInsetsListener(page) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
            insets
        }
    }

    private fun goNext() {
        if (animating) return
        if (index < screens.size - 1) transitionTo(index + 1, +1) else goToApp()
    }

    private fun goPrev() {
        if (animating || index == 0) return
        transitionTo(index - 1, -1)
    }

    /** The views that swap when changing screens (the frame stays put). */
    private fun contentViews(): List<View> = listOfNotNull(
        page.findViewById(R.id.onbImage),
        page.findViewById(R.id.onbTitle),
        page.findViewById(R.id.onbDesc),
        page.findViewById(R.id.onbDots)
    )

    /** Slides the current content out, swaps in the new screen, then slides it in. */
    private fun transitionTo(newIndex: Int, dir: Int) {
        animating = true
        val out = (dir * dp(56f))
        val views = contentViews()
        views.forEachIndexed { i, v ->
            val anim = v.animate().translationX(-out).alpha(0f)
                .setDuration(170).setInterpolator(AccelerateInterpolator())
            if (i == 0) anim.withEndAction {
                index = newIndex
                bindContent(newIndex)
                animateInContent(dir)
            }
            anim.start()
        }
    }

    private fun animateInContent(dir: Int) {
        val from = dir * dp(56f)
        contentViews().forEachIndexed { i, v ->
            v.translationX = from
            v.alpha = 0f
            val anim = v.animate().translationX(0f).alpha(1f)
                .setStartDelay(40L * i).setDuration(300)
                .setInterpolator(DecelerateInterpolator(1.8f))
            if (i == contentViews().size - 1) anim.withEndAction { animating = false }
            anim.start()
        }
    }

    /** Sets the per-screen content (no animation); colours/shape come from config. */
    private fun bindContent(position: Int) {
        val screen = screens[position]
        val textColor = parseColor(config.onboardingTextColor, R.color.textPrimary)

        applyImage(page.findViewById(R.id.onbImage), screen.image)
        page.findViewById<TextView>(R.id.onbTitle).apply {
            text = screen.title
            setTextColor(textColor)
        }
        page.findViewById<TextView>(R.id.onbDesc).apply {
            text = screen.description
            visibility = if (screen.description.isBlank()) View.GONE else View.VISIBLE
            setTextColor((textColor and 0xFFFFFF) or (0xB3 shl 24))
        }
        renderDots(page.findViewById(R.id.onbDots), position, accent())
    }

    /** First-time staggered entrance for the opening screen. */
    private fun revealPage() {
        page.findViewById<View?>(R.id.onbImage)?.apply {
            alpha = 0f; scaleX = 0.8f; scaleY = 0.8f
            animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(60).setDuration(560)
                .setInterpolator(OvershootInterpolator(1.6f)).start()
        }
        fun rise(id: Int, delay: Long) {
            page.findViewById<View?>(id)?.apply {
                alpha = 0f; translationY = dp(28f)
                animate().alpha(1f).translationY(0f)
                    .setStartDelay(delay).setDuration(460)
                    .setInterpolator(DecelerateInterpolator(2f)).start()
            }
        }
        rise(R.id.onbTitle, 170)
        rise(R.id.onbDesc, 240)
        rise(R.id.onbSkip, 330)
        rise(R.id.onbDots, 360)
        rise(R.id.onbNextFab, 390)
        rise(R.id.onbNext, 390)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachSwipe(host: View) {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (abs(velocityX) > abs(velocityY) && abs(velocityX) > 700) {
                    if (velocityX < 0) goNext() else goPrev()
                    return true
                }
                return false
            }
        })
        // Buttons consume their own taps; swipes elsewhere drive the content transition.
        host.setOnTouchListener { _, ev -> detector.onTouchEvent(ev) }
    }

    /** Style 1 colours the top panel + card + forward button; Style 2 the whole surface. */
    private fun applyBackground(page: View, accent: Int) {
        val gradient = config.onboardingGradient
        val tint = (accent and 0xFFFFFF) or (0x4D shl 24)
        if (config.onboardingStyle == 1) {
            page.background = if (gradient) GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(tint, Color.WHITE)
            ) else ColorDrawable(Color.WHITE)
        } else {
            page.findViewById<View>(R.id.onbTopPanel).background = if (gradient) GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf((accent and 0xFFFFFF) or (0x66 shl 24), tint)
            ) else ColorDrawable(tint)
            page.findViewById<View>(R.id.onbCard).backgroundTintList =
                ColorStateList.valueOf(parseColor(config.onboardingCardColor, R.color.surface))
            page.findViewById<View>(R.id.onbNextFab).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }
        }
    }

    private fun applyImage(img: ImageView, data: String) {
        val bmp = data.takeIf { it.isNotBlank() }?.let { Base64ImageUtil.base64ToBitmap(it) }
        val tile = GradientDrawable().apply {
            when (config.onboardingImageShape) {
                0 -> shape = GradientDrawable.OVAL
                1 -> { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(15f) }
                else -> shape = GradientDrawable.RECTANGLE
            }
        }
        if (bmp != null) {
            img.imageTintList = null
            img.setPadding(0, 0, 0, 0)
            img.setImageBitmap(bmp)
            img.background = if (config.onboardingImageShape == 2) null else tile
            img.clipToOutline = config.onboardingImageShape != 2
        } else {
            img.clipToOutline = false
            img.setImageResource(R.drawable.ic_image)
            img.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorPrimary))
            tile.setColor(ContextCompat.getColor(this, R.color.colorPrimaryLight))
            img.background = tile
        }
    }

    private fun renderDots(dots: LinearLayout, active: Int, accent: Int) {
        dots.removeAllViews()
        val gray = ContextCompat.getColor(this, R.color.divider)
        for (i in screens.indices) {
            val on = i == active
            dots.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(if (on) 22f else 8f).toInt(), dp(8f).toInt()
                ).apply { marginEnd = dp(6f).toInt() }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4f)
                    setColor(if (on) accent else gray)
                }
            })
        }
    }

    override fun onBackPressed() {
        if (index > 0) goPrev() else super.onBackPressed()
    }

    private fun goToApp() {
        // Remember the intro was completed so installed apps skip it next launch.
        // Preview (appId != null) is left untouched so it can be re-previewed.
        if (appId == null) {
            getSharedPreferences(SplashActivity.PREFS, MODE_PRIVATE).edit()
                .putBoolean(SplashActivity.KEY_ONBOARDING_SEEN, true).apply()
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_APP_ID, appId)
        )
        finish()
    }

    private fun parseColor(hex: String?, fallbackRes: Int): Int = try {
        if (hex.isNullOrBlank()) ContextCompat.getColor(this, fallbackRes) else Color.parseColor(hex)
    } catch (e: Exception) {
        ContextCompat.getColor(this, fallbackRes)
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
