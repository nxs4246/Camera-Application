plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.cameraapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cameraapplication"
        minSdk = 24
        targetSdk = 35
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    // implementation(libs.core)
    // implementation(libs.androidx.junit)

    // Unit testing dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:3.12.4")
    testImplementation("org.mockito:mockito-inline:3.12.4")
    testImplementation("io.mockk:mockk:1.13.10") // For local JVM tests
    testImplementation ("org.robolectric:robolectric:4.6.1") // Robolectric for simulating Android components in unit tests

    // AndroidX Test (for Instrumentation/UI tests)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation("androidx.test.ext:junit:1.1.3") // AndroidX JUnit test extension
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0") // Espresso for UI testing
    androidTestImplementation("androidx.test:core:1.4.0") // Core test libraries
    androidTestImplementation("androidx.test:runner:1.4.0") // Test runner for instrumentation tests

    // AndroidX Test - ActivityScenario for UI testing
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
}