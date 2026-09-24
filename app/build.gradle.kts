plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val numeroBuild = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val keystore = System.getenv("KEYSTORE_FILE")

android {
    namespace = "app.corridaverde"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.corridaverde"
        minSdk = 26
        targetSdk = 35
        versionCode = numeroBuild
        versionName = "1.$numeroBuild"
    }

    signingConfigs {
        create("release") {
            if (keystore != null) {
                storeFile = file(keystore)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
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

dependencies {
    testImplementation("junit:junit:4.13.2")
}
