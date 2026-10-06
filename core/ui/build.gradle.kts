plugins {
    id("stayfocused.android.library.compose")
}

android {
    namespace = "com.dculus.stayfocused.core.ui"
}

dependencies {
    implementation(project(":core:model"))
}
