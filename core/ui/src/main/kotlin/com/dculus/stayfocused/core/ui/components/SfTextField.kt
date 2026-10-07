@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

/**
 * Text field: mono caps [label] above a r16 box; the border turns lime on focus and red with an
 * inline [error] in `alertText`. [isPassword] adds a show/hide toggle.
 */
@Composable
fun SfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    val c = StayFocusedTheme.colors
    val shape = StayFocusedTheme.shapes.input
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val showDescription = stringResource(R.string.sf_password_show_description)
    val hideDescription = stringResource(R.string.sf_password_hide_description)
    var revealed by remember { mutableStateOf(false) }
    val borderColor =
        when {
            error != null -> c.alert
            focused -> c.accent
            else -> c.hairline14
        }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        MonoLabel(label)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clip(shape)
                    .background(c.panel)
                    .border(1.dp, borderColor, shape)
                    .padding(start = 16.dp, end = if (isPassword) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                interactionSource = interaction,
                textStyle = StayFocusedTheme.type.bodyL.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions =
                    KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
                visualTransformation =
                    if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = label
                            if (error != null) error(error)
                        },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = StayFocusedTheme.type.bodyL, color = c.tertiary)
                        }
                        inner()
                    }
                },
            )
            if (isPassword) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clickable(enabled = enabled, role = Role.Button) { revealed = !revealed }
                            .semantics {
                                contentDescription = if (revealed) hideDescription else showDescription
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    MonoLabel(
                        text = stringResource(if (revealed) R.string.sf_password_hide else R.string.sf_password_show),
                        color = c.accent,
                    )
                }
            }
        }
        if (error != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(c.alert, CircleShape))
                Text(error, style = StayFocusedTheme.type.bodyS.copy(fontSize = 13.sp), color = c.alertText)
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 420)
@Composable
internal fun SfTextFieldPreview() {
    StayFocusedTheme {
        Column(
            modifier = Modifier.background(StayFocusedTheme.colors.background).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SfTextField("you@example.com", {}, "Email")
            SfTextField("", {}, "Email", placeholder = "you@example.com")
            SfTextField("secret", {}, "Password", isPassword = true)
            SfTextField(
                "nope",
                {},
                "Email",
                error = "Enter your email first, then tap Forgot password.",
            )
        }
    }
}
