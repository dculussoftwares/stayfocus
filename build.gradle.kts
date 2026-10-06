// Plugins are declared here (not applied) so every module shares one plugin classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// ---------------------------------------------------------------------------------------------
// Code quality: Spotless (ktlint + Compose rules), detekt, Kover, Roborazzi. See docs: CI = ciCheck.
// ---------------------------------------------------------------------------------------------

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
            .customRuleSets(
                listOf(
                    libs.compose.rules.ktlint
                        .get()
                        .let { "${it.module}:${it.versionConstraint.requiredVersion}" },
                ),
            )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
    source.setFrom(
        files(rootDir).asFileTree.matching {
            include("**/src/**/*.kt")
            exclude("**/build/**")
        },
    )
    parallel = true
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required = true
        sarif.required = true
        checkstyle.required = false
        markdown.required = false
    }
}

subprojects {
    // Modules are still empty skeletons; drop this once every module has tests.
    tasks.withType<Test>().configureEach { failOnNoDiscoveredTests = false }

    apply(plugin = "org.jetbrains.kotlinx.kover")
    rootProject.dependencies.add("kover", this)

    // Roborazzi: `recordRoborazziDebug` locally, `verifyRoborazziDebug` in CI.
    plugins.withId("com.android.library") { apply(plugin = "io.github.takahirom.roborazzi") }
    plugins.withId("com.android.application") { apply(plugin = "io.github.takahirom.roborazzi") }

    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
        reports.filters.excludes { excludeFromCoverage() }
        // Minimum line coverage for the logic modules (the overall 60% bound is in the root `kover` block).
        if (path == ":core:model" || path == ":core:blocking") {
            reports.verify.rule("Logic line coverage") { minBound(80) }
        }
    }
}

kover {
    reports {
        filters.excludes { excludeFromCoverage() }
        verify.rule("Overall line coverage") { minBound(60) }
    }
}

// Android entry points, generated code and previews are covered by UI/screenshot/E2E tests, not line coverage.
// Applied to the merged report (root) and to every module's own report, which the per-module bounds use.
fun kotlinx.kover.gradle.plugin.dsl.KoverReportFilter.excludeFromCoverage() {
    classes(
        "*.BuildConfig",
        "*.R",
        "*.R$*",
        "*ComposableSingletons*",
        "*_Factory",
        "*_Factory$*",
        "*_HiltModules*",
        "*_MembersInjector",
        "Hilt_*",
        "*.Hilt_*",
        "dagger.hilt.*",
        "hilt_aggregated_deps.*",
        // Plain data holders in :core:model (no logic; their generated members are not worth testing).
        // Logic types (DaysOfWeek, TimeRange, DialConfig, KnownApps, formatters) stay covered. Add new data holders here.
        "com.dculus.stayfocused.core.model.AppInfo",
        "com.dculus.stayfocused.core.model.AppUsage",
        "com.dculus.stayfocused.core.model.Block",
        "com.dculus.stayfocused.core.model.BlockSource",
        "com.dculus.stayfocused.core.model.BlockTarget*",
        "com.dculus.stayfocused.core.model.BlockType",
        "com.dculus.stayfocused.core.model.BreakSession",
        "com.dculus.stayfocused.core.model.DayUsage",
        "com.dculus.stayfocused.core.model.FocusSession",
        "com.dculus.stayfocused.core.model.LimitPeriod",
        "com.dculus.stayfocused.core.model.LinkedDevice",
        "com.dculus.stayfocused.core.model.LockedApp",
        "com.dculus.stayfocused.core.model.Permissions",
        "com.dculus.stayfocused.core.model.TamperAlert",
        "com.dculus.stayfocused.core.model.TamperKind",
        "com.dculus.stayfocused.core.model.TemporaryAllowance",
        "com.dculus.stayfocused.core.model.UnlockRequest*",
        "*.Placeholder", // empty module placeholders; delete this line when the modules get real code
        "*.MainActivity",
        "*.StayFocusedApp",
        "*.StayFocusedKidsApp",
    )
    annotatedBy("androidx.compose.ui.tooling.preview.Preview", "javax.annotation.processing.Generated")
}

// Aggregates so CI jobs and `ciCheck` run the same thing. Android modules use `lint`/`testDebugUnitTest`,
// JVM modules use `test`.
val lintAll = tasks.register("lintAll") { group = "verification" }
val unitTests = tasks.register("unitTests") { group = "verification" }
val verifyScreenshots = tasks.register("verifyScreenshots") { group = "verification" }

subprojects {
    plugins.withId("com.android.library") { registerAndroidQualityTasks() }
    plugins.withId("com.android.application") { registerAndroidQualityTasks() }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        rootProject.tasks.named("unitTests") { dependsOn(tasks.named("test")) }
    }
}

fun Project.registerAndroidQualityTasks() {
    rootProject.tasks.named("lintAll") { dependsOn(tasks.named("lint")) }
    rootProject.tasks.named("unitTests") { dependsOn(tasks.named("testDebugUnitTest")) }
    rootProject.tasks.named("verifyScreenshots") { dependsOn(tasks.named("verifyRoborazziDebug")) }
}

// Everything the CI jobs run, locally: the Definition-of-done command.
tasks.register("ciCheck") {
    group = "verification"
    description =
        "Runs spotlessCheck, detekt, lint, unit tests, Kover verification, screenshot tests and assembleDebug."
    dependsOn("spotlessCheck", "detekt", lintAll, unitTests, "koverXmlReport", "koverVerify", verifyScreenshots)
    dependsOn(subprojects.filter { it.path == ":app" || it.path == ":kids" }.map { "${it.path}:assembleDebug" })
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

val checkFeatureIsolation =
    tasks.register("checkFeatureIsolation") {
        group = "verification"
        description = "Fails if a :feature:* module depends on another :feature:* module."
        val violations = featureIsolationViolations
        doLast {
            check(violations.isEmpty()) {
                "Feature modules must not depend on each other:\n" + violations.joinToString("\n")
            }
        }
    }

tasks.named("check") {
    dependsOn(checkFeatureIsolation)
}
