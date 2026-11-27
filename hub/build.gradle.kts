plugins {
    alias(libs.plugins.android.application)
    // Same pair as the app module (which builds fine): kotlin-compose
    // provides the Kotlin support here. Applying kotlin-android directly
    // conflicts — its 'kotlin' extension is already registered.
    alias(libs.plugins.kotlin.compose)
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
