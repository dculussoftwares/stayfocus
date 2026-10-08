plugins {
    id("stayfocused.android.application.compose")
    id("stayfocused.android.hilt")
    id("stayfocused.android.instrumented-test")
}

android {
    namespace = "com.dculus.stayfocused.kids"

    defaultConfig {
        applicationId = "com.dculus.stayfocused.kids"
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:usage"))
    implementation(project(":core:blocking"))
    implementation(project(":core:sync"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.timber)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
