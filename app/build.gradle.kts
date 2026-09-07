plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Properties

fun resolveProperty(dev: Properties, local: Properties, key: String, fallback: String = ""): String {
    return dev.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
        ?: local.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
        ?: fallback
}

fun buildConfigString(value: String): String {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

fun requiredLocalProperty(properties: Properties, key: String): String =
    properties.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
        ?: error("Missing required $key in local.properties")

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

val devProperties = Properties().apply {
    val devPropertiesFile = rootProject.file("local.dev.properties")
    if (devPropertiesFile.exists()) {
        load(devPropertiesFile.inputStream())
    }
}

val tmdbApiKey = requiredLocalProperty(localProperties, "TMDB_API_KEY")

fun env(name: String): String? = providers.environmentVariable(name).orNull

fun truthy(value: String?): Boolean {
    return value.equals("true", ignoreCase = true) ||
        value.equals("1", ignoreCase = true) ||
        value.equals("yes", ignoreCase = true)
}

val useDebugReleaseSigning = env("CI_USE_DEBUG_SIGNING").equals("true", ignoreCase = true)
val useLocalFfmpegDecoder = truthy(
    providers.gradleProperty("useLocalFfmpegDecoder").orNull
        ?: env("USE_LOCAL_FFMPEG_DECODER")
        ?: localProperties.getProperty("USE_LOCAL_FFMPEG_DECODER")
)
val releaseStoreFilePath = env("NUVIO_RELEASE_STORE_FILE")
    ?: localProperties.getProperty("NUVIO_RELEASE_STORE_FILE")
val releaseKeyAliasValue = env("NUVIO_RELEASE_KEY_ALIAS")
    ?: localProperties.getProperty("NUVIO_RELEASE_KEY_ALIAS", "nuviotv")
val releaseKeyPasswordValue = env("NUVIO_RELEASE_KEY_PASSWORD")
    ?: localProperties.getProperty("NUVIO_RELEASE_KEY_PASSWORD", "815787")
val releaseStorePasswordValue = env("NUVIO_RELEASE_STORE_PASSWORD")
    ?: localProperties.getProperty("NUVIO_RELEASE_STORE_PASSWORD", "815787")

android {
    namespace = "com.nuvio.tv"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.jeziel.lume"
        minSdk = 24
        targetSdk = 36
        versionCode = 1062
        versionName = "0.7.37-beta"

        buildConfigField("String", "INTRODB_API_URL", buildConfigString(localProperties.getProperty("INTRODB_API_URL", "https://api.introdb.app/")))
        buildConfigField("String", "ANIMESKIP_CLIENT_ID", buildConfigString(localProperties.getProperty("ANIMESKIP_CLIENT_ID", "")))
        buildConfigField("String", "TRAILER_API_URL", "\"${localProperties.getProperty("TRAILER_API_URL", "")}\"")
        buildConfigField("String", "TMDB_API_KEY", buildConfigString(tmdbApiKey))
        // Native Dolby Vision tooling is not built; the Kotlin Dolby bridge reports
        // the compile-time capability as disabled (see core/player/DoviBridge.kt).
        buildConfigField("boolean", "DOVI_NATIVE_ENABLED", "false")
        buildConfigField("boolean", "DOVI_EXTRACTOR_HOOK_READY", "false")

        // In-app updater (GitHub Releases)
        buildConfigField("String", "GITHUB_OWNER", "\"jeziel-jr\"")
        buildConfigField("String", "GITHUB_REPO", "\"Lume\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
        }
        create("playstore") {
            dimension = "distribution"
            applicationId = "com.jeziel.lume"
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = releaseKeyAliasValue
            keyPassword = releaseKeyPasswordValue
            storeFile = releaseStoreFilePath?.let(::file) ?: file("../nuviotv.jks")
            storePassword = releaseStorePasswordValue
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
            isMinifyEnabled = false

            buildConfigField("boolean", "IS_DEBUG_BUILD", "true")

            // Dev environment (from local.dev.properties)
            buildConfigField("String", "INTRODB_API_URL", buildConfigString(resolveProperty(devProperties, localProperties, "INTRODB_API_URL", "https://api.introdb.app/")))
            buildConfigField("String", "ANIMESKIP_CLIENT_ID", buildConfigString(resolveProperty(devProperties, localProperties, "ANIMESKIP_CLIENT_ID")))
            buildConfigField("String", "TRAILER_API_URL", "\"${devProperties.getProperty("TRAILER_API_URL", "")}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (useDebugReleaseSigning) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }

            buildConfigField("boolean", "IS_DEBUG_BUILD", "false")
        }
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "IS_DEBUG_BUILD", "true")
            applicationIdSuffix = ".debug"
            matchingFallbacks += "release"
        }
    }

    splits {
        abi {
            // Produce a single universal APK instead of one APK per CPU architecture.
            isEnable = false
        }
    }

    bundle {
        language {
            // Keep all string resources in the
            // base install so Play Store installs can switch languages at runtime.
            // https://developer.android.com/guide/app-bundle/configure-base
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            // Keep one consistent native set across dependencies.
            pickFirsts += listOf(
                "lib/*/libc++_shared.so",
                "lib/*/libavcodec.so",
                "lib/*/libavdevice.so",
                "lib/*/libavfilter.so",
                "lib/*/libavformat.so",
                "lib/*/libavutil.so",
                "lib/*/libswscale.so",
                "lib/*/libswresample.so"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Keep lint off the release path: lintVital was gating every release build
        // with results nobody consumes. Manual `lint` tasks remain available.
        checkReleaseBuilds = false
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.applicationId.set("com.jeziel.lume.debug")
    }
}

composeCompiler {
    // Enable Compose compiler metrics for performance analysis
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
    reportsDestination = layout.buildDirectory.dir("compose_reports")
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability_config.conf"))
}

// Globally exclude stock media3 modules — replaced by local :nuvio-exoplayer-engine module
configurations.all {
    exclude(group = "androidx.media3", module = "media3-exoplayer")
    exclude(group = "androidx.media3", module = "media3-common")
    exclude(group = "androidx.media3", module = "media3-datasource")
    exclude(group = "androidx.media3", module = "media3-datasource-okhttp")
    exclude(group = "androidx.media3", module = "media3-exoplayer-hls")
    exclude(group = "androidx.media3", module = "media3-extractor")
}

baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
    baselineProfileOutputDir = "generated/baselineProfiles"
    filter {
        include("com.nuvio.tv.**")
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")

    // Source-retention nullness annotations (MonotonicNonNull / RequiresNonNull /
    // EnsuresNonNull) used by the vendored Matroska extractor in
    // com.nuvio.tv.core.player.dvmkv. Media3 keeps these compileOnly in its own
    // build, so they aren't on our classpath via the prebuilt AARs.
    compileOnly("org.checkerframework:checker-qual:3.43.0")

    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.profileinstaller)
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.activity:activity-compose:1.11.0")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    implementation(libs.lottie.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // ViewModel
    implementation(libs.lifecycle.viewmodel.compose)

    // Media3 — remaining stock modules from Maven (not forked)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.smoothstreaming)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.decoder)
    implementation(libs.media3.session)
    implementation(libs.media3.container)

    // Transitive dependencies required by forked local AARs (not bundled in AARs):
    // - Guava: needed by lib-common (ImmutableList/ImmutableSet in Tracks, Player API)
    // - media3-database: needed by lib-datasource (cache/storage layer)
    // - annotation-experimental: needed by lib-common (OptIn annotations)
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("androidx.media3:media3-database:1.8.0")
    implementation("androidx.annotation:annotation-experimental:1.3.1")

    // Nuvio Engine local AARs (replaces lib-exoplayer, lib-common, lib-datasource, lib-datasource-okhttp, lib-exoplayer-hls, lib-extractor)
    implementation(files(
        "libs/lib-common-release.aar",
        "libs/lib-datasource-release.aar",
        "libs/lib-datasource-okhttp-release.aar",
        "libs/lib-exoplayer-release.aar",
        "libs/lib-exoplayer-hls-release.aar",
        "libs/lib-extractor-release.aar"
    ))
    implementation(libs.media3.ui)

    // Local decoder AARs (AV1; IAMF/MPEG-H removed)
    implementation(files("libs/lib-decoder-av1-release.aar"))
    if (useLocalFfmpegDecoder) {
        implementation(project(":ffmpeg-decoder-downmix"))
    } else {
        implementation(files("libs/lib-decoder-ffmpeg-release.aar"))
    }

    // libass-android for ASS/SSA subtitle support (from Maven Central)
    implementation("io.github.peerless2012:ass-media:0.4.0")
    // Local nextlib-mediainfo fork (static FFmpeg; no libav*.so in final AAR)
    implementation(files("libs/nextlib-mediainfo-local.aar"))
    implementation("dev.chrisbanes.haze:haze-android:0.7.3") {
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.compose.foundation")
    }

    implementation(libs.gson)

    // WebJar crypto-js for the Xtream QR pairing page (served by XtreamSetupServer)
    add("fullImplementation", libs.crypto.js)

    // Markdown rendering
    implementation(libs.markdown.renderer.m3)

    // QR code + local server for Xtream pairing setup
    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Performance profiling
    implementation("androidx.metrics:metrics-performance:1.0.0-rc01")  // JankStats
    debugImplementation("androidx.compose.runtime:runtime-tracing")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.12")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
