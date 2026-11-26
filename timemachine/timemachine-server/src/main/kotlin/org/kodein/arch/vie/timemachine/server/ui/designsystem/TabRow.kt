package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorDimens


/**
 * The tabs of a pane, [selected] the one shown, each written [label] with its [badge] if any, a click on
 * one asking [onSelect] for it; a rule under them. A tab group to accessibility.
 */
@Composable
internal fun <T> TabRow(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    badge: (T) -> String? = { null },
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(InspectorDimens.tabRow).padding(horizontal = TabRowPadding).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tab in tabs) {
                val on = tab == selected
                Box(
                    Modifier
                        .fillMaxHeight()
                        .action(role = Role.Tab, selected = on) { onSelect(tab) }
                        .drawBehind {
                            if (on) drawRoundRect(
                                scheme.primary,
                                topLeft = Offset(IndicatorInset.toPx(), size.height - IndicatorHeight.toPx()),
                                size = Size(size.width - 2 * IndicatorInset.toPx(), IndicatorHeight.toPx()),
                                cornerRadius = CornerRadius(IndicatorHeight.toPx()),
                            )
                        }
                        .padding(horizontal = TabPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TabBadgeGap), verticalAlignment = Alignment.CenterVertically) {
                        Text(label(tab), style = MaterialTheme.typography.labelMedium, color = if (on) scheme.primary else scheme.onSurfaceVariant)
                        badge(tab)?.let { Chip(it, ChipTone.Neutral, mono = true) }
                    }
                }
            }
        }
        HorizontalRule()
    }
}

private val TabRowPadding = 6.dp
private val TabPadding = 12.dp
private val TabBadgeGap = 6.dp
private val IndicatorInset = 10.dp
private val IndicatorHeight = 3.dp
