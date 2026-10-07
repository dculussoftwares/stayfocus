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
}
