import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestAuditTest {
    private fun manifest(
        permissions: List<String> = emptyList(),
        application: String = "",
    ) = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
        ${permissions.joinToString("\n") { """<uses-permission android:name="$it"/>""" }}
        <application>$application</application>
        </manifest>
        """.trimIndent()

    private val kidsApplication =
        """
        <meta-data android:name="isMonitoringTool" android:value="child_monitoring"/>
        <service android:name=".monitoring.MonitoringService" android:foregroundServiceType="specialUse">
          <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="parental"/>
        </service>
        """.trimIndent()
    private val kidsPermissions = listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")

    @Test
    fun `compliant app and kids manifests pass`() {
        assertEquals(emptyList<String>(), ManifestAudit.auditManifest(manifest(), AppRole.APP))
        assertEquals(
            emptyList<String>(),
            ManifestAudit.auditManifest(manifest(kidsPermissions, kidsApplication), AppRole.KIDS),
        )
    }

    @Test
    fun `QUERY_ALL_PACKAGES fails the audit`() {
        val v = ManifestAudit.auditManifest(manifest(listOf("android.permission.QUERY_ALL_PACKAGES")), AppRole.APP)
        assertTrue(v.single().contains("QUERY_ALL_PACKAGES"))
    }

    @Test
    fun `battery optimisation exemption fails the audit`() {
        val m = manifest(kidsPermissions + "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", kidsApplication)
        assertTrue(ManifestAudit.auditManifest(m, AppRole.KIDS).single().contains("BATTERY"))
    }

    @Test
    fun `app with a foreground service or permission fails`() {
        val svc = """<service android:name=".S" android:foregroundServiceType="dataSync"/>"""
        assertEquals(1, ManifestAudit.auditManifest(manifest(application = svc), AppRole.APP).size)
        val perm = manifest(listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertEquals(1, ManifestAudit.auditManifest(perm, AppRole.APP).size)
        // The untyped permission is merged in by library SDKs and is not a foreground service by itself.
        val bare = manifest(listOf("android.permission.FOREGROUND_SERVICE"))
        assertTrue(ManifestAudit.auditManifest(bare, AppRole.APP).isEmpty())
    }

    @Test
    fun `kids without the monitoring flag or the specialUse property fails`() {
        assertTrue(ManifestAudit.auditManifest(manifest(kidsPermissions), AppRole.KIDS).size >= 2)
        val noProperty =
            """<service android:name=".monitoring.MonitoringService"
                android:foregroundServiceType="specialUse"/>"""
        val flag = """<meta-data android:name="isMonitoringTool" android:value="child_monitoring"/>"""
        val v = ManifestAudit.auditManifest(manifest(kidsPermissions, flag + noProperty), AppRole.KIDS)
        assertTrue(v.single().contains("PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
    }

    @Test
    fun `another service cannot stand in for MonitoringService`() {
        val other = kidsApplication.replace(".monitoring.MonitoringService", ".Other")
        val v = ManifestAudit.auditManifest(manifest(kidsPermissions, other), AppRole.KIDS)
        assertTrue(v.single().contains("MonitoringService"))
    }

    @Test
    fun `isMonitoringTool via resource is still rejected in app`() {
        val app = """<meta-data android:name="isMonitoringTool" android:resource="@xml/x"/>"""
        assertTrue(ManifestAudit.auditManifest(manifest(application = app), AppRole.APP).isNotEmpty())
    }

    @Test
    fun `isAccessibilityTool in a service config fails`() {
        val ns = "http://schemas.android.com/apk/res/android"
        val ok = """<accessibility-service xmlns:android="$ns" android:canRetrieveWindowContent="false"/>"""
        val bad = """<accessibility-service xmlns:android="$ns" android:isAccessibilityTool="true"/>"""
        assertTrue(ManifestAudit.auditAccessibilityConfig(ok).isEmpty())
        assertEquals(1, ManifestAudit.auditAccessibilityConfig(bad).size)
    }

    @Test
    fun `targetSdk must be 36`() {
        assertTrue(ManifestAudit.auditTargetSdk(36).isEmpty())
        assertEquals(1, ManifestAudit.auditTargetSdk(35).size)
        assertEquals(1, ManifestAudit.auditTargetSdk(null).size)
    }
}
