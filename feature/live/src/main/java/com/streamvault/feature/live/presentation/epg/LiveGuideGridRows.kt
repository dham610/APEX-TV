package com.streamvault.feature.live.presentation.epg

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.streamvault.core.ui.image.ChannelLogoBadge
import com.streamvault.core.ui.interaction.TvClickableSurface
import com.streamvault.core.ui.design.browse.FocusBorder
import com.streamvault.core.ui.design.browse.OnSurface
import com.streamvault.core.ui.design.browse.OnSurfaceDim
import com.streamvault.core.ui.design.browse.Primary
import com.streamvault.core.ui.design.browse.SurfaceElevated
import com.streamvault.core.ui.design.browse.SurfaceHighlight
import com.streamvault.core.ui.design.browse.TextPrimary
import com.streamvault.core.ui.design.browse.TextSecondary
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Program
import com.streamvault.domain.playback.archivePlaybackCapability
import com.streamvault.feature.live.presentation.time.LocalLiveTimeFormat
import com.streamvault.feature.live.presentation.time.createLiveTimeFormatter
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

data class LiveGuideGridLabels(
    val noSchedule: String,
    val archiveBadge: String,
    val favoriteBadge: String
)

fun List<Program>.liveCurrentProgramAt(now: Long): Program? =
    firstOrNull { now in it.startTime until it.endTime }

@Composable
fun LiveGuideGridRow(
    channel: Channel,
    isFavorite: Boolean,
    isSelected: Boolean = false,
    programs: List<Program>,
    windowStart: Long,
    windowEnd: Long,
    channelRailWidth: Dp,
    timelineGap: Dp,
    timelineViewportWidth: Dp,
    totalTimelineWidth: Dp,
    density: GuideDensity,
    transparentOverlay: Boolean,
    rowHeight: Dp,
    markerStepMs: Long,
    scrollState: androidx.compose.foundation.ScrollState,
    labels: LiveGuideGridLabels,
    focusRequester: FocusRequester? = null,
    onChannelClick: () -> Unit,
    onChannelLongClick: ((Program?) -> Unit)? = null,
    onChannelFocused: (Program?) -> Unit,
    onProgramClick: (Program) -> Unit,
    onProgramLongClick: ((Program) -> Unit)? = null,
    onProgramFocused: (Program) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val now = currentLiveGuideNow()
    val currentProgram by remember(programs, now) {
        derivedStateOf { programs.liveCurrentProgramAt(now) }
    }
    val hasUsableArchive = channel.archivePlaybackCapability().canBuildReplayCandidate
    val totalDuration = (windowEnd - windowStart).coerceAtLeast(1L)
    val channelPaddingVertical = when (density) {
        GuideDensity.COMPACT -> 3.dp
        GuideDensity.COMFORTABLE -> 4.dp
        GuideDensity.CINEMATIC -> 5.dp
    }
    val channelLogoSize = when (density) {
        GuideDensity.COMPACT -> 22.dp
        GuideDensity.COMFORTABLE -> 24.dp
        GuideDensity.CINEMATIC -> 26.dp
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvClickableSurface(
            onClick = onChannelClick,
            onLongClick = onChannelLongClick?.let { cb ->
                {
                    val prog = currentProgram
                        ?: programs.minByOrNull { kotlin.math.abs(it.startTime - now) }
                    cb(prog)
                }
            },
            modifier = Modifier
                .width(channelRailWidth)
                .fillMaxHeight()
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                )
                .onFocusChanged {
                    if (it.isFocused && !isFocused) {
                        onChannelFocused(currentProgram)
                    }
                    isFocused = it.isFocused
                },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) Primary.copy(alpha = 0.20f) else if (transparentOverlay) SurfaceElevated.copy(alpha = 0.62f) else SurfaceElevated,
                focusedContainerColor = if (transparentOverlay) SurfaceHighlight.copy(alpha = 0.88f) else SurfaceHighlight
            ),
            shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            border = ClickableSurfaceDefaults.border(
                border = if (isSelected) Border(
                    border = BorderStroke(2.dp, Primary),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) else Border.None,
                focusedBorder = Border(
                    border = BorderStroke(2.dp, FocusBorder),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 7.dp, vertical = channelPaddingVertical)
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (channelRailWidth >= 150.dp) ChannelLogoBadge(
                    channelName = channel.name,
                    logoUrl = channel.logoUrl,
                    modifier = Modifier
                        .width(channelLogoSize)
                        .height(channelLogoSize),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = if (channel.number > 0) "${channel.number}. ${channel.name}" else channel.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) Primary else if (isFocused) TextPrimary else OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isFocused) {
                            Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 900,
                                repeatDelayMillis = 1200,
                                velocity = 24.dp
                            )
                        } else {
                            Modifier
                        }
                    )
                    Text(
                        text = currentProgram?.title ?: labels.noSchedule,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // APEX TV: small icons instead of text chips, so channel names get the room.
                // Star = favourite. Clock = the provider keeps past shows you can replay (catch-up).
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = labels.favoriteBadge,
                        tint = Primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (hasUsableArchive) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = labels.archiveBadge,
                        tint = Primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(timelineGap))

        Box(
            modifier = Modifier
                .width(timelineViewportWidth)
                .fillMaxHeight()
                .background(
                    if (transparentOverlay) SurfaceElevated.copy(alpha = 0.48f) else SurfaceElevated,
                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier
                    .width(totalTimelineWidth)
                    .horizontalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(totalTimelineWidth)
                        .fillMaxHeight()
                ) {
                    val markers = remember(windowStart, windowEnd, markerStepMs) {
                        buildList {
                            var marker = windowStart - (windowStart % markerStepMs)
                            if (marker < windowStart) marker += markerStepMs
                            while (marker <= windowEnd) {
                                add(marker)
                                marker += markerStepMs
                            }
                        }
                    }
                    markers.forEach { marker ->
                        val markerRatio = ((marker - windowStart).toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .padding(start = totalTimelineWidth * markerRatio)
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                    }
                    if (programs.isEmpty()) {
                        // APEX TV: a focusable placeholder, so Up/Down/Left/Right can pass through a row with no
                        // schedule instead of skipping it (or dropping focus) and getting stuck.
                        TvClickableSurface(
                            onClick = onChannelClick,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 2.dp)
                                .onFocusChanged { if (it.isFocused) onChannelFocused(null) },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = SurfaceHighlight.copy(alpha = 0.6f),
                            ),
                            shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, FocusBorder),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                )
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(labels.noSchedule, color = OnSurfaceDim)
                            }
                        }
                    } else {
                        programs.forEach { program ->
                            LiveGuideProgramItem(
                                program = program,
                                density = density,
                                transparentOverlay = transparentOverlay,
                                windowStart = windowStart,
                                windowEnd = windowEnd,
                                totalTimelineWidth = totalTimelineWidth,
                                scrollState = scrollState,
                                viewportWidth = timelineViewportWidth,
                                onClick = { onProgramClick(program) },
                                onLongClick = onProgramLongClick?.let { cb -> { cb(program) } },
                                onFocused = { onProgramFocused(program) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LiveGuideProgramItem(
    program: Program,
    density: GuideDensity,
    transparentOverlay: Boolean,
    windowStart: Long,
    windowEnd: Long,
    totalTimelineWidth: Dp,
    scrollState: androidx.compose.foundation.ScrollState? = null,
    viewportWidth: Dp = 0.dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val pxDensity = androidx.compose.ui.platform.LocalDensity.current
    val now = currentLiveGuideNow()
    val isCurrent = now in program.startTime until program.endTime

    val appTimeFormat = LocalLiveTimeFormat.current
    val format = remember(appTimeFormat) { appTimeFormat.createLiveTimeFormatter() }
    val zone = remember { ZoneId.systemDefault() }
    val startStr = format.format(Instant.ofEpochMilli(program.startTime).atZone(zone))
    val endStr = format.format(Instant.ofEpochMilli(program.endTime).atZone(zone))
    val totalDuration = (windowEnd - windowStart).coerceAtLeast(1L)
    val visibleStart = max(program.startTime, windowStart)
    val visibleEnd = max(visibleStart + 1, minOf(program.endTime, windowEnd))
    val startRatio = ((visibleStart - windowStart).toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    val widthRatio = ((visibleEnd - visibleStart).toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    val itemStart = totalTimelineWidth * startRatio
    val minimumItemWidth = when (density) {
        GuideDensity.COMPACT -> 40.dp
        GuideDensity.COMFORTABLE -> 48.dp
        GuideDensity.CINEMATIC -> 56.dp
    }
    // APEX TV: never wider than the real slot (minus a hairline gap), so neighbouring programmes can't overlap and blur.
    val itemWidth = (totalTimelineWidth * widthRatio - 2.dp).coerceAtLeast(10.dp)
    val isCompactCell = itemWidth < 148.dp
    val isVeryCompactCell = itemWidth < 116.dp
    val outerVerticalPadding = when (density) {
        GuideDensity.COMPACT -> 2.dp
        GuideDensity.COMFORTABLE -> 2.dp
        GuideDensity.CINEMATIC -> 3.dp
    }
    val innerHorizontalPadding = when {
        isVeryCompactCell -> 5.dp
        isCompactCell -> 6.dp
        else -> 8.dp
    }
    val innerVerticalPadding = when (density) {
        GuideDensity.COMPACT -> 2.dp
        GuideDensity.COMFORTABLE -> 3.dp
        GuideDensity.CINEMATIC -> 4.dp
    }
    val titleStyle = when {
        isVeryCompactCell -> MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            lineHeight = 11.sp
        )
        isCompactCell || density == GuideDensity.COMPACT -> MaterialTheme.typography.labelMedium.copy(
            fontSize = 11.sp,
            lineHeight = 12.sp
        )
        else -> MaterialTheme.typography.labelLarge.copy(
            fontSize = 12.sp,
            lineHeight = 14.sp
        )
    }
    val timeStyle = when {
        isCompactCell || density == GuideDensity.COMPACT -> MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            lineHeight = 10.sp
        )
        else -> MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            lineHeight = 11.sp
        )
    }

    TvClickableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .padding(start = itemStart, top = outerVerticalPadding, bottom = outerVerticalPadding)
            .width(itemWidth)
            .fillMaxHeight()
            .onFocusChanged {
                if (it.isFocused && !isFocused) {
                    onFocused()
                }
                isFocused = it.isFocused
            }
            // APEX TV: a programme that is already partly on screen must not drag the whole guide back to its
            // start time (a 12:00 show at 2:00 PM used to scroll everything to 12:00). Only the part that is
            // visible is requested, so the timeline stays on the current time like TiviMate.
            .bringIntoViewResponder(
                remember(scrollState, itemStart, viewportWidth, pxDensity) {
                    visiblePartResponder(
                        scrollPx = { scrollState?.value ?: 0 },
                        cellLeftPx = with(pxDensity) { itemStart.toPx() },
                        viewportPx = with(pxDensity) { viewportWidth.toPx() },
                    )
                }
            ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                isCurrent && transparentOverlay -> Primary.copy(alpha = 0.28f)
                isCurrent -> Primary.copy(alpha = 0.2f)
                transparentOverlay -> SurfaceElevated.copy(alpha = 0.54f)
                else -> SurfaceElevated
            },
            focusedContainerColor = if (transparentOverlay) SurfaceHighlight.copy(alpha = 0.88f) else SurfaceHighlight
        ),
        shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, FocusBorder),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = innerHorizontalPadding, vertical = innerVerticalPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = program.title,
                style = titleStyle,
                color = if (isFocused) TextPrimary else if (isCurrent) Primary else OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isVeryCompactCell) {
                Text(
                    text = "$startStr - $endStr",
                    style = timeStyle,
                    color = if (isFocused) TextSecondary else OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun visiblePartResponder(
    scrollPx: () -> Int,
    cellLeftPx: Float,
    viewportPx: Float,
) = object : androidx.compose.foundation.relocation.BringIntoViewResponder {
    override fun calculateRectForParent(localRect: androidx.compose.ui.geometry.Rect): androidx.compose.ui.geometry.Rect {
        if (viewportPx <= 0f) return localRect
        val viewLeft = scrollPx().toFloat()
        val viewRight = viewLeft + viewportPx
        val cellRight = cellLeftPx + localRect.right
        // A cell completely off screen: ask for only the edge nearest the screen, at most half a viewport, so
        // moving onto a long programme nudges the timeline instead of jumping to that programme's start.
        val maxSlice = viewportPx * 0.5f
        if (cellLeftPx >= viewRight) {
            return androidx.compose.ui.geometry.Rect(
                localRect.left, localRect.top, minOf(localRect.right, localRect.left + maxSlice), localRect.bottom,
            )
        }
        if (cellRight <= viewLeft) {
            return androidx.compose.ui.geometry.Rect(
                maxOf(localRect.left, localRect.right - maxSlice), localRect.top, localRect.right, localRect.bottom,
            )
        }
        val left = (viewLeft - cellLeftPx).coerceAtLeast(localRect.left)
        val right = (viewRight - cellLeftPx).coerceAtMost(localRect.right)
        return if (right > left) {
            androidx.compose.ui.geometry.Rect(left, localRect.top, right, localRect.bottom)
        } else localRect
    }

    override suspend fun bringChildIntoView(localRect: () -> androidx.compose.ui.geometry.Rect?) = Unit
}
