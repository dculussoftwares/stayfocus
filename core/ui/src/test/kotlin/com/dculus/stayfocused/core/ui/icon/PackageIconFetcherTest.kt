package com.dculus.stayfocused.core.ui.icon

import android.content.pm.ApplicationInfo
import android.graphics.drawable.ColorDrawable
import coil3.ImageLoader
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackageIconFetcherTest {
    private val context = RuntimeEnvironment.getApplication()
    private val factory = PackageIconFetcher.Factory(context)

    @Test
    fun `only handles package scheme`() {
        val loader = ImageLoader(context)
        val options = Options(context)
        assertNotNull(factory.create(packageIconUri("com.example"), options, loader))
        assertNull(factory.create(coil3.Uri("https://example.com/a.png"), options, loader))
    }

    @Test
    fun `fetches the launcher icon for an installed package`() =
        runBlocking {
            val info = ApplicationInfo().apply { packageName = "com.example" }
            shadowOf(context.packageManager).installPackage(
                android.content.pm.PackageInfo().apply {
                    packageName = "com.example"
                    applicationInfo = info
                },
            )
            shadowOf(context.packageManager).setApplicationIcon("com.example", ColorDrawable(0xFFFF0000.toInt()))

            val fetcher = factory.create(packageIconUri("com.example"), Options(context), ImageLoader(context))!!
            assertTrue(fetcher.fetch() is ImageFetchResult)
        }

    @Test
    fun `tile letter comes from the label, else the package`() {
        assertEquals("I", tileLetter("instagram", "com.instagram.android"))
        assertEquals("R", tileLetter(null, "com.reddit.frontpage".substringBeforeLast('.') + ".reddit"))
        assertEquals("?", tileLetter(" ", ""))
    }
}
