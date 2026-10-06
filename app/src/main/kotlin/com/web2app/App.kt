package com.web2app

import android.app.Application
import com.onesignal.OneSignal
import com.web2app.models.parseAppConfig

/**
 * Application entry point. Initializes OneSignal push as early as possible when the
 * bundled app_settings.json has push enabled and a OneSignal App ID configured in the
 * builder. FCM credentials live in the OneSignal dashboard, so nothing else (no
 * google-services.json) is needed in the app. When push is off or no App ID is set,
 * the SDK is never touched, so default builds are unaffected.
 *
 * The runtime permission prompt (POST_NOTIFICATIONS on Android 13+) is requested from
 * [SplashActivity], which already has a window to attach the system dialog to.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val config = parseAppConfig(readJson(this, "app_settings.json"))
        if (config.pushNotification && config.oneSignalAppId.isNotBlank()) {
            OneSignal.initWithContext(this, config.oneSignalAppId)
        }
    }
}
