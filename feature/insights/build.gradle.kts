plugins {
    id("stayfocused.android.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dculus.stayfocused.feature.insights"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:usage"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
