plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.fitnesstimer"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.fitnesstimer"
        // Spike minSdk: 29 is the floor for BlendMode.Difference (see DECISIONS.md).
        // Not yet the final v1 minSdk decision.
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sideload builds are signed with the standard debug key so they install over
            // earlier v0.1.x builds. The release variant is NOT debuggable, which switches
            // off the debug-only test hooks. A private release keystore is needed before
            // any Play/F-Droid distribution.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.common)
    implementation(libs.media3.session)
    implementation(libs.androidx.palette.ktx)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json is a stub in JVM unit tests; use the real one.
    testImplementation("org.json:json:20240303")
}
