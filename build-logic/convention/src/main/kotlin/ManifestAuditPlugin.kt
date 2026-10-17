import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Applied to the root project. Registers `verifyManifests`, which audits the merged release manifests of `:app` and
 * `:kids`, their targetSdk and every accessibility-service config (see [ManifestAudit]). Part of `ciCheck`.
 */
class ManifestAuditPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val aggregate =
            target.tasks.register("verifyManifests") {
                group = "verification"
                description = "Fails if a manifest breaks the Play policy guard (docs/play/COMPLIANCE.md)."
            }
        val roles = mapOf(":app" to AppRole.APP, ":kids" to AppRole.KIDS)
        roles.forEach { (path, role) ->
            target.project(path).plugins.withId("com.android.application") {
                val app = target.project(path)
                val targetSdk =
                    app.provider {
                        app.extensions
                            .getByType(ApplicationExtension::class.java)
                            .defaultConfig.targetSdk
                    }
                val configs =
                    target.fileTree(target.projectDir) {
                        include("**/src/main/res/xml/*.xml")
                        exclude("**/build/**", "**/.gradle/**")
                    }
                val components = app.extensions.getByType(AndroidComponentsExtension::class.java)
                components.onVariants(components.selector().withBuildType("release")) { variant ->
                    val taskName = "verify${variant.name.replaceFirstChar { it.uppercase() }}Manifest"
                    val task =
                        app.tasks.register(taskName, VerifyManifestTask::class.java) {
                            group = "verification"
                            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                            appRole.set(role.name)
                            expectedTargetSdk.set(targetSdk.map { it ?: -1 })
                            accessibilityConfigs.from(configs)
                        }
                    aggregate.configure { dependsOn(task) }
                }
            }
        }
    }
}

abstract class VerifyManifestTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val appRole: Property<String>

    @get:Input
    abstract val expectedTargetSdk: Property<Int>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val accessibilityConfigs: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val violations = mutableListOf<String>()
        val manifest = mergedManifest.get().asFile.readText()
        violations += ManifestAudit.auditManifest(manifest, AppRole.valueOf(appRole.get()))
        violations += ManifestAudit.auditTargetSdk(expectedTargetSdk.get().takeIf { it >= 0 })
        accessibilityConfigs.files
            .map { it to it.readText() }
            .filter { (_, text) -> "<accessibility-service" in text }
            .forEach { (file, text) ->
                ManifestAudit.auditAccessibilityConfig(text).forEach { violations += "${file.name}: $it" }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Manifest audit failed for ${appRole.get()}:\n" +
                    violations.joinToString("\n") { " - $it" } +
                    "\nSee docs/play/COMPLIANCE.md.",
            )
        }
    }
}
