package com.dculus.stayfocused.core.ui.components

import android.os.Build
import androidx.annotation.ArrayRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import java.util.Locale

const val BATTERY_GUIDANCE_SETTINGS_TAG = "battery_guidance_settings"
const val BATTERY_GUIDANCE_GUIDE_TAG = "battery_guidance_guide"

private const val DONT_KILL_MY_APP = "https://dontkillmyapp.com"

/** Manufacturer families whose skins kill background apps in their own way. */
enum class OemFamily(
    @ArrayRes val stepsRes: Int,
    /** dontkillmyapp.com page slug, or null for the site's front page. */
    val guideSlug: String?,
) {
    XIAOMI(R.array.sf_battery_steps_xiaomi, "xiaomi"),
    OPPO(R.array.sf_battery_steps_oppo, "oppo"),
    VIVO(R.array.sf_battery_steps_vivo, "vivo"),
    SAMSUNG(R.array.sf_battery_steps_samsung, "samsung"),
    ONEPLUS(R.array.sf_battery_steps_oneplus, "oneplus"),
    HUAWEI(R.array.sf_battery_steps_huawei, "huawei"),
    DEFAULT(R.array.sf_battery_steps_default, null),
    ;

    val guideUrl: String get() = if (guideSlug == null) DONT_KILL_MY_APP else "$DONT_KILL_MY_APP/$guideSlug"

    companion object {
        /** Maps `Build.MANUFACTURER` (any case, null-safe) to a family; sub-brands share their parent's steps. */
        fun fromManufacturer(manufacturer: String?): OemFamily =
            when (manufacturer?.trim()?.lowercase(Locale.ROOT)) {
                "xiaomi", "redmi", "poco" -> XIAOMI
                "oppo", "realme" -> OPPO
                "vivo", "iqoo" -> VIVO
                "samsung" -> SAMSUNG
                "oneplus" -> ONEPLUS
                "huawei", "honor" -> HUAWEI
                else -> DEFAULT
            }
    }
}

/**
 * OEM-specific steps for keeping Stay Focused alive in the background, plus a button for the battery optimisation
 * list and a link to dontkillmyapp.com. Navigation is the caller's job: [onOpenBatterySettings] and [onOpenGuide]
 * receive the click (the guide with the URL to open). [appName] defaults to the host app's label, so the steps name
 * "Stay Focused" or "Stay Focused Kids" as installed.
 */
@Composable
fun BatteryGuidance(
    onOpenBatterySettings: () -> Unit,
    onOpenGuide: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    family: OemFamily = OemFamily.fromManufacturer(Build.MANUFACTURER),
    appName: String? = null,
) {
    val c = StayFocusedTheme.colors
    val context = LocalContext.current
    val name =
        remember(context, appName) {
            appName ?: context.applicationInfo.loadLabel(context.packageManager).toString()
        }
    val steps = stringArrayResource(family.stepsRes).map { it.format(name) }
    Panel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(StayFocusedTheme.spacing.gap16), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.sf_battery_title, name),
                style = StayFocusedTheme.type.bodyL.copy(fontWeight = FontWeight.ExtraBold),
                color = c.text,
            )
            Text(
                stringResource(R.string.sf_battery_intro, name),
                style = StayFocusedTheme.type.body,
                color = c.secondary,
            )
            steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${index + 1}",
                        style = StayFocusedTheme.type.numeric.copy(fontWeight = FontWeight.Bold),
                        color = c.accent,
                    )
                    Text(step, style = StayFocusedTheme.type.body, color = c.text)
                }
            }
            OutlineButton(
                text = stringResource(R.string.sf_battery_open_settings),
                onClick = onOpenBatterySettings,
                modifier = Modifier.fillMaxWidth().testTag(BATTERY_GUIDANCE_SETTINGS_TAG),
            )
            GhostButton(
                text = stringResource(R.string.sf_battery_open_guide),
                onClick = { onOpenGuide(family.guideUrl) },
                modifier = Modifier.fillMaxWidth().testTag(BATTERY_GUIDANCE_GUIDE_TAG),
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 560)
@Composable
internal fun BatteryGuidancePreview() {
    StayFocusedTheme {
        Column(Modifier.padding(StayFocusedTheme.spacing.screen)) {
            BatteryGuidance(
                onOpenBatterySettings = {},
                onOpenGuide = {},
                family = OemFamily.XIAOMI,
                appName = "Stay Focused",
            )
        }
    }
}
