package com.web2app

import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * The `AndroidAds` object the bundled page can call.
 *
 * ### Why a bridge at all
 *
 * A banner and an every-Nth-page interstitial can be scheduled by the app, because the
 * app knows when a page changed. A rewarded ad cannot: it is a bargain the PAGE offers
 * — "watch this for 50 coins" — so only the page knows what is on offer, whether the
 * user accepted, and what to hand over afterwards. The trigger has to come from
 * JavaScript, and the outcome has to go back to JavaScript.
 *
 * ### The security edge
 *
 * `addJavascriptInterface` exposes this object to EVERY page the WebView loads, remote
 * ones included. A Pro app may have tab bar entries pointing at real websites, and
 * those must not be able to spend the publisher's ad inventory or fake a reward. So
 * every method checks that the calling page is the bundled site before doing anything
 * — see [isLocalPage]. Nothing here is callable from a remote origin.
 *
 * Methods arrive on the WebView's JavaBridge thread, never the main thread, so every
 * one of them hops back before touching an ad or the WebView.
 *
 * ### The API
 *
 * ```js
 * AndroidAds.isRewardedReady()                  // boolean
 * AndroidAds.showRewarded("coins_50")           // tag is echoed back to you
 * AndroidAds.isRewardedInterstitialReady()
 * AndroidAds.showRewardedInterstitial("level_2")
 * AndroidAds.showInterstitial()                 // fire-and-forget
 *
 * // Define these to hear back. All are optional.
 * window.onAdReward = function (r) { /* {tag, type, amount} — ad was WATCHED */ };
 * window.onAdClosed = function (r) { /* {tag} — dismissed, rewarded or not */ };
 * window.onAdUnavailable = function (r) { /* {tag} — nothing was ready to show */ };
 * ```
 *
 * Hand out the reward in `onAdReward` and nowhere else: it is the only callback that
 * means the ad was actually watched.
 */
class AdsBridge(
    private val activity: Activity,
    private val webView: WebView,
    private val rewarded: AdRewarded?,
    private val rewardedInterstitial: AdRewarded?,
    private val interstitial: AdInterstitial?,
) {
    companion object {
        /** The name the page calls: `AndroidAds.showRewarded(...)`. */
        const val NAME = "AndroidAds"
    }

    /**
     * True when the page in the WebView right now is the bundled site.
     *
     * Read on the main thread only (WebView.getUrl is not thread-safe), which every
     * entry point already arranges.
     */
    private fun isLocalPage(): Boolean =
        webView.url?.startsWith(LocalContent.ORIGIN) == true

    /** Runs [block] on the main thread, but only for the bundled site. */
    private fun guarded(block: () -> Unit) {
        activity.runOnUiThread {
            if (isLocalPage()) block()
        }
    }

    @JavascriptInterface
    fun isRewardedReady(): Boolean = rewarded?.isReady == true

    @JavascriptInterface
    fun isRewardedInterstitialReady(): Boolean = rewardedInterstitial?.isReady == true

    @JavascriptInterface
    fun showRewarded(tag: String?) = guarded {
        show(rewarded, tag.orEmpty())
    }

    @JavascriptInterface
    fun showRewardedInterstitial(tag: String?) = guarded {
        show(rewardedInterstitial, tag.orEmpty())
    }

    /**
     * Shows an interstitial now, for a page that would rather choose its own moment
     * than leave it to the every-Nth-page rule. Silent if none is ready.
     */
    @JavascriptInterface
    fun showInterstitial() = guarded {
        interstitial?.showNow()
    }

    private fun show(ad: AdRewarded?, tag: String) {
        if (ad == null || !ad.isEnabled) return callback("onAdUnavailable", tag)
        ad.show(
            onReward = { type, amount -> callback("onAdReward", tag, type, amount) },
            onClosed = { callback("onAdClosed", tag) },
            onUnavailable = { callback("onAdUnavailable", tag) },
        )
    }

    /**
     * Calls `window.<name>({...})` if the page defined it. Built with JSONObject so a
     * tag containing a quote cannot break out of the call — the page chooses the tag,
     * and it comes back through here.
     */
    private fun callback(name: String, tag: String, type: String? = null, amount: Int = 0) {
        val payload = JSONObject().put("tag", tag)
        if (type != null) payload.put("type", type).put("amount", amount)
        val js = "if (typeof window.$name === 'function') { window.$name($payload); }"
        activity.runOnUiThread { webView.evaluateJavascript(js, null) }
    }
}
