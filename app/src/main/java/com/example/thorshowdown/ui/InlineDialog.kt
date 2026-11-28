package com.example.thorshowdown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val DIALOG_PADDING = 24.dp
private val DIALOG_SPACING = 16.dp
private val DIALOG_MAX_WIDTH = 560.dp
private val DIALOG_ELEVATION = 6.dp
private const val SCRIM_ALPHA = 0.32f

/**
 * A dialog drawn inside the screen's own layout, over [BoxScope]'s content, instead of in a separate
 * window. The bottom screen is a `Presentation`, and a dialog window can't be opened from a
 * presentation's context (and one opened from the Activity's lands on the top display), so the
 * bottom screen uses this. Tapping outside the card calls [onDismissRequest].
 */
@Composable
fun BoxScope.InlineDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest),
    )
    Surface(
        modifier = Modifier.align(Alignment.Center).padding(DIALOG_PADDING).widthIn(max = DIALOG_MAX_WIDTH),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = DIALOG_ELEVATION,
    ) {
        Column(modifier = Modifier.padding(DIALOG_PADDING), verticalArrangement = Arrangement.spacedBy(DIALOG_SPACING)) {
            ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
            text()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/** A full-size box for screens that show [InlineDialog]s over their content. */
@Composable
fun DialogHost(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), content = content)
}
