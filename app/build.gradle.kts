plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Remove compose plugin since we're not using Jetpack Compose
}

android {
    namespace = "com.scu.soilsalinity"
    compileSdk = 35  // Updated to latest stable version (36 might be too new)


    defaultConfig {
        applicationId = "com.scu.soilsalinity"
        minSdk = 26  // Lowered from 28 for better compatibility
        targetSdk = 35
        versionCode = 2
        versionName = "2.7.4.100899"

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
    kotlinOptions {
        jvmTarget = "11"
    }
    // Remove compose build features since we're using WebView
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // WebView dependencies
    implementation(libs.androidx.webkit)

    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // TFLite Model
    implementation("org.tensorflow:tensorflow-lite:2.12.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

}