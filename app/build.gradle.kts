import groovy.json.JsonSlurper

plugins {
    // AGP 9.0+ has built-in Kotlin support, so the org.jetbrains.kotlin.android
    // plugin is no longer applied. See https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.application)
}

// Per-app identity (package, version, name) is injected from the pushed
// app_settings.json so each build produces its own installable app. Missing
// values fall back to the template defaults.
val appSettings: Map<*, *> = file("src/main/assets/app_settings.json").let { f ->
    if (f.exists()) JsonSlurper().parse(f) as Map<*, *> else emptyMap<String, Any?>()
}
val cfgPackage = (appSettings["package"] as? String)?.takeIf { it.isNotBlank() } ?: "com.web2app"
val cfgVersionName = (appSettings["versionName"] as? String)?.takeIf { it.isNotBlank() } ?: "1.0"
val cfgVersionCode = (appSettings["versionCode"] as? Number)?.toInt() ?: 1
val cfgAppName = (appSettings["appName"] as? String)?.takeIf { it.isNotBlank() } ?: "My App"

android {
    // Code/resource package stays fixed; only the installed applicationId varies.
    namespace = "com.web2app"
    compileSdk = 36

    defaultConfig {
        applicationId = cfgPackage
        minSdk = 21
        targetSdk = 36
        versionCode = cfgVersionCode
        versionName = cfgVersionName

        // Drives android:label so the launcher shows the user's app name.
        manifestPlaceholders["appLabel"] = cfgAppName
    }

    // Fixed release signing key committed to the repo so every release build is
    // signed with the SAME key. Without a stable key, installing an updated build
    // over a prior one fails with "package conflicts with an existing package".
    signingConfigs {
        create("release") {
            // When the builder generated a per-project signing key, CI downloads it
            // and points these env vars at it, so the build is signed with the user's
            // own key. Otherwise fall back to the shared default keystore (keeps
            // rebuilds installable over each other for previews / main builds).
            val envStore = System.getenv("SIGN_STORE_FILE")
            if (!envStore.isNullOrBlank() && file(envStore).exists()) {
                storeFile = file(envStore)
                storePassword = System.getenv("SIGN_STORE_PASSWORD")
                keyAlias = System.getenv("SIGN_KEY_ALIAS")
                keyPassword = System.getenv("SIGN_KEY_PASSWORD")
            } else {
                storeFile = file("signing/web2app-release.keystore")
                storePassword = "web2apppro"
                keyAlias = "webtoapppro"
                keyPassword = "web2apppro"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.swiperefreshlayout)
    // Serves a bundled HTML/ZIP site over an https origin — see LocalContent.
    implementation(libs.androidx.webkit)

    // Push notifications (OneSignal). FCM credentials live in the OneSignal
    // dashboard, so no google-services.json is needed in the app. Only activated
    // at runtime when app_settings.json has pushNotification + an oneSignalAppId.
    implementation("com.onesignal:OneSignal:5.1.6")
}
