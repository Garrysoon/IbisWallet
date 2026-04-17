plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

android {
    namespace = "github.aeonbtc.ibiswallet"
    compileSdk = 36

    defaultConfig {
        applicationId = "github.aeonbtc.ibiswallet"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "3.1.2-beta"

        vectorDrawables {
            useSupportLibrary = true
        }

        // BDK native library only works reliably on ARM architectures
        // x86/x86_64 emulators have compatibility issues
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // LZ4 compression for native libraries (10-15% smaller than gzip)
            useLegacyPackaging = false
            // Remove debug symbols from native libraries (20-30% reduction)
            keepDebugSymbols += listOf()
        }
    }

    // Split APKs by ABI for side-loading (F-Droid, GitHub)
    // Reduces APK size from ~100MB to ~50MB per architecture
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false  // Don't build fat APK
        }
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.extensions.configure(JacocoTaskExtension::class) {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.register<JacocoReport>("jacocoUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    sourceDirectories.setFrom(files("${projectDir}/src/main/java"))

    val excludes = listOf(
        "**/R.class", "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/databinding/**",
        "**/ui/**",
        "**/theme/**",
    )
    classDirectories.setFrom(
        fileTree("${layout.buildDirectory.get().asFile}/intermediates/javac/debug/classes") { exclude(excludes) },
        fileTree("${layout.buildDirectory.get().asFile}/tmp/kotlin-classes/debug") { exclude(excludes) },
    )

    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/testDebugUnitTest.exec") },
    )
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Bitcoin Development Kit
    implementation(libs.bdk.android)
    // Silent Payments (BIP 352) - secp256k1 operations via BouncyCastle
    // Using bcprov-jdk15to18 for full ECC support (secp256k1 curve)
    // Excluding unnecessary PKIX and utilities to reduce size (~3-5MB -> ~2-3MB)
    implementation(libs.bouncycastle.bcprov) {
        exclude(group = "org.bouncycastle", module = "bcpkix-jdk15to18")
        exclude(group = "org.bouncycastle", module = "bcutil-jdk15to18")
    }

    // Security & Storage
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.google.material)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // QR Code
    implementation(libs.zxing.core)

    // Camera
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Tor Network
    implementation(libs.tor.android)

    // HTTP Client
    implementation(libs.okhttp)

    // BC-UR (Uniform Resources) for animated QR codes (PSBT exchange with hardware wallets)
    implementation(libs.hummingbird)

    // Liquid Wallet Kit (LWK) - Blockstream's Liquid Network wallet toolkit
    implementation(libs.lwk)
    implementation(libs.jna) { artifact { type = "aar" } }
    implementation(libs.lightning.kmp.core.jvm)

    // Silent Payments (BIP 352) - secp256k1 operations
    // NOTE: Full secp256k1 integration requires native/JNI libraries.
    // For now using stub implementations. Add fr.acinq.secp256k1:secp256k1-kmp
    // or bitcoinj secp256k1 when ready for production crypto.

    // Testing
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
}
