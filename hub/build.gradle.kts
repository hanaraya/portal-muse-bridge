plugins {
    alias(libs.plugins.android.application)
    // kotlin-android is already on the classpath via the root's kotlin-compose
    // plugin (same kotlin-gradle-plugin artifact), so it must be applied
    // without a version — a versioned request fails with "already on the
    // classpath with an unknown version".
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.portal.hub"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "com.portal.hub"
        minSdk = 28
        targetSdk = 29
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
