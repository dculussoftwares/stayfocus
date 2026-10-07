/**
 * Version scheme shared by both apps: `vMAJOR.MINOR.PATCH[-suffix]` tags map to
 * `versionCode = MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + build`, where `build` (0..99) is the
 * `-PversionBuild` Gradle property (default 0; bump it to re-upload the same version to Play).
 * Monotonic for any increasing version; stays below Play's 2_100_000_000 limit while MAJOR < 2100.
 */
internal data class ReleaseVersion(
    val name: String,
    val code: Int,
) {
    companion object {
        const val DEFAULT_NAME = "0.1.0"
        private const val MAX_PART = 99
        private const val MAX_MAJOR = 2099
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.-]+)?$""")

        /** Parses a tag or version name (leading `v` optional) and a build number. */
        fun parse(
            raw: String,
            build: Int = 0,
        ): ReleaseVersion {
            val trimmed = raw.trim()
            val match =
                requireNotNull(PATTERN.matchEntire(trimmed)) { "Invalid version '$raw', expected vMAJOR.MINOR.PATCH" }
            val (major, minor, patch) = match.destructured.toList().map { it.toLongOrNull() ?: Long.MAX_VALUE }
            require(build in 0..MAX_PART) { "versionBuild must be 0..$MAX_PART, was $build" }
            require(minor <= MAX_PART && patch <= MAX_PART) { "minor and patch must be 0..$MAX_PART in '$raw'" }
            require(major <= MAX_MAJOR) { "major must be at most $MAX_MAJOR in '$raw'" }
            val code = major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + build
            return ReleaseVersion(trimmed.removePrefix("v"), code)
        }
    }
}
