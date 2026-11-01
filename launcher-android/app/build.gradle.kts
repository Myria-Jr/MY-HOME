plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.myria.home"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.myria.home"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "3.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Fester Schlüssel, damit neue Versionen einfach drüber-installiert werden können.
    signingConfigs {
        create("island") {
            storeFile = file("island.keystore")
            storePassword = "islandisland"
            keyAlias = "island"
            keyPassword = "islandisland"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("island")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("island")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
