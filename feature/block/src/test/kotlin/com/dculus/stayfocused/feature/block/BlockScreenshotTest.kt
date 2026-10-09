package com.dculus.stayfocused.feature.block

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class BlockScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shot(
        name: String,
        state: BlockUiState,
    ) = shot(name) { BlockScreen(state, BlockActions()) }

    private fun shot(
        name: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { StayFocusedTheme { content() } }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun rows() =
        listOf(
            BlockRowUi(cycleBlock(), listOf("Instagram", "YouTube")),
            BlockRowUi(limitBlock(), listOf("Reddit", "X")),
            BlockRowUi(scheduleBlock(), listOf("Instagram", "YouTube", "Reddit")),
        )

    @Test
    fun empty() = shot("BlockEmpty", BlockUiState())

    @Test
    fun withBlocks() =
        shot(
            "BlockWithBlocks",
            BlockUiState(targets = listOf(TargetUi(BlockTarget.ThisPhone, null, 2)), blocks = rows()),
        )

    @Test
    fun withOneDevice() {
        val kid = device()
        shot(
            "BlockWithDevice",
            BlockUiState(
                targets =
                    listOf(
                        TargetUi(BlockTarget.ThisPhone, null, 2),
                        TargetUi(BlockTarget.Device(kid.id), kid, 1),
                    ),
                selected = BlockTarget.Device(kid.id),
                blocks = listOf(BlockRowUi(limitBlock(target = BlockTarget.Device(kid.id)), listOf("Instagram"))),
            ),
        )
    }

    @Test
    fun allApps() =
        shot(
            "BlockAllApps",
            BlockUiState(
                tab = BlockTab.ALL_APPS,
                apps =
                    listOf(
                        AppRowUi(pkg("instagram"), "Instagram", 83, 12, locked = true),
                        AppRowUi(pkg("youtube"), "YouTube", 45, 6, locked = false),
                    ),
            ),
        )
}
