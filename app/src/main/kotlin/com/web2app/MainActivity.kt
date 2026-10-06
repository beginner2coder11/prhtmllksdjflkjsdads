package com.web2app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import android.widget.FrameLayout
import android.webkit.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.web2app.data.AppRepository
import com.web2app.handlers.PermissionsHandler
import com.web2app.models.localOrWebsiteUrl
import com.web2app.models.parseAppConfig

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: View
    private lateinit var bottomNav: BottomNavigationView

    /** Holder for the configured page loader; null/false means use the default spinner. */
    private var pageLoaderHolder: View? = null
    private var useCustomPageLoader = false

    private lateinit var permissionsHandler: PermissionsHandler

    /** Pending WebView file-input callback; must be invoked exactly once per chooser. */
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>


    private var selectedTabIndex = 0

    companion object {
        /**
         * Web hosts whose only purpose is to deep-link into an app. An https link to one
         * of these is handed to the system (which routes it to the app via App Links)
         * rather than loaded in the WebView.
         */
        private val APP_LINK_HOSTS = setOf(
            "wa.me", "api.whatsapp.com", "chat.whatsapp.com",   // WhatsApp
            "t.me", "telegram.me",                                // Telegram
            "m.me",                                               // Messenger
            "play.google.com",                                    // Play Store
            "maps.google.com", "maps.app.goo.gl",                 // Google Maps
        )
        const val EXTRA_APP_ID = "extra_app_id"
        private const val DEFAULT_THEME = 0xFF5B5BD6.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Preview mode: load the app being previewed from the builder's storage.
        // Falls back to the bundled assets/app_settings.json when launched standalone.
        val appId = intent.getStringExtra(EXTRA_APP_ID)
        val isPreview = appId != null
        appConfig = if (isPreview) {
            AppRepository(this).getApp(appId!!) ?: appConfig
        } else {
            parseAppConfig(readJson(this, "app_settings.json"))
        }

        // Setup permissions handler (must be created before setContentView for ActivityResult)
        permissionsHandler = PermissionsHandler(this)

        // System file/photo picker for <input type="file"> — needs no media permission.
        // Registered here (before STARTED) so it's ready when onShowFileChooser fires.
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileUploadCallback?.onReceiveValue(uris)
            fileUploadCallback = null
        }

        setContentView(R.layout.activity_main)
        applySystemBarInsets(R.id.mainRoot, R.id.statusBarScrim)
        applyBrandSurfaces()
        if (appConfig.fullScreen) enterFullScreen()

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        bottomNav = findViewById(R.id.bottomNav)

        // Show the preview close button (faint eye behind a clear X) only when
        // previewing an app from the builder; tapping it dismisses the preview.
        findViewById<View>(R.id.previewControls).apply {
            visibility = if (isPreview) View.VISIBLE else View.GONE
            setOnClickListener { finish() }
        }

        setupPageLoader()
        setupWebView()
        setupBottomNav()
        requestConfiguredPermissions()

        swipeRefresh.setOnRefreshListener { webView.reload() }
        swipeRefresh.isEnabled = appConfig.pullRefresh

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadCurrentUrl()
        }
    }

    // ─── Page loader ─────────────────────────────────────────────────────────

    /**
     * Builds the configured in-page loader into [R.id.pageLoaderHolder]. When the page
     * loader isn't enabled we fall back to the default centered [progressBar].
     */
    private fun setupPageLoader() {
        val holder = findViewById<FrameLayout>(R.id.pageLoaderHolder)
        pageLoaderHolder = holder
        val pl = appConfig.pageLoader
        if (!pl.enabled) {
            useCustomPageLoader = false
            return
        }
        val theme = safeParseColor(appConfig.themeColor, DEFAULT_THEME)
        val icon = Base64ImageUtil.base64ToBitmap(appConfig.appIcon)
        val opts = PageLoaderViews.Opts(
            card = pl.card,
            cardColor = safeParseColor(pl.cardColor, Color.WHITE),
            cardOpacity = pl.cardOpacity,
            cardRadius = pl.cardRadius,
            gap = pl.gap,
            loaderWidth = pl.loaderWidth,
            loaderThickness = pl.loaderThickness
        )
        holder.removeAllViews()
        holder.addView(PageLoaderViews.create(this, pl.style, icon, theme, opts))
        useCustomPageLoader = true
    }

    /** Shows/hides whichever loader is in use (configured page loader or the default). */
    private fun showLoading(show: Boolean) {
        val target = if (useCustomPageLoader) pageLoaderHolder else progressBar
        target?.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ─── WebView ───────────────────────────────────────────────────────────────

    /** Non-null only when this app ships its own content (see [LocalContent]). */
    private val assetLoader by lazy {
        if (appConfig.localContent) LocalContent.loaderFor(this) else null
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // Allow mixed content (http resources on https pages) – mirrors reference Webview.kt
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            // Custom user-agent matching the reference
            userAgentString =
                "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // Enable cookies (including third-party) – mirrors reference Webview.kt
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, true)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            // A bundled site is served from the app's own assets; a website app has no
            // loader and every request goes to the network as before.
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? = LocalContent.intercept(assetLoader, request)

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                val target = uri.toString()

                // Anything that is not a web page belongs to another app: tel:, mailto:,
                // sms:, whatsapp:, geo:, market:, intent:, and the rest. WebView cannot
                // render those (it shows ERR_UNKNOWN_URL_SCHEME), so hand them to the
                // system and keep the page where it is.
                val scheme = uri.scheme?.lowercase()
                if (scheme != null && scheme != "http" && scheme != "https" &&
                    scheme != "file" && scheme != "about" && scheme != "javascript" &&
                    scheme != "data" && scheme != "blob"
                ) {
                    openExternally(target)
                    return true
                }

                // Some https links exist only to open another app: wa.me, t.me, the Play
                // Store, Google Maps. Rendered in the WebView they show that service's
                // marketing site instead — so treat them like a custom scheme.
                if (uri.host?.lowercase()?.let { h -> APP_LINK_HOSTS.any { h == it || h.endsWith(".$it") } } == true) {
                    openExternally(target)
                    return true
                }

                // A bundled page may link to the placeholder address the builder wrote
                // (or to a plain path); both mean "stay inside the bundle". Resolving
                // here keeps in-page navigation working, not just the initial load.
                // Only take over when the address actually changes — returning false
                // AFTER calling loadUrl used to load every link twice.
                if (appConfig.localContent) {
                    val resolved = LocalContent.urlFor(target)
                    if (resolved != target) {
                        view.loadUrl(resolved)
                        return true
                    }
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                showLoading(true)
            }

            override fun onPageFinished(view: WebView, url: String) {
                showLoading(false)
                swipeRefresh.isRefreshing = false
            }
        }

        // WebChromeClient with popup-window support – mirrors reference Webview.kt onCreateWindow
        webView.webChromeClient = object : WebChromeClient() {
            // Handles <input type="file"> taps via the Android system picker (zero media
            // permissions). params.createIntent() honours the input's `accept` types and
            // `multiple` attribute; parseResult() pairs with it to return the picked URIs.
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Release any previous callback so an abandoned input never dead-locks.
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                    ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    fileUploadCallback = null
                    filePathCallback?.onReceiveValue(null)
                    false
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val ctx = view?.context ?: return false

                val popupWebView = WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        setSupportMultipleWindows(true)
                        javaScriptCanOpenWindowsAutomatically = true
                    }
                    webViewClient = object : WebViewClient() {}
                }

                android.app.Dialog(ctx).apply {
                    setContentView(popupWebView)
                    window?.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    show()
                }

                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    // ─── Bottom Navigation ─────────────────────────────────────────────────────

    private fun setupBottomNav() {
        if (!appConfig.showTabbar || appConfig.tabbars.isEmpty()) {
            bottomNav.visibility = View.GONE
            return
        }

        bottomNav.visibility = View.VISIBLE

        // targetSdk 35+ edge-to-edge: the root (mainRoot) already pads for the
        // navigation-bar inset, but Material's BottomNavigationView ALSO adds its own
        // bottom inset padding by default. That double-padding eats into our fixed bar
        // height and collapses the icon row to a blank white strip. Neutralise the
        // view's own inset handling so the icons + labels fill the full fixed height.
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
            insets
        }

        // Apply colours from tabSettings (mirrors BottomNavigationBar composable)
        val activeColor = safeParseColor(appConfig.tabSettings.tabActiveColor, Color.BLUE)
        val inactiveColor = safeParseColor(appConfig.tabSettings.tabInactiveColor, Color.GRAY)

        // Modern, flat tabbar (no line/elevation, matching Google Play).
        bottomNav.setBackgroundColor(brandBarColor())

        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(activeColor, inactiveColor)
        )
        bottomNav.itemIconTintList = colorStateList
        bottomNav.itemTextColor = colorStateList
        bottomNav.setItemTextAppearanceActive(R.style.TextAppearance_Web2App_Tab)
        bottomNav.setItemTextAppearanceInactive(R.style.TextAppearance_Web2App_Tab)

        // Match the classic (shorter) bottom bar: drop the Material3 active-indicator
        // pill, which otherwise makes the bar noticeably taller, and use a compact
        // fixed height (a touch shorter when titles are hidden).
        bottomNav.isItemActiveIndicatorEnabled = false
        val showTitles = appConfig.tabSettings.showTitles
        bottomNav.labelVisibilityMode = if (showTitles) {
            com.google.android.material.bottomnavigation.BottomNavigationView.LABEL_VISIBILITY_LABELED
        } else {
            com.google.android.material.bottomnavigation.BottomNavigationView.LABEL_VISIBILITY_UNLABELED
        }
        val barHeightDp = if (showTitles) 60 else 54
        bottomNav.layoutParams = bottomNav.layoutParams.apply {
            height = (barHeightDp * resources.displayMetrics.density).toInt()
        }

        // Build menu items dynamically from JSON (Base64 icons → BitmapDrawable)
        val menu = bottomNav.menu
        menu.clear()

        // 3px gap between icon and title (only meaningful when titles are shown).
        val iconLabelGap = if (showTitles) 3 else 0
        for ((index, tab) in appConfig.tabbars.withIndex()) {
            val item = menu.add(0, index, index, tab.title)
            if (tab.icon.isNotBlank()) {
                val res = MaterialIcons.resFor(tab.icon)
                val icon: android.graphics.drawable.Drawable? = when {
                    LucideIcons.isLucide(tab.icon) -> {
                        val px = (24 * resources.displayMetrics.density).toInt()
                        LucideIcons.drawable(
                            this, LucideIcons.name(tab.icon), px, android.graphics.Color.BLACK
                        )
                    }
                    res != null -> androidx.core.content.ContextCompat.getDrawable(this, res)
                    else -> Base64ImageUtil.base64ToBitmapDrawable(this, tab.icon)
                }
                item.icon = icon?.let {
                    android.graphics.drawable.InsetDrawable(it, 0, 0, 0, iconLabelGap)
                }
            }
        }

        // Select first tab
        bottomNav.selectedItemId = 0

        bottomNav.setOnItemSelectedListener { item ->
            selectedTabIndex = item.itemId
            loadCurrentUrl()
            true
        }
    }

    private fun loadCurrentUrl() {
        val target = if (appConfig.showTabbar && appConfig.tabbars.isNotEmpty()) {
            appConfig.tabbars.getOrNull(selectedTabIndex)?.url ?: appConfig.localOrWebsiteUrl()
        } else {
            appConfig.localOrWebsiteUrl()
        }
        // In a bundled app a tab's address is a path inside the bundle unless it is a
        // full http(s) URL, which is left to the network.
        val url = if (appConfig.localContent) LocalContent.urlFor(target) else target
        webView.loadUrl(url)
    }

    // ─── Permissions (mirrors PermissionsWrapper composable in reference) ──────

    private fun requestConfiguredPermissions() {
        val perms = appConfig.permissions
        val needsLocation = perms.contains(0)
        val needsFileCamera = perms.contains(1)
        val needsAudioVideo = perms.contains(2)

        when {
            needsLocation -> permissionsHandler.requestLocationPermissions {
                if (needsFileCamera) {
                    permissionsHandler.requestFileAndCameraPermissions {
                        if (needsAudioVideo) permissionsHandler.requestAudioVideoPermissions()
                    }
                } else if (needsAudioVideo) {
                    permissionsHandler.requestAudioVideoPermissions()
                }
            }
            needsFileCamera -> permissionsHandler.requestFileAndCameraPermissions {
                if (needsAudioVideo) permissionsHandler.requestAudioVideoPermissions()
            }
            needsAudioVideo -> permissionsHandler.requestAudioVideoPermissions()
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun safeParseColor(hex: String, default: Int): Int {
        return try {
            if (hex.isBlank()) default else Color.parseColor(hex)
        } catch (e: Exception) {
            default
        }
    }

    /** Blends [color] toward white, keeping [fraction] (0–1) of the original colour. */
    private fun lightTint(color: Int, fraction: Float): Int {
        fun mix(c: Int) = (c * fraction + 255 * (1 - fraction)).toInt().coerceIn(0, 255)
        return Color.rgb(mix(Color.red(color)), mix(Color.green(color)), mix(Color.blue(color)))
    }

    /**
     * The brand surface colour shared by the top status-bar scrim and the bottom tabbar
     * (Google-Play style). Defaults to a very light tint of the theme (active) colour;
     * a custom bar colour set in the builder overrides it.
     */
    private fun brandBarColor(): Int {
        val activeColor = safeParseColor(appConfig.tabSettings.tabActiveColor, Color.BLUE)
        val rawBarColor = safeParseColor(appConfig.tabSettings.tabBarColor, Color.WHITE)
        return if (rawBarColor == Color.WHITE) lightTint(activeColor, 0.08f) else rawBarColor
    }

    /**
     * Sends a non-web link to whatever app handles it. `intent:` URIs carry their own
     * target (and often a `browser_fallback_url`); every other scheme is a plain VIEW.
     * A scheme nobody on the device handles is reported, not swallowed — a dead tap
     * with no feedback reads as a broken app.
     */
    private fun openExternally(url: String) {
        try {
            if (url.startsWith("intent:", ignoreCase = true)) {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                // Try the launch rather than resolveActivity() first: since Android 11
                // that query answers "none" for any package not declared in <queries>,
                // which sent every intent:// link to its browser fallback even with the
                // target app installed. startActivity itself is not filtered.
                try {
                    startActivity(intent)
                    return
                } catch (_: android.content.ActivityNotFoundException) {
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    if (!fallback.isNullOrBlank()) {
                        webView.loadUrl(fallback)
                        return
                    }
                    throw android.content.ActivityNotFoundException()
                }
            }
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            android.widget.Toast.makeText(
                this, getString(R.string.no_app_for_link), android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Hides both system bars and lets the page take the whole display. The bars come
     * back transiently on an edge swipe and hide themselves again. Hiding also zeroes
     * the system-bar insets, so the status scrim and root padding from
     * applySystemBarInsets collapse on their own — nothing else to undo.
     */
    private fun enterFullScreen() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    /** Re-hide the bars after another window (a dialog, the file picker) showed them. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && appConfig.fullScreen) enterFullScreen()
    }

    /**
     * Colours the two system-bar regions:
     *  - status bar (top)  → the plain theme colour from settings, with matching icons;
     *  - navigation bar (bottom) → the tabbar background tint, so it blends with the bar.
     * The scrim fills the status-bar strip; the root's bottom inset-padding shows behind
     * the nav bar, so colouring the root fills that strip.
     */
    private fun applyBrandSurfaces() {
        val statusColor = safeParseColor(appConfig.themeColor, DEFAULT_THEME)
        val navColor = brandBarColor()

        findViewById<View>(R.id.statusBarScrim).setBackgroundColor(statusColor)
        findViewById<View>(R.id.mainRoot).setBackgroundColor(navColor)

        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            // Dark primary status bar → light icons; light nav surface → dark icons.
            isAppearanceLightStatusBars =
                androidx.core.graphics.ColorUtils.calculateLuminance(statusColor) > 0.5
            isAppearanceLightNavigationBars =
                androidx.core.graphics.ColorUtils.calculateLuminance(navColor) > 0.5
        }
        // In 3-button nav mode the system draws a translucent contrast scrim behind the
        // buttons, which hides our colour. Opt out so the nav surface shows through.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            @Suppress("DEPRECATION")
            window.navigationBarColor = navColor
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
