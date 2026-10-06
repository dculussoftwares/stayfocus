import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

internal const val COMPILE_SDK = 37
internal const val TARGET_SDK = 36
internal const val MIN_SDK = 26
internal const val JAVA_VERSION = 17

/** Lint checks that map to Google Play policy or target-SDK requirements. */
internal val PLAY_LINT_CHECKS =
    setOf(
        "ExpiredTargetSdkVersion",
        "OldTargetApi",
        "QueryAllPackagesPermission",
        "ScopedStorage",
        "ProtectedPermissions",
        "MissingPermission",
        "HardwareIds",
    )

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

internal fun VersionCatalog.plugin(alias: String) = findPlugin(alias).get().get().pluginId

/** Compile/target SDK, Java 17 and the Kotlin toolchain shared by every Android module. */
internal fun Project.configureAndroid() {
    extensions.configure<CommonExtension> {
        compileSdk = COMPILE_SDK
        defaultConfig.minSdk = MIN_SDK
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        lint.apply {
            abortOnError = true
            checkDependencies = true
            // Play-relevant checks are errors, never warnings.
            error += PLAY_LINT_CHECKS
            sarifReport = true
            htmlReport = true
        }
        // Roborazzi / Robolectric need merged resources on the unit-test classpath.
        testOptions.unitTests.isIncludeAndroidResources = true
    }
    configureKotlin()
}

internal fun Project.configureKotlin() {
    extensions.configure<KotlinBaseExtension> {
        jvmToolchain(JAVA_VERSION)
    }
}

/** Turns on Compose with the Kotlin Compose compiler plugin and adds the shared Compose dependencies. */
internal fun Project.configureCompose() {
    pluginManager.apply(libs.plugin("kotlin-compose"))
    extensions.configure<CommonExtension> {
        buildFeatures.compose = true
    }
    dependencies {
        val bom = libs.library("androidx-compose-bom")
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        add("implementation", libs.library("androidx-compose-ui"))
        add("implementation", libs.library("androidx-compose-material3"))
        add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
        add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
    }
}

/**
 * Applies the google-services plugin only when this app module has a `google-services.json`.
 * Forks and CI build without the file; Firebase then stays uninitialised (see `FirebaseAvailability`).
 */
internal fun Project.applyGoogleServicesIfConfigured() {
    if (file("google-services.json").exists()) {
        pluginManager.apply(libs.plugin("google-services"))
    } else {
        logger.lifecycle("$path: no google-services.json, building without Firebase configuration.")
    }
}
