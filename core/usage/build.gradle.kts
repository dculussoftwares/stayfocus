plugins {
    id("stayfocused.android.library")
    id("stayfocused.android.hilt")
}

android {
    namespace = "com.dculus.stayfocused.core.usage"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
