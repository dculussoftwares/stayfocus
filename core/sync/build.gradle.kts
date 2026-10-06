plugins {
    id("stayfocused.android.library")
    id("stayfocused.android.hilt")
}

android {
    namespace = "com.dculus.stayfocused.core.sync"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
}
