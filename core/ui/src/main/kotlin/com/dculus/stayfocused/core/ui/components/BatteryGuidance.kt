package com.dculus.stayfocused.core.ui.components

import android.os.Build
import androidx.annotation.ArrayRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.PillButtonSize
import com.dculus.stayfocused.core.ui.v5.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.v5.components.V5BottomSheet
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
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
 * list and a link to dontkillmyapp.com, built from v5 tokens (no container: host it in [BatteryGuidanceSheet]).
 * Navigation is the caller's job: [onOpenBatterySettings] and [onOpenGuide] receive the click (the guide with the
 * URL to open). [appName] defaults to the host app's label, so the steps name "Stay Focused" or
 * "Stay Focused Kids" as installed.
 */
@Composable
fun BatteryGuidance(
    onOpenBatterySettings: () -> Unit,
    onOpenGuide: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    family: OemFamily = OemFamily.fromManufacturer(Build.MANUFACTURER),
    appName: String? = null,
) {
    val colors = V5Theme.colors
    val type = V5Theme.type
    val context = LocalContext.current
    val name =
        remember(context, appName) {
            appName ?: context.applicationInfo.loadLabel(context.packageManager).toString()
        }
    val steps = stringArrayResource(family.stepsRes).map { it.format(name) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sf_battery_title, name), style = type.heading, color = colors.ink)
        Text(stringResource(R.string.sf_battery_intro, name), style = type.body, color = colors.textSecondary)
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${index + 1}", style = type.label.copy(fontWeight = FontWeight.Bold), color = colors.orange)
                Text(step, style = type.body, color = colors.ink)
            }
        }
        PillButton(
            text = stringResource(R.string.sf_battery_open_settings),
            onClick = onOpenBatterySettings,
            variant = PillButtonVariant.Primary,
            size = PillButtonSize.Sheet,
            modifier = Modifier.testTag(BATTERY_GUIDANCE_SETTINGS_TAG),
        )
        PillButton(
            text = stringResource(R.string.sf_battery_open_guide),
            onClick = { onOpenGuide(family.guideUrl) },
            variant = PillButtonVariant.Text,
            size = PillButtonSize.Medium,
            modifier = Modifier.testTag(BATTERY_GUIDANCE_GUIDE_TAG),
        )
    }
}

/** [BatteryGuidance] in a v5 bottom sheet; scrolls on short screens and at large font scales. */
@Composable
fun BatteryGuidanceSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenGuide: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    family: OemFamily = OemFamily.fromManufacturer(Build.MANUFACTURER),
    appName: String? = null,
) {
    V5Theme {
        V5BottomSheet(visible = visible, onDismiss = onDismiss, modifier = modifier) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                BatteryGuidance(onOpenBatterySettings, onOpenGuide, family = family, appName = appName)
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 560)
@Composable
internal fun BatteryGuidancePreview() {
    V5Theme {
        Column(Modifier.padding(18.dp)) {
            BatteryGuidance(
                onOpenBatterySettings = {},
                onOpenGuide = {},
                family = OemFamily.XIAOMI,
                appName = "Stay Focused",
            )
        }
    }
}
