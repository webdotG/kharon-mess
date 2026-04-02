plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kharon.messenger"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kharon.messenger"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val ksPath = (project.findProperty("KEYSTORE_PATH") as? String) ?: System.getenv("KEYSTORE_PATH")
            val ksPass = (project.findProperty("KEYSTORE_PASS") as? String) ?: System.getenv("KEYSTORE_PASS")
            val kAlias = (project.findProperty("KEY_ALIAS") as? String) ?: System.getenv("KEY_ALIAS")
            val kPass = (project.findProperty("KEY_PASS") as? String) ?: System.getenv("KEY_PASS")

            storeFile = if (!ksPath.isNullOrEmpty()) file(ksPath) else null
            storePassword = ksPass
            keyAlias = kAlias
            keyPassword = kPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Нужно для JNA (lazysodium)
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // ── Compose ───────────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ── Core ──────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // ── Hilt DI ───────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Крипто ───────────────────────────────────────────────────────────────
    implementation("com.goterl:lazysodium-android:5.2.0@aar")
    implementation("net.java.dev.jna:jna:5.17.0@aar")

    // ── Сеть ─────────────────────────────────────────────────────────────────
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging)

    // ── Хранилище ────────────────────────────────────────────────────────────
    implementation(libs.security.crypto)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)

    // ── QR ────────────────────────────────────────────────────────────────────
    implementation(libs.zxing.android.embedded)
    implementation(libs.zxing.core)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Сериализация ──────────────────────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ── Тесты ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
