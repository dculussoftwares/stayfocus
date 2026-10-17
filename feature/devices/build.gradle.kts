plugins {
    id("stayfocused.android.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dculus.stayfocused.feature.devices"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:sync"))
    implementation(libs.kotlinx.coroutines.android)
    // QR scanning: CameraX preview + analysis, ML Kit's Play-services (unbundled) barcode model keeps the APK small.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
