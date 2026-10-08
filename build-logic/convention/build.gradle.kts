plugins {
    `kotlin-dsl`
}

group = "com.dculus.stayfocused.buildlogic"

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.android.gradleCommonApi)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.composeGradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.google.services.gradlePlugin)

    testImplementation(libs.junit4)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "stayfocused.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "stayfocused.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "stayfocused.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "stayfocused.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "stayfocused.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "stayfocused.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "stayfocused.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidInstrumentedTest") {
            id = "stayfocused.android.instrumented-test"
            implementationClass = "AndroidInstrumentedTestConventionPlugin"
        }
        register("jvmLibrary") {
            id = "stayfocused.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
