import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply(libs.plugin("android-application"))
            configureAndroid()
            applyGoogleServicesIfConfigured()
            configureRelease()
            extensions.configure<ApplicationExtension> {
                defaultConfig.targetSdk = TARGET_SDK
            }
            dependencies {
                add("testImplementation", libs.library("junit4"))
            }
        }
}

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("stayfocused.android.application")
            configureCompose()
        }
}

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply(libs.plugin("android-library"))
            configureAndroid()
            dependencies {
                add("testImplementation", libs.library("junit4"))
            }
        }
}

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("stayfocused.android.library")
            configureCompose()
        }
}

/** Feature modules: Compose + Hilt + `:core:ui`, `:core:model`, `:core:data`. Never another feature. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("stayfocused.android.library.compose")
            pluginManager.apply("stayfocused.android.hilt")
            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:data"))
                add("implementation", libs.library("androidx-navigation-compose"))
                add("implementation", libs.library("androidx-hilt-navigation-compose"))
            }
        }
}

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply(libs.plugin("ksp"))
            pluginManager.apply(libs.plugin("hilt"))
            dependencies {
                add("implementation", libs.library("hilt-android"))
                add("ksp", libs.library("hilt-compiler"))
            }
        }
}

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply(libs.plugin("ksp"))
            extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
                // Schemas are exported to <module>/schemas and committed; CI fails on drift without a version bump.
                arg("room.schemaLocation", "${project.projectDir}/schemas")
            }
            dependencies {
                add("implementation", libs.library("androidx-room-runtime"))
                add("implementation", libs.library("androidx-room-ktx"))
                add("ksp", libs.library("androidx-room-compiler"))
            }
        }
}
