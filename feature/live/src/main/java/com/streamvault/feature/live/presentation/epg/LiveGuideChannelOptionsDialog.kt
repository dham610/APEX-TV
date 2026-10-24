package com.streamvault.feature.live.presentation.epg

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.streamvault.core.ui.design.BrowseShapes
import com.streamvault.core.ui.design.browse.FocusBorder
import com.streamvault.core.ui.design.browse.OnSurface
import com.streamvault.core.ui.design.browse.OnSurfaceDim
import com.streamvault.core.ui.design.browse.SurfaceElevated
import com.streamvault.core.ui.design.browse.SurfaceHighlight
import com.streamvault.core.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.Category

data class LiveGuideChannelOption(
    val label: String,
    val onClick: () -> Unit
)

/**
 * APEX TV: pop-up menu of actions for one channel (open, favourite, record, hide, add to category...).
 * "Add to category" opens a second list of the user's custom groups.
 */
@Composable
fun LiveGuideChannelOptionsDialog(
    channelName: String,
    options: List<LiveGuideChannelOption>,
    groups: List<Category>,
    addToCategoryLabel: String,
    onGroupSelected: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var showGroups by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(showGroups) { runCatching { firstFocus.requestFocus() } }

    val rows: List<LiveGuideChannelOption> = if (showGroups) {
        groups.map { group -> LiveGuideChannelOption(group.name) { onGroupSelected(group) } }
    } else {
        buildList {
            addAll(options)
            if (groups.isNotEmpty()) add(LiveGuideChannelOption("$addToCategoryLabel…") { showGroups = true })
        }
    }

    Dialog(onDismissRequest = { if (showGroups) showGroups = false else onDismiss() }) {
        Surface(
            modifier = Modifier.width(380.dp).focusGroup().swallowHeldOkKey(),
            colors = SurfaceDefaults.colors(containerColor = SurfaceElevated),
            shape = BrowseShapes.Panel
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    maxLines = 1
                )
                rows.forEachIndexed { index, row ->
                    TvClickableSurface(
                        onClick = row.onClick,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = SurfaceHighlight,
                            focusedContainerColor = SurfaceHighlight,
                            contentColor = OnSurface,
                            focusedContentColor = OnSurface
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        shape = ClickableSurfaceDefaults.shape(BrowseShapes.Control),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, FocusBorder),
                                shape = BrowseShapes.Control
                            )
                        )
                    ) {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = OnSurface,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
