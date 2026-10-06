plugins {
    id("stayfocused.android.library")
    id("stayfocused.android.hilt")
    id("stayfocused.android.room")
}

android {
    namespace = "com.dculus.stayfocused.core.data"
}

dependencies {
    implementation(project(":core:model"))
}
