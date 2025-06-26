plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.producepal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.producepal"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        mlModelBinding = true
    }
}

val camerax_version = "1.4.2"

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // CameraX dependencies
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // TensorFlow Lite for ML inference
    implementation("org.tensorflow:tensorflow-lite:2.12.0")

    // Guava for ListenableFuture used by CameraX
    implementation("com.google.guava:guava:31.0.1-android")
    implementation(libs.camera.view)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}


