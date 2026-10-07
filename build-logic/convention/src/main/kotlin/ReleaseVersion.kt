/**
 * Version scheme shared by both apps: `vMAJOR.MINOR.PATCH[-suffix]` tags map to
 * `versionCode = MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + build`, with `build` in 0..99:
 * - a pre-release suffix ending in a number (`-rc1`, `-beta2`) uses that number (1..98), so every pre-release is
 *   below the final release of the same version;
 * - a final tag uses 99, so `v0.2.0-rc3` < `v0.2.0` < `v0.2.1-rc1`;
 * - `-PversionBuild` overrides it (0..99), e.g. to re-upload the same version to Play.
 * Monotonic for any increasing version; stays below Play's 2_100_000_000 limit while MAJOR <= 2099.
 */
internal data class ReleaseVersion(
    val name: String,
    val code: Int,
) {
    companion object {
        const val DEFAULT_NAME = "0.1.0"
        private const val MAX_PART = 99
        private const val FINAL_BUILD = 99
        private const val MAX_MAJOR = 2099
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$""")
        private val TRAILING_NUMBER = Regex("""(\d+)$""")

        /** Parses a tag or version name (leading `v` optional); [build] overrides the derived build number. */
        fun parse(
            raw: String,
            build: Int? = null,
        ): ReleaseVersion {
            val trimmed = raw.trim()
            val match =
                requireNotNull(PATTERN.matchEntire(trimmed)) { "Invalid version '$raw', expected vMAJOR.MINOR.PATCH" }
            val (major, minor, patch) =
                match.groupValues
                    .drop(1)
                    .take(3)
                    .map { it.toLongOrNull() ?: Long.MAX_VALUE }
            val suffix = match.groupValues[4]
            val derived =
                if (suffix.isEmpty()) {
                    FINAL_BUILD
                } else {
                    TRAILING_NUMBER.find(suffix)?.value?.toIntOrNull() ?: 0
                }
            val number = build ?: derived
            require(number in 0..MAX_PART) { "build number must be 0..$MAX_PART, was $number in '$raw'" }
            require(suffix.isEmpty() || build != null || number < FINAL_BUILD) {
                "pre-release number must be below $FINAL_BUILD in '$raw'"
            }
            require(minor <= MAX_PART && patch <= MAX_PART) { "minor and patch must be 0..$MAX_PART in '$raw'" }
            require(major <= MAX_MAJOR) { "major must be at most $MAX_MAJOR in '$raw'" }
            val code = major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + number
            return ReleaseVersion(trimmed.removePrefix("v"), code)
        }
    }
}
