package com.dculus.stayfocused.feature.block

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.components.PillButton
import com.dculus.stayfocused.core.ui.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.components.RoundIconButton
import com.dculus.stayfocused.core.ui.components.TargetTag
import com.dculus.stayfocused.core.ui.components.V5Card
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Screen
import com.dculus.stayfocused.core.ui.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.theme.v5StatusBarPadding

const val DESCRIBE_FIELD_TAG = "block_describe_field"
const val DESCRIBE_BUILD_TAG = "block_describe_build"
const val DESCRIBE_TARGET_TAG = "block_describe_target"
const val DESCRIBE_BACK_TAG = "block_describe_back"
const val DESCRIBE_ERROR_TAG = "block_describe_error"

private val ScreenPadding = 14.dp
private val FieldHeight = 150.dp
private val CardRadius = 34.dp

private val Examples =
    listOf(
        R.string.block_ai_example_1,
        R.string.block_ai_example_2,
        R.string.block_ai_example_3,
        R.string.block_ai_example_4,
    )

/** Callbacks of the describe screen. */
data class DescribeActions(
    val onBack: () -> Unit = {},
    val onFlipTarget: () -> Unit = {},
    val onText: (String) -> Unit = {},
    val onBuild: () -> Unit = {},
)

/** "Describe a block" (v5, V5_SPEC §5): back, target tag, title, hint, field card, example chips, "Build my block". */
@Composable
fun DescribeScreen(
    state: DescribeUiState,
    actions: DescribeActions,
    modifier: Modifier = Modifier,
) {
    V5Screen(darkContent = false, modifier = modifier) {
        val c = StayFocusedTheme.colors
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .v5StatusBarPadding(extra = 10.dp)
                    .v5NavigationBarPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap12),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(
                    icon = V5Icons.back,
                    contentDescription = stringResource(R.string.block_describe_back),
                    onClick = actions.onBack,
                    modifier = Modifier.testTag(DESCRIBE_BACK_TAG),
                )
                Spacer(Modifier.weight(1f))
                TargetTag(
                    name = state.targetName ?: stringResource(R.string.block_my_phone),
                    modifier = Modifier.testTag(DESCRIBE_TARGET_TAG),
                    onClick = actions.onFlipTarget,
                )
            }
            Column(
                Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.block_describe_title),
                    style = StayFocusedTheme.type.heading.copy(fontSize = 30.sp, letterSpacing = (-0.01).em),
                    color = c.ink,
                )
                Text(
                    stringResource(R.string.block_describe_hint),
                    style = StayFocusedTheme.type.body,
                    color = c.textSecondary,
                )
            }
            V5Card(Modifier.fillMaxWidth(), radius = CardRadius, padding = 18.dp) {
                DescribeField(state.text, actions.onText)
            }
            if (state.error) {
                Text(
                    stringResource(R.string.block_describe_error),
                    style = StayFocusedTheme.type.body,
                    color = c.error,
                    modifier = Modifier.padding(horizontal = 6.dp).testTag(DESCRIBE_ERROR_TAG),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Examples.forEach { res ->
                    val label = stringResource(res)
                    Text(
                        label,
                        style = StayFocusedTheme.type.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        color = c.ink,
                        modifier =
                            Modifier
                                .heightIn(min = 48.dp)
                                .clip(StayFocusedTheme.shapes.pill)
                                .background(c.surface)
                                .clickable(role = Role.Button) { actions.onText(label) }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            PillButton(
                text = stringResource(R.string.block_ai_create),
                onClick = actions.onBuild,
                variant = PillButtonVariant.Primary,
                enabled = state.canBuild,
                loading = state.busy,
                loadingLabel = stringResource(R.string.block_ai_reading),
                leadingIcon = {
                    Icon(
                        V5Icons.sparkle,
                        contentDescription = null,
                        tint = c.onGradient,
                        modifier = Modifier.height(18.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag(DESCRIBE_BUILD_TAG),
            )
        }
    }
}

@Composable
private fun DescribeField(
    text: String,
    onText: (String) -> Unit,
) {
    val c = StayFocusedTheme.colors
    val placeholder = stringResource(R.string.block_ai_placeholder)
    val style = StayFocusedTheme.type.heading.copy(fontSize = 21.sp, fontWeight = FontWeight.Light, lineHeight = 28.sp)
    BasicTextField(
        value = text,
        onValueChange = { onText(it.take(MAX_SENTENCE)) },
        textStyle = style.copy(color = c.ink),
        cursorBrush = SolidColor(c.ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(FieldHeight)
                .testTag(DESCRIBE_FIELD_TAG)
                .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            if (text.isEmpty()) {
                Text(placeholder, style = style, color = c.textSecondary)
            }
            inner()
        },
    )
}

private const val MAX_SENTENCE = 300

@Preview(widthDp = 390, heightDp = 844)
@Composable
internal fun DescribeScreenPreview() {
    DescribeScreen(DescribeUiState(text = "Instagram 10 min, then lock it for 30"), DescribeActions())
}
