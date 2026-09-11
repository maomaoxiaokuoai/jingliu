import com.android.build.gradle.api.ApkVariantOutput
import java.util.Properties

plugins {
    id("com.android.application")
    id("com.chaquo.python")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Public address only. Platform client_secret values belong exclusively to auth-bridge environment.
val qrProperties = Properties().apply {
    val file = rootProject.file("qr-auth.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val qrBridge = qrProperties.getProperty("bridgeUrl", "").trim().trimEnd('/')
require(qrBridge.isEmpty() || Regex("https://[A-Za-z0-9.-]+(?::443)?").matches(qrBridge)) {
    "bridgeUrl must be a plain HTTPS origin; do not add credentials or query parameters"
}
// Release signing comes exclusively from environment / CI secrets.
// Local unsigned builds and PRs fall back to the debug key with a warning.
val releaseKeystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")?.trim()?.takeIf { it.isNotEmpty() }?.let { file(it) }
    ?: rootProject.file("release.keystore").takeIf { it.isFile }
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: ""
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: ""
val hasReleaseSigning = releaseKeystoreFile != null && releaseKeystoreFile.isFile &&
    releaseKeystorePassword.isNotEmpty() && releaseKeyAlias.isNotEmpty() && releaseKeyPassword.isNotEmpty()
if (!hasReleaseSigning) {
    logger.warn("Release signing key not configured; assembleRelease will use the debug key. Configure ANDROID_KEYSTORE_* secrets for distribution builds.")
}
android {
    namespace = "com.luma.downloader"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.luma.downloader"
        minSdk = 26
        targetSdk = 35
        buildConfigField("String", "QR_AUTH_BRIDGE_URL", "\"$qrBridge\"")
        buildConfigField("boolean", "FRAME_METRICS_ENABLED", "false")
        versionCode = 19
        versionName = "0.8.2-split-apks"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs { useLegacyPackaging = true }
        resources { excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
    }
    // Per-ABI APK splits: each APK ships native libs (Chaquopy Python, ffmpeg,
    // lxml/aiohttp wheels) for one ABI only, so downloads stay small.
    // Universal APK is kept for devices with an unknown ABI and for emulators.
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
                storeFile = releaseKeystoreFile!!
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        create("profile") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = false // keep native/runtime integration identical for A/B comparisons
            signingConfig = signingConfigs.getByName("debug") // local testing only; not a production signing key
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "FRAME_METRICS_ENABLED", "true")
        }
    }
    // Distinct versionCode per split APK (universal keeps the base code).
    // Higher code wins on capable devices, so each device prefers its own ABI split.
    applicationVariants.all {
        outputs.map { it as ApkVariantOutput }.forEach { output ->
            val abi = output.getFilter("ABI") ?: return@forEach
            val abiCode = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)[abi] ?: 0
            output.versionCode.set(output.versionCode.get() * 10 + abiCode)
        }
    }
}
dependencies {
    implementation(project(":core"))
    val bom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(bom)
    androidTestImplementation(bom)
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
    // All Media3 modules use the same pinned version. API 26+ only, no desktop binaries.
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.7.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.7.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.7.1")
    implementation("androidx.media3:media3-ui:1.7.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// The full upstream library is built into APK assets, never fetched/installed at app runtime.
// Python 3.11 retains the four ABIs already supported by this project.
val localPython = Properties().apply {
    val config = rootProject.file("python-build.properties")
    if (config.isFile) config.inputStream().use { load(it) }
}
chaquopy {
    defaultConfig {
        version = "3.11"
        extractPackages("fake_useragent")
        localPython.getProperty("buildPython")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            buildPython(it)
        }
        pip {
            install("-r", rootProject.file("python-runtime-requirements.txt").absolutePath)
        }
    }
}
