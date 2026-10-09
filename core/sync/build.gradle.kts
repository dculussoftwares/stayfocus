plugins {
    id("stayfocused.android.library")
    id("stayfocused.android.hilt")
}

android {
    namespace = "com.dculus.stayfocused.core.sync"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// The emulator tests skip themselves without the emulator; make the env part of the cache key so a skipped run is
// never reused as a passing emulator run.
tasks.withType<Test>().configureEach {
    inputs.property("authEmulator", providers.environmentVariable("FIREBASE_AUTH_EMULATOR_HOST").orElse(""))
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
