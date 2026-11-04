plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Bundle the clean-room engine schemas as app assets; EngineActivity copies them
    // into app-private storage before starting the native runtime.
    sourceSets.getByName("main").assets.srcDir("../upstream/NVForever/schemas")

    namespace = "com.notadeveloper4665.nvforever"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.notadeveloper4665.nvforever"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.games:games-activity:4.4.2")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core-ktx:1.15.0")
}
