package com.web2app

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.web2app.models.AppConfig

/**
 * The anchored banner at the bottom of the built app.
 *
 * Everything here is conditional on the app having been built with a real AdMob
 * account: with no ids in app_settings.json the SDK is never initialised, no request
 * is made, and the container stays collapsed. That matters because this runtime ships
 * the Ads SDK in *every* build, including apps whose owner never asked for ads.
 *
 * The slot is also collapsed until an ad actually arrives, so a failed or unfilled
 * request costs no screen space rather than leaving a grey band above the content.
 */
class AdBanner(
    private val activity: Activity,
    private val config: AppConfig,
) {
    private var container: FrameLayout? = null
    private var view: AdView? = null

    /** True when this build can show ads at all. */
    private val enabled: Boolean
        get() = config.ads.enabled && config.ads.banner.isNotBlank()

    /**
     * Initialises the SDK and loads the first banner. Safe to call when ads are not
     * configured — it returns without touching the SDK.
     */
    fun attach(slot: FrameLayout) {
        container = slot
        if (!enabled) return
        // Off the main thread: initialize() reaches Play services over Binder and can
        // block for seconds on a loaded device.
        Thread { runCatching { MobileAds.initialize(activity) { load() } } }.start()
    }

    private fun load() = activity.runOnUiThread {
        val slot = container ?: return@runOnUiThread
        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

        val ad = AdView(activity).apply {
            adUnitId = config.ads.banner
            setAdSize(sizeFor(slot))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    slot.visibility = View.VISIBLE
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // No fill is routine, not an error worth showing anyone. Leave the
                    // slot collapsed; the next launch tries again.
                    slot.visibility = View.GONE
                    Log.d(TAG, "banner not loaded: ${error.code} ${error.message}")
                }
            }
        }
        view = ad
        slot.removeAllViews()
        slot.addView(ad)
        runCatching { ad.loadAd(AdRequest.Builder().build()) }
    }

    /**
     * The size the project asked for. Adaptive is the default and the one Google
     * recommends — it returns the height that suits this device's width, so the banner
     * looks right on a phone and on a tablet instead of a fixed 320x50 stranded in the
     * middle of a wide screen.
     */
    private fun sizeFor(slot: View): AdSize = when (config.ads.bannerSize) {
        "rectangle" -> AdSize.MEDIUM_RECTANGLE
        "large" -> AdSize.LARGE_BANNER
        "banner" -> AdSize.BANNER
        else -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp(slot))
    }

    /** Usable width in dp, which is what the adaptive size is calculated from. */
    private fun widthDp(slot: View): Int {
        val density = activity.resources.displayMetrics.density
        val px = if (slot.width > 0) slot.width else activity.resources.displayMetrics.widthPixels
        return (px / density).toInt().coerceAtLeast(320)
    }

    fun resume() = view?.resume()
    fun pause() = view?.pause()

    fun destroy() {
        view?.destroy()
        view = null
        container = null
    }

    private companion object {
        const val TAG = "AdBanner"
    }
}
