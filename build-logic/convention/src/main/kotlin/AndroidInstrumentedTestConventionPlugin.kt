import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

internal const val HILT_TEST_RUNNER = "com.dculus.stayfocused.core.testing.HiltTestRunner"
internal const val CI_DEVICES_GROUP = "ciDevices"

/** Managed device name -> (API level, system image source). */
internal val CI_DEVICES =
    mapOf(
        // ATD images only exist from API 30, so the minSdk device uses the plain AOSP image.
        "pixel6Api26" to (26 to "aosp"),
        "pixel6Api36" to (36 to "google-atd"),
    )

/**
 * Instrumented tests for an application module: the Hilt test runner from `:core:testing`, the AndroidX Test and
 * Compose UI test dependencies, and the Gradle Managed Devices group `ciDevices`
 * (`./gradlew ciDevicesGroupDebugAndroidTest` runs it headless).
 * Apply after `stayfocused.android.application` and `stayfocused.android.hilt`.
 */
class AndroidInstrumentedTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            extensions.configure<ApplicationExtension> {
                defaultConfig.testInstrumentationRunner = HILT_TEST_RUNNER
                val devices = testOptions.managedDevices.allDevices
                CI_DEVICES.forEach { (name, config) ->
                    devices.create(name, ManagedVirtualDevice::class.java).apply {
                        device = "Pixel 6"
                        apiLevel = config.first
                        systemImageSource = config.second
                    }
                }
                testOptions.managedDevices.groups.create(CI_DEVICES_GROUP).apply {
                    CI_DEVICES.keys.forEach { targetDevices.add(devices.getByName(it)) }
                }
            }
            dependencies {
                add("androidTestImplementation", project(":core:testing"))
                add("androidTestImplementation", libs.library("androidx-test-runner"))
                add("androidTestImplementation", libs.library("androidx-test-ext-junit"))
                add("androidTestImplementation", libs.library("androidx-compose-ui-test-junit4"))
                add("androidTestImplementation", libs.library("hilt-android-testing"))
                add("kspAndroidTest", libs.library("hilt-compiler"))
            }
        }
}
