import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Firma della release: solo da variabili d'ambiente (in CI dai Secrets di
 * GitHub). Nessuna chiave o password nel repository. Senza queste variabili
 * la release si compila comunque, ma non firmata.
 */
val releaseKeystore: File? = System.getenv("EROIDEWEB_KEYSTORE_PATH")
    ?.let(::File)
    ?.takeIf { it.exists() }

android {
    namespace = "com.eroideweb.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.eroideweb.browser"
        minSdk = 26
        targetSdk = 37
        // In CI arrivano dal tag (v1.2.3 → 1.2.3 e 10203); in locale i default.
        versionCode = System.getenv("EROIDEWEB_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("EROIDEWEB_VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        getByName("debug") {
            // In CI la chiave debug è sempre la stessa (cache di Actions): gli APK
            // si aggiornano sopra la versione precedente senza disinstallare.
            // In locale resta la chiave debug di default.
            val ciKeystore = System.getenv("EROIDEWEB_DEBUG_KEYSTORE")
            if (ciKeystore != null && File(ciKeystore).exists()) {
                storeFile = file(ciKeystore)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("EROIDEWEB_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("EROIDEWEB_KEY_ALIAS")
                keyPassword = System.getenv("EROIDEWEB_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
        // Come la release (R8, non debuggable: fluida come quella vera) ma firmata
        // con la chiave debug. Serve a provare le prestazioni reali senza keystore.
        create("anteprima") {
            initWith(getByName("release"))
            applicationIdSuffix = ".anteprima"
            versionNameSuffix = "-anteprima"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    // Schemi Room versionati nel repository: servono alle AutoMigration e a
    // garantire che un aggiornamento non perda cronologia e segnalibri.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.webkit)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
