// Plugins are declared here (not applied) so every module shares one plugin classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Feature modules must never depend on each other; they may only use :core:* modules.
val featureIsolationViolations = mutableListOf<String>()

gradle.projectsEvaluated {
    subprojects
        .filter { it.path.startsWith(":feature:") }
        .forEach { feature ->
            feature.configurations.forEach { configuration ->
                configuration.dependencies
                    .filterIsInstance<ProjectDependency>()
                    .map { it.path }
                    .filter { it.startsWith(":feature:") && it != feature.path }
                    .forEach { featureIsolationViolations += "${feature.path} -> $it (${configuration.name})" }
            }
        }
}

val checkFeatureIsolation by tasks.registering {
    group = "verification"
    description = "Fails if a :feature:* module depends on another :feature:* module."
    val violations = featureIsolationViolations
    doLast {
        check(violations.isEmpty()) {
            "Feature modules must not depend on each other:\n" + violations.joinToString("\n")
        }
    }
}

tasks.register("check") {
    dependsOn(checkFeatureIsolation)
}
