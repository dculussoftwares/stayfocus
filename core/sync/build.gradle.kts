plugins {
    id("stayfocused.android.library")
    id("stayfocused.android.hilt")
}

android {
    namespace = "com.dculus.stayfocused.core.sync"

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // App Check debug token for CI/emulator builds. Comes from the environment (or -P), never committed.
            val token =
                providers
                    .environmentVariable("APP_CHECK_DEBUG_TOKEN")
                    .orElse(providers.gradleProperty("appCheckDebugToken"))
                    .orElse("")
                    .get()
            buildConfigField("String", "APP_CHECK_DEBUG_TOKEN", "\"$token\"")
        }
        release {
            buildConfigField("String", "APP_CHECK_DEBUG_TOKEN", "\"\"")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.androidx.core.ktx)
}
