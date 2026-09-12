import java.util.Properties

plugins {
    id("com.android.application")
    id("com.chaquo.python")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciVersionName = providers.environmentVariable("JINGLIU_VERSION_NAME")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
val ciVersionCode = providers.environmentVariable("JINGLIU_VERSION_CODE")
    .orNull
    ?.trim()
    ?.toIntOrNull()
require(ciVersionCode == null || ciVersionCode in 1..2_100_000_000) {
    "JINGLIU_VERSION_CODE must be an Android-compatible positive integer"
}

// Optional public OAuth bridge origin. Credentials must never be embedded in the APK.
val qrProperties = Properties().apply {
    val file = rootProject.file("qr-auth.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val qrBridge = qrProperties.getProperty("bridgeUrl", "").trim().trimEnd('/')
require(qrBridge.isEmpty() || Regex("https://[A-Za-z0-9.-]+(?::443)?").matches(qrBridge)) {
    "bridgeUrl must be a plain HTTPS origin without credentials, paths or query parameters"
}

// Release signing is supplied by GitHub Actions environment variables.
// When no release key is configured, assembleRelease falls back to the debug signing key.
val releaseKeystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let { file(it) }
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: ""
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: ""
val hasReleaseSigning = releaseKeystoreFile?.isFile == true &&
    releaseKeystorePassword.isNotEmpty() &&
    releaseKeyAlias.isNotEmpty() &&
    releaseKeyPassword.isNotEmpty()

if (!hasReleaseSigning) {
    logger.warn(
        "Release signing key not configured; assembleRelease will use the debug key. " +
            "Configure ANDROID_KEYSTORE_* repository secrets for update-compatible releases."
    )
}

android {
    namespace = "com.luma.downloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.luma.downloader"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode ?: 20
        versionName = ciVersionName ?: "0.8.2"
        buildConfigField("String", "QR_AUTH_BRIDGE_URL", "\"$qrBridge\"")
        buildConfigField("boolean", "FRAME_METRICS_ENABLED", "false")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }

    // One APK per ABI plus a universal APK for direct GitHub distribution.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = requireNotNull(releaseKeystoreFile)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.7.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.7.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.7.1")
    implementation("androidx.media3:media3-ui:1.7.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

val localPython = Properties().apply {
    val config = rootProject.file("python-build.properties")
    if (config.isFile) config.inputStream().use { load(it) }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        extractPackages("fake_useragent")
        localPython.getProperty("buildPython")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { buildPython(it) }
        pip {
            install("-r", rootProject.file("python-runtime-requirements.txt").absolutePath)
        }
    }
}
