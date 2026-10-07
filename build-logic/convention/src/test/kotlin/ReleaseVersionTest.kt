import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun `tag maps to name and code`() {
        val v = ReleaseVersion.parse("v1.2.3")
        assertEquals("1.2.3", v.name)
        assertEquals(1_020_300, v.code)
    }

    @Test
    fun `build number is added and prerelease suffix kept in name`() {
        val v = ReleaseVersion.parse("0.1.0-rc1", build = 7)
        assertEquals("0.1.0-rc1", v.name)
        assertEquals(10_007, v.code)
    }

    @Test
    fun `codes increase with versions`() {
        val codes = listOf("0.1.0", "0.1.1", "0.2.0", "1.0.0", "1.0.1").map { ReleaseVersion.parse(it).code }
        assertEquals(codes.sorted(), codes)
        assertEquals(codes.distinct(), codes)
    }

    @Test
    fun `invalid input is rejected`() {
        listOf("1.2", "latest", "v1.100.0", "v1.0.100", "v2100.0.0", "v99999999999.0.0").forEach {
            assertTrue(it, runCatching { ReleaseVersion.parse(it) }.isFailure)
        }
        assertTrue(runCatching { ReleaseVersion.parse("1.0.0", build = 100) }.isFailure)
    }
}
