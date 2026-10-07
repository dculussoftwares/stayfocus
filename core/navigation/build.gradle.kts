plugins {
    id("stayfocused.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dculus.stayfocused.core.navigation"
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
