plugins {
    id("vesper.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dculus.vesper.core.navigation"
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
