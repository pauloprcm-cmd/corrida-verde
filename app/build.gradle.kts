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

    // github: a versão de hoje, com o atualizador. play: vai para a Play Store, sem o atualizador
    // e sem a permissão de instalar pacotes (a Play proíbe, e os bancos tratam como sinal de risco).
    flavorDimensions += "loja"
    productFlavors {
        create("github") {
            dimension = "loja"
            buildConfigField("boolean", "ATUALIZADOR", "true")
            buildConfigField("boolean", "GRAVACAO", "true")
        }
        create("play") {
            dimension = "loja"
            buildConfigField("boolean", "ATUALIZADOR", "false")
            // Gravação da corrida fica de fora da Play no começo (câmera e microfone pedem declaração com vídeo).
            buildConfigField("boolean", "GRAVACAO", "false")
        }
    }

    buildTypes {
        release {
            // R8: embaralha os nomes do código (difícil de copiar) e tira o que não é usado (APK menor).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
