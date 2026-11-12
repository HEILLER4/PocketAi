plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pocketai.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pocketai.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-beta"
    }
    signingConfigs {
    create("release") {
        storeFile = file("${System.getProperty("user.home")}/.keys/pocketai-release.jks")
        storePassword = System.getenv("POCKETAI_STORE_PASSWORD") ?: ""
        keyAlias = "pocketai"
        keyPassword = System.getenv("POCKETAI_KEY_PASSWORD") ?: ""
    }
}

buildTypes {
    getByName("release") {
        applicationIdSuffix = ".beta"
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = false
    }
}

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1")
}
