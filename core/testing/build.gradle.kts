plugins {
    id("stayfocused.android.library")
}

android {
    namespace = "com.dculus.stayfocused.core.testing"
}

dependencies {
    api(project(":core:data"))
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.test)
    api(libs.junit4)
    // Instrumented-test harness (HiltTestRunner); Hilt's test artifact is only used by androidTest source sets.
    api(libs.androidx.test.runner)
    api(libs.androidx.test.ext.junit)
    api(libs.hilt.android.testing)
}
