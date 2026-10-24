package com.streamvault.feature.live.presentation.epg

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
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
import com.streamvault.core.ui.design.browse.Primary
import com.streamvault.core.ui.design.browse.SurfaceElevated
import com.streamvault.core.ui.design.browse.SurfaceHighlight
import com.streamvault.core.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.Category

/**
 * APEX TV: category list that slides in from the left edge of the Guide (TiviMate style).
 * Right or Back closes it; picking a category loads that category's guide.
 */
@Composable
fun LiveGuideCategorySidePanel(
    categories: List<Category>,
    selectedCategoryId: Long,
    title: String,
    parentalControlLabel: String,
    channelCount: (Int) -> String,
    isCategoryLocked: (Category) -> Boolean,
    onDismiss: () -> Unit,
    onCategorySelected: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedRequester = remember { FocusRequester() }
    val selectedIndex = categories.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)

    LaunchedEffect(Unit) {
        runCatching { selectedRequester.requestFocus() }
    }

    Surface(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionRight || event.key == Key.Back)
                ) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        colors = SurfaceDefaults.colors(containerColor = SurfaceElevated),
        shape = BrowseShapes.Panel
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = categories,
                    key = { index, category -> liveGuideCategoryKey(category, index) }
                ) { index, category ->
                    val isSelected = category.id == selectedCategoryId
                    val isLocked = isCategoryLocked(category)
                    TvClickableSurface(
                        onClick = { onCategorySelected(category) },
                        modifier = if (index == selectedIndex) {
                            Modifier.focusRequester(selectedRequester)
                        } else {
                            Modifier
                        },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) Primary.copy(alpha = 0.18f) else SurfaceHighlight,
                            focusedContainerColor = if (isSelected) Primary.copy(alpha = 0.22f) else SurfaceHighlight,
                            contentColor = if (isSelected) Primary else OnSurface,
                            focusedContentColor = if (isSelected) Primary else OnSurface
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) Primary else OnSurface,
                                    maxLines = 1
                                )
                                if (isLocked) {
                                    Text(
                                        text = parentalControlLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceDim
                                    )
                                } else if (category.count > 0) {
                                    Text(
                                        text = channelCount(category.count),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceDim
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
