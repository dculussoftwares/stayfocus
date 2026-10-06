import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply(libs.plugin("kotlin-jvm"))
            configureKotlin()
            dependencies {
                add("testImplementation", libs.library("junit4"))
            }
        }
}
