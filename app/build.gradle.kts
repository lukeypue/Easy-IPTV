plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Select distribution without changing application identity or customer data.
val ryzodDistribution = providers.gradleProperty("ryzodDistribution").getOrElse("direct")
require(ryzodDistribution in setOf("direct", "fire", "amazon", "play")) { "Unknown ryzodDistribution" }
val tvArtwork = ryzodDistribution in setOf("fire", "amazon")
val selfUpdates = ryzodDistribution in setOf("direct", "fire")

val releaseKeystorePath = System.getenv("ZAKO_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ZAKO_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("ZAKO_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ZAKO_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            // Playback/recording singletons and native graphics are process-owned.
            // Keep independent test classes from inheriting another screen's state.
            it.forkEvery = 1
        }
    }
    namespace = "com.easyiptv.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.easyiptv.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 109
        versionName = "4.85"
        buildConfigField("boolean", "TV_ARTWORK", tvArtwork.toString())
        buildConfigField("boolean", "SELF_UPDATES", selfUpdates.toString())
        buildConfigField("String", "DISTRIBUTION", "\"$ryzodDistribution\"")
    }

    sourceSets {
        for (variant in listOf("debug", "release")) {
            getByName(variant) {
                if(tvArtwork) res.srcDir("src/fire/res")
                if(!selfUpdates) manifest.srcFile("src/store/AndroidManifest.xml")
            }
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            // ZAKO_V450_RELEASE_HEADROOM: shrink only the signed customer build.
            // This reduces dead bytecode/resources without changing DVR/player tuning.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required by the FFmpeg audio decoder (the "always have sound" feature)
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Helper the FFmpeg decoder needs on older Android versions
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.6.0")

    val media3Version = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    // Prebuilt FFmpeg software audio decoder (AC-3, E-AC-3, DTS, MP2, TrueHD, etc.)
    // so channels with Dolby audio still have sound even if the phone lacks the codec.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
