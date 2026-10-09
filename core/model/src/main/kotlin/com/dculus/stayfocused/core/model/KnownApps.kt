package com.dculus.stayfocused.core.model

/** Prototype app ids mapped to Android package names. */
object KnownApps {
    val packages: Map<String, String> =
        linkedMapOf(
            "instagram" to "com.instagram.android",
            "youtube" to "com.google.android.youtube",
            "reddit" to "com.reddit.frontpage",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "x" to "com.twitter.android",
            "spotify" to "com.spotify.music",
            "maps" to "com.google.android.apps.maps",
        )

    /** Prototype ids in the "social media" group. */
    val socialMediaIds: List<String> = listOf("instagram", "reddit", "x", "youtube")

    val socialMedia: Set<String> = socialMediaIds.map { packages.getValue(it) }.toSet()
}

/** Name of an app without a launcher label (not installed): the prototype name, else the package tail. */
fun fallbackAppLabel(pkg: String): String {
    val known =
        KnownApps.packages.entries
            .firstOrNull { it.value == pkg }
            ?.key
    val raw = known ?: pkg.substringAfterLast('.')
    return raw.replaceFirstChar { it.uppercase() }
}
