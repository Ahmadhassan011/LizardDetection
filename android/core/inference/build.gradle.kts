plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lizardlens.core.inference"
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
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:logging"))
    implementation("androidx.core:core-ktx:1.13.1")

    // TFLite — mock fallback when model file not present
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // TFLite GPU delegate (optional, loaded via reflection)
    compileOnly("org.tensorflow:tensorflow-lite-gpu:2.16.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")

    // Instrumented testing (real TFLite model on device)
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
}
