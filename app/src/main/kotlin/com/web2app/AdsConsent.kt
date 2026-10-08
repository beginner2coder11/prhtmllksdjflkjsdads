package com.web2app

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlin.concurrent.thread

/**
 * The consent gate every ad in this app waits behind.
 *
 * AdMob requires publishers serving the EEA, the UK or Switzerland to use a certified
 * CMP registered with IAB's Transparency and Consent Framework before showing
 * personalised ads. UMP — the library used here — is Google's own certified CMP, so
 * satisfying the rule is a matter of actually running its flow rather than adding
 * anything exotic:
 *
 *   1. ask UMP what this user's consent state is ([ConsentInformation]);
 *   2. show the form if one is required, and wait for the answer;
 *   3. start the Mobile Ads SDK only once [ConsentInformation.canRequestAds] says so.
 *
 * Nothing in this app requests an ad before [whenReady] fires, so a user who has not
 * answered the form never sees one.
 *
 * ### What the publisher still has to do
 *
 * This code is only half of it. The owner of the AdMob account behind a built app must
 * create a GDPR message for that app in their own AdMob console (Privacy & messaging →
 * GDPR). With no message configured UMP answers "nothing to show" and reports a
 * publisher misconfiguration — which is what the AdMob policy notice is about.
 */
object AdsConsent {

    /** True once the SDK has been started and ads may be requested. */
    @Volatile
    private var ready = false

    private var resolving = false
    private val waiters = mutableListOf<() -> Unit>()

    val canRequestAds: Boolean get() = ready

    /**
     * Runs [block] as soon as ads are permitted — immediately if they already are.
     * Callbacks land on the main thread.
     */
    fun whenReady(activity: Activity, block: () -> Unit) {
        if (ready) return runCatching { block() }.getOrElse { Log.w(TAG, "ad callback failed", it) }
        synchronized(waiters) { waiters += block }
        resolve(activity)
    }

    /**
     * Every UMP call sits behind this.
     *
     * The app exists to show the user's content; ads are an extra. A publisher who has
     * not created a consent message, a Play services build that answers oddly, a device
     * with no WebView for the form — none of that may take the app down. On a throw the
     * app simply runs without ads, which is the same outcome as consent being refused.
     */
    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            resolving = false
            Log.w(TAG, "consent $what failed; continuing without ads", e)
        }
    }

    private fun resolve(activity: Activity) = guard("lookup") {
        if (ready || resolving) return@guard
        resolving = true
        val info = UserMessagingPlatform.getConsentInformation(activity)

        // A decision made on an earlier launch is readable without the network, so a
        // returning user's ads start immediately rather than behind a round trip. The
        // refresh below still runs, and still surfaces a form if one became required.
        if (info.canRequestAds()) start(activity)


        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                guard("form") {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    // The form's own error is not the authority on anything;
                    // canRequestAds() is, whether the form showed or not.
                    if (error != null) Log.d(TAG, "consent form: ${error.message}")
                    resolving = false
                    if (info.canRequestAds()) start(activity)
                }
                }
            },
            { error ->
                // UMP could not answer at all — offline, or no message configured in
                // the publisher's AdMob account.
                //
                // This fails CLOSED: no ad is requested. The alternative, serving
                // anyway, is exactly the personalised-ads-without-consent case the
                // TCF rule exists to prevent, and it would put the publisher's own
                // AdMob account at risk rather than ours. A user who opens the app
                // again once UMP is reachable gets ads then.
                resolving = false
                Log.d(TAG, "consent unavailable: ${error.message}")
            }
        )
    }

    /** Starts the Mobile Ads SDK once, then releases everything waiting on consent. */
    private fun start(activity: Activity) {
        if (ready) return
        ready = true
        val app: Context = activity.applicationContext
        // initialize() reaches Play services over Binder and can block for seconds on
        // a loaded device, so it never runs on the main thread.
        thread { runCatching { MobileAds.initialize(app) } }
        val pending = synchronized(waiters) { waiters.toList().also { waiters.clear() } }
        // One failing waiter must not stop the others, or take the app with it.
        activity.runOnUiThread {
            pending.forEach { runCatching { it() }.onFailure { e -> Log.w(TAG, "ad start failed", e) } }
        }
    }

    private const val TAG = "AdsConsent"
}
