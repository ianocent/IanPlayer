plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val ksProps = rootProject.file("keystore.properties").readLines()
    .filter { it.contains("=") }
    .associate {
        val (k, v) = it.split("=", limit = 2)
        k.trim() to v.trim()
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
        versionCode = 14
        versionName = "5.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}