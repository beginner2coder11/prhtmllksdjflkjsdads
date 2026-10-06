package com.web2app

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.web2app.models.AppConfig

/**
 * The full-screen ad shown between pages, every Nth navigation.
 *
 * ### Why it is built this way
 *
 * An interstitial is the format most likely to get an AdMob account suspended, because
 * the rules are about WHEN it appears rather than whether it is implemented correctly.
 * The ones this class enforces:
 *
 *  - **Never on the first page.** The app opening is not a transition the user chose;
 *    an ad there reads as a launch ad and is the single most complained-about pattern.
 *  - **Only at a navigation**, once the new page has finished loading. The ad covers
 *    content that is ready, so dismissing it reveals the page rather than a spinner.
 *  - **Never two in quick succession.** Beyond the every-Nth rule there is a hard
 *    floor of [MIN_GAP_MS] between ads, so a burst of quick navigations cannot chain
 *    interstitials together.
 *  - **Only a preloaded ad.** Requesting one at the moment it is needed means either a
 *    visible stall or an ad that arrives after the user has moved on; the next one is
 *    requested as soon as the current is dismissed.
 *  - **Nothing at all when unconfigured.** No unit id, or a frequency of 0, means the
 *    SDK is never asked for anything.
 *
 * What it deliberately does NOT do is show on back-navigation out of the app, or over
 * a page still loading — both are accidental-click territory.
 */
class AdInterstitial(
    private val activity: Activity,
    private val config: AppConfig,
) {
    private var ad: InterstitialAd? = null
    private var loading = false

    /** Navigations seen since the last ad. The first page load is not counted. */
    private var navigations = 0

    /** Uptime of the last ad shown, for the hard floor between ads. */
    private var lastShownAt = 0L

    /** True once the first page has finished: only then does a change count. */
    private var firstPageDone = false

    private val unitId: String get() = config.ads.interstitial

    /** Every how many page changes an ad is due. 0 (or no unit id) disables the format. */
    private val every: Int get() = config.ads.interstitialEvery

    private val enabled: Boolean
        get() = config.ads.enabled && unitId.isNotBlank() && every > 0

    /** Requests the first ad, so one is ready before it is due. */
    fun preload() {
        if (!enabled || ad != null || loading) return
        loading = true
        InterstitialAd.load(
            activity, unitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    loading = false
                    ad = loaded
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    ad = null
                    // No fill is routine. The next navigation that comes due tries again.
                    Log.d(TAG, "interstitial not loaded: ${error.code} ${error.message}")
                }
            }
        )
    }

    /**
     * Call when a page has finished loading. Counts the navigation and shows an ad if
     * one is due, available, and far enough from the last.
     */
    fun onPageFinished() {
        if (!enabled) return
        if (!firstPageDone) {
            // The app opening is not a page change. Start counting from here.
            firstPageDone = true
            preload()
            return
        }
        navigations++
        if (navigations < every) return
        if (SystemClock.elapsedRealtime() - lastShownAt < MIN_GAP_MS) return

        val ready = ad ?: run {
            // Nothing to show: leave the counter at the threshold so the ad appears at
            // the next navigation once one has loaded, rather than restarting the wait.
            preload()
            return
        }
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                lastShownAt = SystemClock.elapsedRealtime()
                navigations = 0
                // Load the next one now, so it is ready long before it is due.
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                preload()
            }
        }
        ready.show(activity)
    }

    /**
     * Shows an interstitial immediately, for a page that asked through the bridge.
     * Still honours the gap between ads — a page cannot chain them by calling in a
     * loop — and silently does nothing when none is ready.
     */
    fun showNow() {
        if (!enabled) return
        if (SystemClock.elapsedRealtime() - lastShownAt < MIN_GAP_MS) return
        val ready = ad ?: return preload()
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                lastShownAt = SystemClock.elapsedRealtime()
                navigations = 0
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                preload()
            }
        }
        ready.show(activity)
    }

    private companion object {
        const val TAG = "AdInterstitial"

        /**
         * The shortest gap between two interstitials, whatever the navigation count.
         * A user clicking quickly through a bundled site can cross the threshold in
         * seconds; without this they would meet an ad each time.
         */
        const val MIN_GAP_MS = 60_000L
    }
}
