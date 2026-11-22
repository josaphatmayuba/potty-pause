plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.famille.rappelpipi"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        targetSdk = 34   // Google Play exige l'API 36 pour les nouvelles applis : a passer avant la mise en ligne.
        versionCode = 10
        versionName = "1.9"
    }
    // direct = APK GitHub (auto-mise a jour, icone cachable) ; play = Google Play (aucune auto-mise a jour).
    flavorDimensions += "dist"
    productFlavors {
        create("direct") {
            dimension = "dist"
            applicationId = "com.famille.rappelpipi"
        }
        create("play") {
            dimension = "dist"
            applicationId = "com.pottypause.app"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // Connexion Google du parent (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
