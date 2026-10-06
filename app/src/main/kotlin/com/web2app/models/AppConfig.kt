package com.web2app.models

import org.json.JSONArray
import org.json.JSONObject

data class AppConfig(
    val id: String = "",
    val appName: String = "Web App",
    val appIcon: String = "",
    val packageName: String = "com.web2app",
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val websiteURL: String = "https://appofweb.com",
    /** Built from a bundled HTML file / website ZIP in `assets/web/`, not an address. */
    val localContent: Boolean = false,
    /** Start page inside the bundle, relative to its root. */
    val localEntry: String = "index.html",
    val themeColor: String = "#FFFFFF",
    val splash: Splash = Splash(),
    val showTabbar: Boolean = false,
    val tabbarStyle: Int = 0,
    val tabbars: List<TabbarItem> = listOf(),
    val tabSettings: TabSettings = TabSettings(),
    val permissions: List<Int> = listOf(),
    val pushNotification: Boolean = false,
    /** OneSignal App ID used to init push when [pushNotification] is on. */
    val oneSignalAppId: String = "",
    val playStore: Boolean = false,
    val appStore: Boolean = false,
    /**
     * Created via the Pro flow. Pro apps expose APK/AAB/keystore builds (each
     * costing coins) in the Build Center; non-Pro apps only get the free APK build.
     */
    val pro: Boolean = false,
    /** Show intro/onboarding screens before the web view on first launch. */
    val showOnboarding: Boolean = false,
    val onboarding: List<OnboardingScreen> = listOf(),
    /** Onboarding layout style (0 = Bottom card, 1 = Centered). */
    val onboardingStyle: Int = 0,
    /** Use a gradient background on the onboarding screens. */
    val onboardingGradient: Boolean = false,
    /** Colour of the onboarding title + description text. */
    val onboardingTextColor: String = "#111827",
    /** Accent colour used for the gradient/coloured panel + controls. */
    val onboardingGradientColor: String = "#5B5BD6",
    /** Bottom-card background colour (Bottom-card style only). */
    val onboardingCardColor: String = "#FFFFFF",
    /** Onboarding image shape: 0 = Circle, 1 = Rounded (15dp), 2 = None. */
    val onboardingImageShape: Int = 0,
    /** Loader shown while web pages load inside the WebView. */
    val pageLoader: PageLoader = PageLoader(),
    /** Swipe-down-to-refresh gesture on the WebView (off by default). */
    val pullRefresh: Boolean = false,
    /**
     * Hide the status and navigation bars so the page owns the whole display. The
     * bars return on a swipe from the edge and hide again on their own.
     */
    val fullScreen: Boolean = false
)

data class PageLoader(
    /** When false, a plain circular spinner is used (legacy default behaviour). */
    val enabled: Boolean = false,
    /**
     * Loader style:
     * 0 = Circular, 1 = Linear bar (both free),
     * 2 = Branding ring (app icon inside a circular spinner),
     * 3 = Branding bar (app icon above a linear bar).
     * Styles 2–3 are Pro only and honour the branding fields below.
     */
    val style: Int = 0,
    /** Branding only: wrap the loader in a rounded card. */
    val card: Boolean = true,
    /** Branding card background color. */
    val cardColor: String = "#FFFFFF",
    /** Branding card opacity, 0–100 percent. */
    val cardOpacity: Int = 100,
    /** Branding card corner radius, in dp. */
    val cardRadius: Int = 12,
    /** Branding: space between the icon and the loader, in dp. */
    val gap: Int = 12,
    /** Branding: loader width/diameter, in dp. */
    val loaderWidth: Int = 78,
    /** Branding: loader stroke thickness (ring band / bar height), in dp. */
    val loaderThickness: Int = 7
)

data class OnboardingScreen(
    val image: String = "",
    val title: String = "",
    val description: String = ""
)

data class Splash(
    val color: String = "#FFFFFF",
    /** When true, the background is a top→bottom gradient from [color] to white (like the intro). */
    val gradient: Boolean = false,
    val image: String = "",
    /** Loader size in dp. */
    val size: Int = 48,
    /**
     * Loader style: 0 = None, 1 = Circle, 2 = Horizontal bar, 3 = Large circle,
     * 4 = Small circle, 5 = Gradient fill (determinate bar that fills over 3s with a
     * gradient of the theme color).
     */
    val loader: Int = 0,
    /** App icon size on the splash, in dp. */
    val iconSize: Int = 120,
    /** App icon corner radius on the splash, in dp (0 = square). */
    val iconCorner: Int = 15,
    /** Vertical space between the icon and the loader, in dp. */
    val gap: Int = 28
)

data class TabSettings(
    val tabActiveColor: String = "#1976D2",
    val tabInactiveColor: String = "#888888",
    val tabBarColor: String = "#ffffff",
    val showTitles: Boolean = true
)

data class TabbarItem(
    val icon: String = "",
    val title: String = "",
    val url: String = ""
)

fun parseAppConfig(json: JSONObject): AppConfig {
    // Parse tabbars
    val tabbars = mutableListOf<TabbarItem>()
    val tabbarsArray = json.optJSONArray("tabbars")
    if (tabbarsArray != null) {
        for (i in 0 until tabbarsArray.length()) {
            val t = tabbarsArray.optJSONObject(i) ?: continue
            tabbars.add(
                TabbarItem(
                    icon = t.optString("icon", ""),
                    title = t.optString("title", ""),
                    url = t.optString("url", "")
                )
            )
        }
    }

    // Parse tabSettings
    val tsObj = json.optJSONObject("tabSettings") ?: JSONObject()
    val tabSettings = TabSettings(
        tabActiveColor = tsObj.optString("tabActiveColor", "#1976D2"),
        tabInactiveColor = tsObj.optString("tabInactiveColor", "#888888"),
        tabBarColor = tsObj.optString("tabBarColor", "#ffffff"),
        showTitles = tsObj.optBoolean("showTitles", true)
    )

    // Parse splash
    val splashObj = json.optJSONObject("splash") ?: JSONObject()
    val splash = Splash(
        color = splashObj.optString("color", "#ffffff"),
        gradient = splashObj.optBoolean("gradient", false),
        image = splashObj.optString("image", ""),
        size = splashObj.optInt("size", 48),
        loader = splashObj.optInt("loader", 0),
        iconSize = splashObj.optInt("iconSize", 120),
        iconCorner = splashObj.optInt("iconCorner", 15),
        gap = splashObj.optInt("gap", 28)
    )

    // Parse page loader
    val plObj = json.optJSONObject("pageLoader") ?: JSONObject()
    val pageLoader = PageLoader(
        enabled = plObj.optBoolean("enabled", false),
        style = plObj.optInt("style", 0),
        card = plObj.optBoolean("card", true),
        cardColor = plObj.optString("cardColor", "#FFFFFF"),
        cardOpacity = plObj.optInt("cardOpacity", 100),
        cardRadius = plObj.optInt("cardRadius", 12),
        gap = plObj.optInt("gap", 12),
        loaderWidth = plObj.optInt("loaderWidth", 78),
        loaderThickness = plObj.optInt("loaderThickness", 7)
    )

    // Parse onboarding screens
    val onboarding = mutableListOf<OnboardingScreen>()
    val onbArray = json.optJSONArray("onboarding")
    if (onbArray != null) {
        for (i in 0 until onbArray.length()) {
            val o = onbArray.optJSONObject(i) ?: continue
            onboarding.add(
                OnboardingScreen(
                    image = o.optString("image", ""),
                    title = o.optString("title", ""),
                    description = o.optString("description", "")
                )
            )
        }
    }

    // Parse permissions array
    val permissionsArray = json.optJSONArray("permissions")
    val permissions = mutableListOf<Int>()
    if (permissionsArray != null) {
        for (i in 0 until permissionsArray.length()) {
            val p = permissionsArray.optInt(i, -1)
            if (p >= 0) permissions.add(p)
        }
    }

    return AppConfig(
        id = json.optString("id", ""),
        appName = json.optString("appName", "Web App"),
        appIcon = json.optString("appIcon", ""),
        packageName = json.optString("package", "com.web2app"),
        versionName = json.optString("versionName", "1.0"),
        versionCode = json.optInt("versionCode", 1),
        websiteURL = json.optString("websiteUrl", "https://appofweb.com"),
        localContent = json.optBoolean("localContent", false),
        localEntry = json.optString("localEntry", "index.html").ifBlank { "index.html" },
        themeColor = json.optString("themeColor", "#FFFFFF"),
        splash = splash,
        showTabbar = json.optBoolean("showTabbar", false),
        tabbarStyle = json.optInt("tabbarStyle", 0),
        tabbars = tabbars,
        tabSettings = tabSettings,
        permissions = permissions,
        pushNotification = json.optBoolean("pushNotification", false),
        oneSignalAppId = json.optString("oneSignalAppId", ""),
        playStore = json.optBoolean("playStore", false),
        appStore = json.optBoolean("appStore", false),
        pro = json.optBoolean("pro", false),
        showOnboarding = json.optBoolean("showOnboarding", false),
        onboarding = onboarding,
        onboardingStyle = json.optInt("onboardingStyle", 0),
        onboardingGradient = json.optBoolean("onboardingGradient", false),
        onboardingTextColor = json.optString("onboardingTextColor", "#111827"),
        onboardingGradientColor = json.optString("onboardingGradientColor", "#5B5BD6"),
        onboardingCardColor = json.optString("onboardingCardColor", "#FFFFFF"),
        onboardingImageShape = json.optInt("onboardingImageShape", 0),
        pageLoader = pageLoader,
        pullRefresh = json.optBoolean("pullRefresh", false),
        fullScreen = json.optBoolean("fullScreen", false)
    )
}

/**
 * Serialize an AppConfig back to a JSONObject (inverse of parseAppConfig).
 * Used to persist builder output into SharedPreferences.
 */
fun appConfigToJson(config: AppConfig): JSONObject {
    val obj = JSONObject()
    obj.put("id", config.id)
    obj.put("appName", config.appName)
    obj.put("appIcon", config.appIcon)
    obj.put("package", config.packageName)
    obj.put("versionName", config.versionName)
    obj.put("versionCode", config.versionCode)
    obj.put("websiteUrl", config.websiteURL)
    obj.put("localContent", config.localContent)
    obj.put("localEntry", config.localEntry)
    obj.put("themeColor", config.themeColor)

    val splashObj = JSONObject()
    splashObj.put("color", config.splash.color)
    splashObj.put("gradient", config.splash.gradient)
    splashObj.put("image", config.splash.image)
    splashObj.put("size", config.splash.size)
    splashObj.put("loader", config.splash.loader)
    splashObj.put("iconSize", config.splash.iconSize)
    splashObj.put("iconCorner", config.splash.iconCorner)
    splashObj.put("gap", config.splash.gap)
    obj.put("splash", splashObj)

    obj.put("showTabbar", config.showTabbar)
    obj.put("tabbarStyle", config.tabbarStyle)

    val tabbarsArray = JSONArray()
    for (tab in config.tabbars) {
        val t = JSONObject()
        t.put("icon", tab.icon)
        t.put("title", tab.title)
        t.put("url", tab.url)
        tabbarsArray.put(t)
    }
    obj.put("tabbars", tabbarsArray)

    val tsObj = JSONObject()
    tsObj.put("tabActiveColor", config.tabSettings.tabActiveColor)
    tsObj.put("tabInactiveColor", config.tabSettings.tabInactiveColor)
    tsObj.put("tabBarColor", config.tabSettings.tabBarColor)
    tsObj.put("showTitles", config.tabSettings.showTitles)
    obj.put("tabSettings", tsObj)

    val permsArray = JSONArray()
    for (p in config.permissions) permsArray.put(p)
    obj.put("permissions", permsArray)

    obj.put("pushNotification", config.pushNotification)
    obj.put("oneSignalAppId", config.oneSignalAppId)
    obj.put("playStore", config.playStore)
    obj.put("appStore", config.appStore)
    obj.put("pro", config.pro)

    obj.put("showOnboarding", config.showOnboarding)
    val onbArray = JSONArray()
    for (screen in config.onboarding) {
        val o = JSONObject()
        o.put("image", screen.image)
        o.put("title", screen.title)
        o.put("description", screen.description)
        onbArray.put(o)
    }
    obj.put("onboarding", onbArray)
    obj.put("onboardingStyle", config.onboardingStyle)
    obj.put("onboardingGradient", config.onboardingGradient)
    obj.put("onboardingTextColor", config.onboardingTextColor)
    obj.put("onboardingGradientColor", config.onboardingGradientColor)
    obj.put("onboardingCardColor", config.onboardingCardColor)
    obj.put("onboardingImageShape", config.onboardingImageShape)

    val plObj = JSONObject()
    plObj.put("enabled", config.pageLoader.enabled)
    plObj.put("style", config.pageLoader.style)
    plObj.put("card", config.pageLoader.card)
    plObj.put("cardColor", config.pageLoader.cardColor)
    plObj.put("cardOpacity", config.pageLoader.cardOpacity)
    plObj.put("cardRadius", config.pageLoader.cardRadius)
    plObj.put("gap", config.pageLoader.gap)
    plObj.put("loaderWidth", config.pageLoader.loaderWidth)
    plObj.put("loaderThickness", config.pageLoader.loaderThickness)
    obj.put("pageLoader", plObj)
    obj.put("pullRefresh", config.pullRefresh)
    obj.put("fullScreen", config.fullScreen)
    return obj
}

/**
 * What the app opens on: the bundle's start page when it ships its own content,
 * otherwise the configured website.
 */
fun AppConfig.localOrWebsiteUrl(): String =
    if (localContent) localEntry else websiteURL
