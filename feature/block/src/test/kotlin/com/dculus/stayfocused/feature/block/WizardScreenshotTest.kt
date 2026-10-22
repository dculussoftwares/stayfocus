package com.dculus.stayfocused.feature.block

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.TimeRange
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Steps 1 and 2 of the wizard against `10-new-block-1-type.png` and `11-new-block-2-apps.png`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-hdpi")
class WizardScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val catalog =
        listOf(
            "com.instagram.android" to "Instagram",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.google.android.youtube" to "YouTube",
            "com.snapchat.android" to "Snapchat",
            "com.reddit.frontpage" to "Reddit",
            "com.twitter.android" to "X",
            "com.whatsapp" to "WhatsApp",
            "com.netflix.mediaclient" to "Netflix",
            "com.example.games" to "Games",
        ).map { (pkg, label) -> TargetApp(pkg, label, 0) }

    private fun shot(
        name: String,
        state: WizardUiState,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                StayFocusedTheme { WizardScreen(state, WizardActions()) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun typeStep() =
        shot(
            "WizardType",
            WizardUiState(
                ready = true,
                step = WIZARD_STEP_TYPE,
                aiAvailable = true,
                draft = BlockDraft(type = BlockType.CYCLE),
            ),
        )

    @Test
    fun appsStep() =
        shot(
            "WizardApps",
            WizardUiState(
                ready = true,
                step = WIZARD_STEP_APPS,
                apps = catalog,
                draft = BlockDraft(apps = linkedSetOf("com.instagram.android", "com.zhiliaoapp.musically")),
            ),
        )

    private val twoApps = linkedSetOf("com.instagram.android", "com.zhiliaoapp.musically")

    @Test
    fun rulesCycle() =
        shot(
            "WizardRulesCycle",
            WizardUiState(
                ready = true,
                step = WIZARD_STEP_RULES,
                apps = catalog,
                draft = BlockDraft(type = BlockType.CYCLE, apps = twoApps, use = 10, rest = 30, days = DaysOfWeek.ALL),
            ),
        )

    @Test
    fun rulesSchedule() =
        shot(
            "WizardRulesSchedule",
            WizardUiState(
                ready = true,
                step = WIZARD_STEP_RULES,
                apps = catalog,
                draft =
                    BlockDraft(
                        type = BlockType.SCHEDULE,
                        apps = twoApps,
                        range = TimeRange.parse("22:00–07:00"),
                        days = DaysOfWeek.ALL,
                    ),
            ),
        )

    @Test
    fun rulesLimit() =
        shot(
            "WizardRulesLimit",
            WizardUiState(
                ready = true,
                step = WIZARD_STEP_RULES,
                apps = catalog,
                draft = BlockDraft(type = BlockType.LIMIT, apps = twoApps, mins = 45),
            ),
        )

    @Test
    fun rulesNow() =
        shot(
            "WizardRulesNow",
            WizardUiState(
                ready = true,
                step = WIZARD_STEP_RULES,
                apps = catalog,
                draft = BlockDraft(type = BlockType.NOW, apps = twoApps, now = 90),
            ),
        )
}
