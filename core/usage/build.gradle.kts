plugins {
    id("stayfocused.android.library")
    id("stayfocused.android.hilt")
}

android {
    namespace = "com.dculus.stayfocused.core.usage"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
}
