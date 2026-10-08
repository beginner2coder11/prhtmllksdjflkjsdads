package com.web2app

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * The rewarded ad, shown when the PAGE asks for one.
 *
 * Unlike the banner and the interstitial, these cannot be scheduled by the app: a
 * rewarded ad is a bargain — watch this, get that — and only the page knows what it is
 * offering and when the user accepted. So the trigger comes from JavaScript (see
 * [AdsBridge]) and the outcome goes back the same way.
 *
 * The reward is reported ONLY from onUserEarnedReward. A user who dismisses the ad
 * early gets [onClosed] without a reward, which is the whole point of the format: the
 * page must not hand out anything until this class says the ad was actually watched.
 */
class AdRewarded(
    private val activity: Activity,
    private val unitId: String,
) {
    private var rewarded: RewardedAd? = null
    private var loading = false

    val isEnabled: Boolean get() = unitId.isNotBlank()

    val isReady: Boolean get() = rewarded != null

    /**
     * Requests one ahead of time; a rewarded ad must never be loaded on demand.
     * Waits for consent first — see [AdsConsent].
     */
    fun preload() {
        if (!isEnabled || isReady || loading) return
        if (!AdsConsent.canRequestAds) {
            AdsConsent.whenReady(activity) { preload() }
            return
        }
        loading = true
        runCatching {
        RewardedAd.load(
            activity, unitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading = false
                    rewarded = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    rewarded = null
                    Log.d(TAG, "rewarded not loaded: ${error.code} ${error.message}")
                }
            }
        )
        }.onFailure { loading = false; Log.w(TAG, "rewarded request failed", it) }
    }

    /**
     * Shows the ad. [onReward] fires only if it was watched to the reward point;
     * [onClosed] always fires once it is gone, rewarded or not; [onUnavailable] means
     * nothing was ready to show, so the page should leave its UI alone.
     */
    fun show(
        onReward: (type: String, amount: Int) -> Unit,
        onClosed: () -> Unit,
        onUnavailable: () -> Unit,
    ) {
        if (!isEnabled) return onUnavailable()
        val callback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                onClosed()
                // The next one starts loading now, so a second tap is not a dead button.
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewarded = null
                onUnavailable()
                preload()
            }
        }
        val ad = rewarded ?: return run { preload(); onUnavailable() }
        runCatching {
            ad.fullScreenContentCallback = callback
            ad.show(activity) { onReward(it.type, it.amount) }
        }.onFailure {
            Log.w(TAG, "rewarded show failed", it)
            rewarded = null
            onUnavailable()
            preload()
        }
    }

    private companion object {
        const val TAG = "AdRewarded"
    }
}
