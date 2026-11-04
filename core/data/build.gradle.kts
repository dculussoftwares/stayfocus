plugins {
    id("vesper.android.library")
    id("vesper.android.hilt")
    id("vesper.android.room")
}

android {
    namespace = "com.dculus.vesper.core.data"

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
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(project(":core:testing"))
    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
