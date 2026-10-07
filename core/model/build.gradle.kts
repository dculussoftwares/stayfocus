plugins {
    id("stayfocused.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

// The AI sentence fixture is shared with the Gemini evals (M9-04), so it lives at the repo root.
sourceSets.test {
    resources.srcDir(rootProject.layout.projectDirectory.dir("fixtures"))
}
