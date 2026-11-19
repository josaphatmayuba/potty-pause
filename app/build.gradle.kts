plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.famille.rappelpipi"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.famille.rappelpipi"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.6"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
}
