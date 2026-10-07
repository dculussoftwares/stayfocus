plugins {
    id("stayfocused.android.library.compose")
    id("stayfocused.android.hilt")
}

android {
    namespace = "com.dculus.stayfocused.core.blocking"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:usage"))
    implementation(project(":core:ui"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
}
