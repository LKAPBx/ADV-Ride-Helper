import java.util.Properties
import java.io.FileInputStream

plugins { id("com.android.application") }
android {
    namespace = "dev.africatwin.telemetry"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "dev.africatwin.telemetry"
        minSdk = 28
        targetSdk = 37
        versionCode = 115
        versionName = "0.115"
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("local.properties")
            val properties = Properties()
            if (keystorePropertiesFile.exists()) {
                properties.load(FileInputStream(keystorePropertiesFile))
            }

            storeFile = properties.getProperty("storeFile")?.let { file(it) }
                ?: System.getenv("KEYSTORE_FILE")?.let { file(it) }
            storePassword = properties.getProperty("storePassword")
                ?: System.getenv("KEYSTORE_PASSWORD")
            keyAlias = properties.getProperty("keyAlias")
                ?: System.getenv("KEY_ALIAS")
            keyPassword = properties.getProperty("keyPassword")
                ?: System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
        create("bikeTest") {
            initWith(getByName("release"))
            isDebuggable = false
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    // RC selected because Google's release notes recommend its security fix.
    implementation("androidx.car.app:app:1.8.0-rc01")
    implementation("androidx.car.app:app-projected:1.8.0-rc01")
    // Bundled Latin OCR model; dashboard image recognition runs on-device.
    implementation("com.google.mlkit:text-recognition:16.0.1")
}

