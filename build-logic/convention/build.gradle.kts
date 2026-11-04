plugins {
    `kotlin-dsl`
}

group = "com.dculus.vesper.buildlogic"

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
            id = "vesper.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "vesper.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "vesper.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "vesper.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "vesper.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "vesper.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "vesper.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidInstrumentedTest") {
            id = "vesper.android.instrumented-test"
            implementationClass = "AndroidInstrumentedTestConventionPlugin"
        }
        register("manifestAudit") {
            id = "vesper.manifest-audit"
            implementationClass = "ManifestAuditPlugin"
        }
        register("jvmLibrary") {
            id = "vesper.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
