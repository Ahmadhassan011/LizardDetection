plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lizardlens.core.logging"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}
