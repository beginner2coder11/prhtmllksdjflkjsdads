package com.web2app

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * Routes links that belong to another app (tel:, mailto:, whatsapp:, geo:, intent:,
 * Google Maps, wa.me, the Play Store…) out of the WebView. A WebView cannot render
 * those itself: a custom scheme shows ERR_UNKNOWN_URL_SCHEME, and an app-link host
 * shows that service's marketing page or redirects to an `intent://` address.
 */
object ExternalLinks {

    /** Web hosts whose only purpose is to deep-link into an app. */
    private val APP_LINK_HOSTS = setOf(
        "wa.me", "api.whatsapp.com", "chat.whatsapp.com",   // WhatsApp
        "t.me", "telegram.me",                                // Telegram
        "m.me",                                               // Messenger
        "play.google.com",                                    // Play Store
        "maps.google.com", "maps.app.goo.gl",                 // Google Maps
    )

    /** Schemes the WebView renders itself. Anything else goes to the system. */
    private val WEB_SCHEMES = setOf("http", "https", "file", "about", "javascript", "data", "blob")

    /** google.com, www.google.com, google.co.in, www.google.co.uk … */
    private val GOOGLE_HOST = Regex("^(www\\.)?google(\\.[a-z]{2,3}){1,2}$")

    private const val MAPS_PACKAGE = "com.google.android.apps.maps"

    private const val NO_APP_MESSAGE = "No app on this device can open that link."

    enum class Result { LAUNCHED, FALLBACK, FAILED }

    /**
     * True when [uri] should open in another app instead of the WebView.
     * [homeHost] is the app's own start host: a website app that IS one of the app-link
     * hosts keeps browsing itself.
     */
    fun isExternal(uri: Uri, homeHost: String? = null): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme !in WEB_SCHEMES) return true
        if (scheme != "http" && scheme != "https") return false

        val host = uri.host?.lowercase() ?: return false
        if (homeHost != null && host == homeHost.lowercase()) return false
        if (APP_LINK_HOSTS.any { host == it || host.endsWith(".$it") }) return true
        // In a WebView Google answers a maps link with an intent:// redirect, so hand
        // it straight to Maps.
        return isGoogleMaps(uri)
    }

    /**
     * maps.google.com, maps.app.goo.gl, and google.com/maps (the documented
     * https://www.google.com/maps/search/?api=1&query=… form).
     */
    private fun isGoogleMaps(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return host == "maps.google.com" || host == "maps.app.goo.gl" ||
            (GOOGLE_HOST.matches(host) && (uri.path ?: "").startsWith("/maps"))
    }

    private fun tryStart(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    /**
     * Opens [url] in the app that handles it. An `intent:` URI carries its own target and
     * often a `browser_fallback_url`, which [loadFallback] receives when the target app is
     * missing. A link nothing on the device handles is reported with a toast — a dead tap
     * with no feedback reads as a broken app.
     */
    fun open(activity: Activity, url: String, loadFallback: (String) -> Unit): Result {
        return try {
            if (url.startsWith("intent:", ignoreCase = true)) {
                openIntentUri(activity, url, loadFallback)
            } else {
                val uri = Uri.parse(url)
                // Where the Maps app does not own the google.com/maps link, a plain VIEW
                // opens the browser, which then bounces to Maps seconds later. Ask Maps
                // first; without it the browser shows the map as before.
                if (!isGoogleMaps(uri) || !tryStart(activity, Intent(Intent.ACTION_VIEW, uri).setPackage(MAPS_PACKAGE))) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                Result.LAUNCHED
            }
        } catch (_: Exception) {
            Toast.makeText(activity, NO_APP_MESSAGE, Toast.LENGTH_SHORT).show()
            Result.FAILED
        }
    }

    private fun openIntentUri(activity: Activity, url: String, loadFallback: (String) -> Unit): Result {
        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
            // The page controls this intent: limit it to what a browser may launch, the
            // same way Chrome does. No explicit component (which could reach this app's
            // own private activities) and no URI-permission grants.
            addCategory(Intent.CATEGORY_BROWSABLE)
            component = null
            selector?.let {
                it.addCategory(Intent.CATEGORY_BROWSABLE)
                it.component = null
            }
            flags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()
        }
        // Try the launch rather than resolveActivity() first: since Android 11 that query
        // answers "none" for any package not declared in <queries>, which would send every
        // intent:// link to its fallback even with the target app installed.
        try {
            activity.startActivity(intent)
            return Result.LAUNCHED
        } catch (_: ActivityNotFoundException) {
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null && (fallback.startsWith("https://", true) || fallback.startsWith("http://", true))) {
                loadFallback(fallback)
                return Result.FALLBACK
            }
            // No fallback, but the link names its app: offer it in the Play Store.
            val pkg = intent.`package` ?: throw ActivityNotFoundException()
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
            return Result.LAUNCHED
        }
    }

    /**
     * Client for the WebView inside a `target="_blank"` / `window.open` popup. Without it
     * the popup tried to render every non-web link itself and showed ERR_UNKNOWN_URL_SCHEME.
     * When the popup only existed to reach another app, it closes once that app opens.
     */
    fun popupClient(activity: Activity, dialog: Dialog): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!isExternal(request.url)) return false
            val result = open(activity, request.url.toString()) { view.loadUrl(it) }
            if (result != Result.FALLBACK && !view.canGoBack()) dialog.dismiss()
            return true
        }
    }
}
