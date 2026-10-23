@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

/**
 * Single-line v5 input: 58 dp tall, radius 24, white, 16 sp ink text, 20 dp side padding. [placeholder] is also the
 * accessibility label. With [isPassword] the text is masked and a Show/Hide button (48 dp target) sits at the end.
 * [error] is shown under the field as a 14/600 line in the error colour and exposed as the field's error semantic.
 */
@Composable
fun V5TextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = StayFocusedTheme.colors
    var revealed by remember(isPassword) { mutableStateOf(false) }
    val masked = isPassword && !revealed
    val textStyle = StayFocusedTheme.type.body.copy(fontSize = 16.sp, color = c.ink)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(StayFocusedTheme.spacing.inputHeight)
                    .clip(StayFocusedTheme.shapes.input)
                    .background(c.surface)
                    .padding(start = 20.dp, end = if (isPassword) 4.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(c.ink),
                visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = passwordAwareKeyboardOptions(isPassword, keyboardOptions),
                keyboardActions = keyboardActions,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = placeholder
                            if (error != null) this.error(error)
                        },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = textStyle.copy(color = c.textSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (isPassword) {
                val toggleDescription =
                    stringResource(
                        if (revealed) R.string.sf_password_hide_description else R.string.sf_password_show_description,
                    )
                Box(
                    modifier =
                        Modifier
                            .defaultMinSize(
                                minWidth = StayFocusedTheme.spacing.minTouchTarget,
                                minHeight = StayFocusedTheme.spacing.minTouchTarget,
                            ).clickable(enabled = enabled, role = Role.Button) { revealed = !revealed }
                            .semantics { contentDescription = toggleDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(if (revealed) R.string.v5_password_hide else R.string.v5_password_show),
                        style = StayFocusedTheme.type.label,
                        color = c.textSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
        if (error != null) {
            Text(
                error,
                style = StayFocusedTheme.type.label,
                color = c.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/**
 * Keyboard options for the field: a password field always uses [KeyboardType.Password] (so the IME doesn't suggest or
 * learn the secret), whatever else the caller sets (IME action, capitalisation …).
 */
internal fun passwordAwareKeyboardOptions(
    isPassword: Boolean,
    options: KeyboardOptions,
): KeyboardOptions = if (isPassword) options.copy(keyboardType = KeyboardType.Password) else options

@Preview(widthDp = 390, heightDp = 420)
@Composable
internal fun V5TextFieldPreview() {
    V5PreviewSurface(gradient = false) {
        V5TextField(value = "", onValueChange = {}, placeholder = "Email")
        V5TextField(value = "sam@example.com", onValueChange = {}, placeholder = "Email")
        V5TextField(value = "", onValueChange = {}, placeholder = "Password (6+ characters)", isPassword = true)
        V5TextField(value = "secret1", onValueChange = {}, placeholder = "Password (6+ characters)", isPassword = true)
        V5TextField(
            value = "sam@",
            onValueChange = {},
            placeholder = "Email",
            error = "Enter a valid email address.",
        )
    }
}
