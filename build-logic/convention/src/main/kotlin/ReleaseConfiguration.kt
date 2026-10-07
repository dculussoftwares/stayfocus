import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Environment variables that carry the upload key in CI (see docs/RELEASING.md). Never committed. */
private const val ENV_KEYSTORE = "STAYFOCUSED_KEYSTORE_FILE"
private const val ENV_STORE_PASSWORD = "STAYFOCUSED_KEYSTORE_PASSWORD"
private const val ENV_KEY_ALIAS = "STAYFOCUSED_KEY_ALIAS"
private const val ENV_KEY_PASSWORD = "STAYFOCUSED_KEY_PASSWORD"

/**
 * Release build type shared by `:app` and `:kids`: version from `-PversionName` / `-PversionBuild`, R8 with the
 * shared rules, and signing with the upload key when the env vars are present. Without them (forks, local
 * builds) the release build is still produced, unsigned, so `bundleRelease` never needs secrets.
 */
internal fun Project.configureRelease() {
    val version =
        ReleaseVersion.parse(
            raw = providers.gradleProperty("versionName").orElse(ReleaseVersion.DEFAULT_NAME).get(),
            build =
                providers
                    .gradleProperty("versionBuild")
                    .map(String::toInt)
                    .orNull,
        )
    val keystore = providers.environmentVariable(ENV_KEYSTORE).orNull?.takeIf { it.isNotBlank() }

    extensions.configure<ApplicationExtension> {
        defaultConfig {
            versionCode = version.code
            versionName = version.name
        }
        if (keystore != null) {
            signingConfigs.create("release") {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable(ENV_STORE_PASSWORD).get()
                keyAlias = providers.environmentVariable(ENV_KEY_ALIAS).get()
                keyPassword = providers.environmentVariable(ENV_KEY_PASSWORD).get()
            }
        } else {
            logger.lifecycle("$path: $ENV_KEYSTORE not set, release builds will be unsigned.")
        }
        buildTypes.getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                rootProject.file("config/proguard/proguard-rules.pro"),
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
}
