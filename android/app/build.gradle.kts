plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.myria.abodetox"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.myria.abodetox"
        minSdk = 26
        targetSdk = 35
        // CI sets GITHUB_RUN_NUMBER so every build can be installed over the previous one
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "3.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    // Personal app: the key lives in the repo so every build has the same SHA-1
    // (the one registered as Android OAuth client in Google Cloud).
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("abodetox.jks")
            storePassword = "abodetox"
            keyAlias = "abodetox"
            keyPassword = "abodetox"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Single source of truth: the web app is bundled as-is.
    sourceSets["main"].assets.srcDirs("../../abo-detox")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}
