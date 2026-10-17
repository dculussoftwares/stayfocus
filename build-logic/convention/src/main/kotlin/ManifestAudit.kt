import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Which app a manifest belongs to: the rules differ between the phone app and the Kids app. */
enum class AppRole { APP, KIDS }

/**
 * Pure checks on a merged AndroidManifest.xml and the accessibility-service configs. Returns human-readable
 * violations (empty = compliant). Mirrors `docs/play/COMPLIANCE.md`; the Gradle task `verifyManifests` runs it in CI.
 */
object ManifestAudit {
    const val EXPECTED_TARGET_SDK = 36
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    val FORBIDDEN_PERMISSIONS =
        setOf(
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        )

    // Typed FGS permissions only: the bare FOREGROUND_SERVICE one is injected by Firebase/Play services libraries.
    private const val FGS_TYPED_PERMISSION_PREFIX = "android.permission.FOREGROUND_SERVICE_"
    private const val FGS_SPECIAL_USE = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
    private const val FGS_SUBTYPE_PROPERTY = "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"

    fun auditManifest(
        xml: String,
        role: AppRole,
    ): List<String> {
        val root = parse(xml)
        val violations = mutableListOf<String>()
        val permissions = root.names("uses-permission") + root.names("uses-permission-sdk-23")
        FORBIDDEN_PERMISSIONS.filter { it in permissions }.forEach { violations += "forbidden permission $it" }

        val application = root.children("application").firstOrNull()
        val services = application?.children("service").orEmpty()
        val fgsServices = services.filter { it.attr("foregroundServiceType") != null }
        val monitoringFlag =
            application
                ?.children("meta-data")
                ?.firstOrNull { it.attr("name") == "isMonitoringTool" }
                ?.attr("value")

        when (role) {
            AppRole.APP -> {
                if (fgsServices.isNotEmpty()) violations += ":app must declare no foreground service"
                permissions
                    .filter { it.startsWith(FGS_TYPED_PERMISSION_PREFIX) }
                    .forEach { violations += ":app must not request $it" }
                if (monitoringFlag != null) violations += ":app must not carry isMonitoringTool"
            }

            AppRole.KIDS -> {
                if (monitoringFlag != "child_monitoring") {
                    violations += ":kids must declare isMonitoringTool=child_monitoring"
                }
                val specialUse = fgsServices.filter { "specialUse" in it.attr("foregroundServiceType").orEmpty() }
                if (specialUse.isEmpty()) violations += ":kids needs a foregroundServiceType=specialUse service"
                specialUse
                    .filter { service ->
                        service.children("property").none {
                            it.attr("name") == FGS_SUBTYPE_PROPERTY && !it.attr("value").isNullOrBlank()
                        }
                    }.forEach { violations += "${it.attr("name")} lacks the $FGS_SUBTYPE_PROPERTY property" }
                if (FGS_SPECIAL_USE !in permissions) violations += ":kids must request $FGS_SPECIAL_USE"
            }
        }
        return violations
    }

    /** Accessibility-service XML configs must never set `isAccessibilityTool` (Play policy). */
    fun auditAccessibilityConfig(xml: String): List<String> {
        val root = parse(xml)
        return if (root.attr("isAccessibilityTool") != null) {
            listOf("accessibility-service config sets isAccessibilityTool")
        } else {
            emptyList()
        }
    }

    fun auditTargetSdk(targetSdk: Int?): List<String> =
        if (targetSdk == EXPECTED_TARGET_SDK) {
            emptyList()
        } else {
            listOf("targetSdk is $targetSdk, expected $EXPECTED_TARGET_SDK")
        }

    private fun parse(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }

    private fun Element.attr(name: String): String? =
        getAttributeNS(ANDROID_NS, name).takeIf { hasAttributeNS(ANDROID_NS, name) }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == tag }

    private fun Element.names(tag: String): Set<String> = children(tag).mapNotNull { it.attr("name") }.toSet()
}
