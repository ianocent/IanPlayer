plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

val ksProps = rootProject.file("keystore.properties").readLines()
    .filter { it.contains("=") }
    .associate {
        val (k, v) = it.split("=", limit = 2)
        k.trim() to v.trim()
    }

// InnerTube client identifiers live in gitignored keys.properties, not in source.
val localProps: Map<String, String> = rootProject.file("keys.properties").let { f ->
    if (f.exists()) f.readLines()
        .filter { it.contains("=") && !it.trim().startsWith("#") }
        .associate {
            val (k, v) = it.split("=", limit = 2)
            k.trim() to v.trim()
        }
    else emptyMap()
}

android {
    namespace = "com.ianocent.musicplayer"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = file(ksProps["storeFile"] ?: "")
            storePassword = ksProps["storePassword"] ?: ""
            keyAlias = ksProps["keyAlias"] ?: ""
            keyPassword = ksProps["keyPassword"] ?: ""
        }
    }

    defaultConfig {
        applicationId = "com.ianocent.musicplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 36
        versionName = "6.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "YTMUSIC_WEB_KEY", "\"${localProps["YTMUSIC_WEB_KEY"] ?: ""}\"")
        buildConfigField("String", "YTMUSIC_ANDROID_KEY", "\"${localProps["YTMUSIC_ANDROID_KEY"] ?: ""}\"")
        buildConfigField("String", "MAPS_API_KEY", "\"${localProps["MAPS_API_KEY"] ?: ""}\"")
        manifestPlaceholders["MAPS_API_KEY"] = localProps["MAPS_API_KEY"] ?: ""
    }

    testOptions {
        unitTests {
            // Domain objects carry android.net.Uri; Robolectric supplies real
            // implementations for the modules under test.
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")
    implementation("sh.calvin.reorderable:reorderable:2.4.0")
    // YouTube PoToken (BotGuard) generation + signature cipher deobfuscation for streaming.
    // Pinned to a specific commit (not master-SNAPSHOT) for reproducible builds.
    implementation("com.github.ZemerTeam:zemer-cipher:55ef918b75")
    // zemer-cipher logs via Timber but only "compileOnly" depends on it; without this it would
    // crash at runtime with NoClassDefFoundError the first time it logs.
    implementation("com.jakewharton.timber:timber:5.0.1")
    // For writing audio metadata tags (MP3)
    implementation("com.mpatric:mp3agic:0.9.1")
    // Audio metadata tags for MP4/M4A containers (YouTube audio downloads are AAC-in-MP4)
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Maps
    implementation("com.google.android.gms:play-services-maps:19.0.0")
}