plugins {
    id("stayfocused.android.library")
    id("stayfocused.android.hilt")
    id("stayfocused.android.room")
}

android {
    namespace = "com.dculus.stayfocused.core.data"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        // Exported Room schemas feed MigrationTestHelper (Robolectric unit tests and instrumented tests).
        getByName("test").assets.directories.add("$projectDir/schemas")
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

dependencies {
    implementation(project(":core:model"))

    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
