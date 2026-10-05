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
        // A versão de teste (ramo beta) aparece como "1.38 teste" na tela do app.
        versionName = "1.$numeroBuild" + if (System.getenv("GITHUB_REF_NAME") == "beta") " teste" else ""
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
    buildFeatures {
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Desenha o QR Code do Pix (só a parte de gerar; Java puro, sem AndroidX).
    implementation("com.google.zxing:core:3.5.3")
    // Lê o cartão da oferta da 99 no print (OCR). Versão dos serviços do Google: o leitor é baixado uma vez,
    // fora do APK, que continua pequeno para o atualizador.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    testImplementation("junit:junit:4.13.2")
}
