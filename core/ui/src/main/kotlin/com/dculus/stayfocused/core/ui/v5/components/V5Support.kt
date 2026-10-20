package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradients
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme

/** Preview/screenshot surface: the v5 background, or the welcome gradient for controls that sit on a gradient. */
@Composable
internal fun V5PreviewSurface(
    gradient: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    V5Theme {
        Column(
            modifier =
                Modifier
                    .background(if (gradient) V5Gradients.welcome.adaptive else SolidColor(V5Theme.colors.background))
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
