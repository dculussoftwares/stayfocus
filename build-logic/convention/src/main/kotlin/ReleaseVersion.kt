/**
 * Version scheme shared by both apps: `vMAJOR.MINOR.PATCH` or `vMAJOR.MINOR.PATCH-rcN` tags map to
 * `versionCode = MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + build`, with `build` in 0..99:
 * - `-rcN` (N in 1..98) uses N, so every release candidate is below the final release of the same version;
 * - a final tag uses 99, so `v0.2.0-rc3` < `v0.2.0` < `v0.2.1-rc1`;
 * - `-PversionBuild` overrides it (0..99), e.g. to re-upload the same version to Play.
 * `-rcN` is the only pre-release form, so two tags never share a code. Monotonic for any increasing version; stays
 * below Play's 2_100_000_000 limit while MAJOR <= 2099.
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
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-rc(\d+))?$""")

        private const val GROUP_MAJOR = 1
        private const val GROUP_MINOR = 2
        private const val GROUP_PATCH = 3
        private const val GROUP_RC = 4

        /** A numeric group, or [Long.MAX_VALUE] when absent or too large so the range checks reject it. */
        private fun number(
            groups: MatchGroupCollection,
            index: Int,
        ): Long = groups[index]?.value?.toLongOrNull() ?: Long.MAX_VALUE

        /** Parses a tag or version name (leading `v` optional); [build] overrides the derived build number. */
        fun parse(
            raw: String,
            build: Int? = null,
        ): ReleaseVersion {
            val trimmed = raw.trim()
            val match =
                requireNotNull(PATTERN.matchEntire(trimmed)) {
                    "Invalid version '$raw', expected vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-rcN"
                }
            val groups = match.groups
            val major = number(groups, GROUP_MAJOR)
            val minor = number(groups, GROUP_MINOR)
            val patch = number(groups, GROUP_PATCH)
            val isRc = groups[GROUP_RC] != null
            val derived = if (isRc) number(groups, GROUP_RC) else FINAL_BUILD.toLong()
            require(
                !isRc || derived in 1 until FINAL_BUILD,
            ) { "release candidate number must be 1..${FINAL_BUILD - 1} in '$raw'" }
            require(build == null || build in 0..MAX_PART) { "build number must be 0..$MAX_PART, was $build" }
            require(minor <= MAX_PART && patch <= MAX_PART) { "minor and patch must be 0..$MAX_PART in '$raw'" }
            require(major <= MAX_MAJOR) { "major must be at most $MAX_MAJOR in '$raw'" }
            val number = build ?: derived.toInt()
            val code = major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + number
            return ReleaseVersion(trimmed.removePrefix("v"), code)
        }
    }
}
