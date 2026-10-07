plugins {
    id("stayfocused.android.application.compose")
    id("stayfocused.android.hilt")
}

android {
    namespace = "com.dculus.stayfocused"

    defaultConfig {
        applicationId = "com.dculus.stayfocused"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:usage"))
    implementation(project(":core:blocking"))
    implementation(project(":core:sync"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:block"))
    implementation(project(":feature:devices"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:account"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.timber)
}
