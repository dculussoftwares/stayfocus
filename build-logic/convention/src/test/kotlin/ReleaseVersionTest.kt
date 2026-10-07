import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun `final tag maps to name and code with build 99`() {
        val v = ReleaseVersion.parse("v1.2.3")
        assertEquals("1.2.3", v.name)
        assertEquals(1_020_399, v.code)
    }

    @Test
    fun `pre-release number is the build and the suffix stays in the name`() {
        val v = ReleaseVersion.parse("0.1.0-rc1")
        assertEquals("0.1.0-rc1", v.name)
        assertEquals(10_001, v.code)
    }

    @Test
    fun `explicit build overrides the derived one`() {
        assertEquals(10_007, ReleaseVersion.parse("0.1.0", build = 7).code)
    }

    @Test
    fun `release sequence has strictly increasing codes`() {
        val tags = listOf("0.1.0-rc1", "0.1.0-rc2", "0.1.0", "0.1.1-rc1", "0.1.1", "0.2.0-rc1", "0.2.0", "1.0.0")
        val codes = tags.map { ReleaseVersion.parse(it).code }
        assertEquals(codes.sorted(), codes)
        assertEquals(codes.distinct(), codes)
    }

    @Test
    fun `invalid input is rejected`() {
        listOf(
            "1.2",
            "latest",
            "v1.100.0",
            "v1.0.100",
            "v2100.0.0",
            "v99999999999.0.0",
            "v1.0.0-rc99",
            "v1.0.0-rc0",
            "v0.2.0-rc2147483648",
            "v0.2.0-beta1",
            "v0.2.0-rc",
            "v0.2.0-rc01",
            "v01.2.0",
            "v1.02.0",
            "v1.2.00",
        ).forEach {
            assertTrue(it, runCatching { ReleaseVersion.parse(it) }.isFailure)
        }
        assertTrue(runCatching { ReleaseVersion.parse("1.0.0", build = 100) }.isFailure)
    }
}
