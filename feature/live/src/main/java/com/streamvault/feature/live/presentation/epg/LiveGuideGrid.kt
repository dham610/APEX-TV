package com.streamvault.feature.live.presentation.epg

import com.streamvault.core.ui.layout.isPortraitLayout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Program
import com.streamvault.feature.live.presentation.model.guideLookupKey
import kotlinx.coroutines.delay

fun liveGuideInitialFocusIndex(
    channels: List<Channel>,
    initialFocusedChannelId: Long?
): Int {
    val resolvedInitialChannelId = initialFocusedChannelId ?: channels.firstOrNull()?.id
    return resolvedInitialChannelId
        ?.let { channelId -> channels.indexOfFirst { it.id == channelId } }
        ?.takeIf { it >= 0 }
        ?: 0
}

@Composable
fun LiveGuideGrid(
    modifier: Modifier = Modifier,
    channels: List<Channel>,
    favoriteChannelIds: Set<Long>,
    programsByChannel: Map<String, List<Program>>,
    guideWindowStart: Long,
    guideWindowEnd: Long,
    density: GuideDensity,
    labels: LiveGuideGridLabels,
    transparentOverlay: Boolean = false,
    initialFocusedChannelId: Long? = null,
    selectedChannelId: Long? = null,
    focusResetKey: Any? = null,
    onChannelClick: (Channel) -> Unit,
    onChannelLongClick: ((Channel, Program?) -> Unit)? = null,
    onProgramClick: (Channel, Program) -> Unit,
    onProgramLongClick: ((Channel, Program) -> Unit)? = null,
    onChannelFocused: (Channel, Program?, Boolean) -> Unit,
    onProgramFocused: (Channel, Program, Boolean) -> Unit,
    onRequestMoreChannels: () -> Unit = {}
) {
    val portrait = isPortraitLayout()
    val channelRailWidth = if (portrait) 112.dp else 260.dp
    val timelineGap = 4.dp
    val rowHeight = when (density) {
        GuideDensity.COMPACT -> 38.dp
        GuideDensity.COMFORTABLE -> 44.dp
        GuideDensity.CINEMATIC -> 52.dp
    }
    val horizontalScrollState = rememberScrollState()
    val duplicateChannelIds = remember(channels) {
        channels.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    }
    val verticalListState = rememberLazyListState()
    val resolvedInitialChannelId = initialFocusedChannelId ?: channels.firstOrNull()?.id
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // One focus requester per channel row, so keyboard-style moves can reach rows that are not composed yet.
    val rowRequesters = remember { HashMap<Long, FocusRequester>() }
    fun requesterFor(channelId: Long): FocusRequester = rowRequesters.getOrPut(channelId) { FocusRequester() }
    val initialFocusRequester = requesterFor(resolvedInitialChannelId ?: -1L)
    val initialFocusIndex = remember(channels, resolvedInitialChannelId) {
        liveGuideInitialFocusIndex(channels, resolvedInitialChannelId)
    }

    // APEX TV: jump to the starting row once per category, not whenever more channels load or the list
    // reorders (favouriting a channel moves it), which used to snap the guide back to the top.
    LaunchedEffect(focusResetKey, channels.isNotEmpty()) {
        if (channels.isEmpty()) return@LaunchedEffect
        verticalListState.scrollToItem((initialFocusIndex - 2).coerceAtLeast(0))
        delay(140)
        initialFocusRequester.requestFocus()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        val timelineViewportWidth = (maxWidth - channelRailWidth - timelineGap).coerceAtLeast(if (portrait) 1.dp else 640.dp)
        val totalDuration = (guideWindowEnd - guideWindowStart).coerceAtLeast(1L)
        val visibleDurationMs = (if (portrait) 1 else 3) * 60 * 60 * 1000L
        val calculatedTimelineWidth = timelineViewportWidth * (totalDuration.toFloat() / visibleDurationMs.toFloat())
        val screenDensity = androidx.compose.ui.platform.LocalDensity.current
        val totalTimelineWidth = if (calculatedTimelineWidth > timelineViewportWidth) {
            calculatedTimelineWidth
        } else {
            timelineViewportWidth
        }
        val markerStepMs = LIVE_GUIDE_MARKER_STEP_MS

        Column(modifier = Modifier.fillMaxSize()) {
            // APEX TV: the timeline starts at the current half-hour block, so open it at the start (TiviMate behaviour).
            LaunchedEffect(focusResetKey, guideWindowStart) { horizontalScrollState.scrollTo(0) }
            LiveGuideTimelineHeader(
                windowStart = guideWindowStart,
                windowEnd = guideWindowEnd,
                channelRailWidth = channelRailWidth,
                timelineGap = timelineGap,
                timelineViewportWidth = timelineViewportWidth,
                totalTimelineWidth = totalTimelineWidth,
                markerStepMs = markerStepMs,
                scrollState = horizontalScrollState
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                state = verticalListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(
                    items = channels,
                    // APEX TV: omit the row index unless a channel repeats, so a moved row keeps its focus.
                    key = { index, channel ->
                        if (channel.id in duplicateChannelIds) liveEpgChannelKey(channel, index)
                        else "channel:${channel.id}"
                    },
                    contentType = { _, _ -> "epg_channel" }
                ) { index, channel ->
                    if (index >= channels.size - 15) {
                        LaunchedEffect(channels.size) { onRequestMoreChannels() }
                    }
                    val programs = channel.guideLookupKey()?.let { lookupKey ->
                        programsByChannel[lookupKey].orEmpty()
                    }.orEmpty()
                    val isFirstRow = index == 0
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.onPreviewKeyEvent { event ->
                            // APEX TV: holding Up/Down moves faster than the list can compose the next row, which
                            // made focus jump out of the guide (up to the preview) before reaching the end.
                            // When the neighbouring row is not on screen yet, scroll to it and focus it ourselves.
                            if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val target = when (event.key) {
                                androidx.compose.ui.input.key.Key.DirectionUp -> index - 1
                                androidx.compose.ui.input.key.Key.DirectionDown -> index + 1
                                else -> return@onPreviewKeyEvent false
                            }
                            if (target !in channels.indices) return@onPreviewKeyEvent false
                            if (verticalListState.layoutInfo.visibleItemsInfo.any { it.index == target }) {
                                return@onPreviewKeyEvent false
                            }
                            scope.launch {
                                verticalListState.scrollToItem(target)
                                androidx.compose.runtime.withFrameNanos { }
                                androidx.compose.runtime.withFrameNanos { }
                                runCatching { requesterFor(channels[target].id).requestFocus() }
                            }
                            true
                        }
                    ) {
                    LiveGuideGridRow(
                        channel = channel,
                        isFavorite = channel.id in favoriteChannelIds,
                        isSelected = channel.id == selectedChannelId,
                        programs = programs,
                        windowStart = guideWindowStart,
                        windowEnd = guideWindowEnd,
                        channelRailWidth = channelRailWidth,
                        timelineGap = timelineGap,
                        timelineViewportWidth = timelineViewportWidth,
                        totalTimelineWidth = totalTimelineWidth,
                        density = density,
                        transparentOverlay = transparentOverlay,
                        rowHeight = rowHeight,
                        markerStepMs = markerStepMs,
                        scrollState = horizontalScrollState,
                        labels = labels,
                        focusRequester = requesterFor(channel.id),
                        onChannelClick = { onChannelClick(channel) },
                        onChannelLongClick = onChannelLongClick?.let { cb -> { prog -> cb(channel, prog) } },
                        onChannelFocused = { onChannelFocused(channel, it, isFirstRow) },
                        onProgramClick = { program -> onProgramClick(channel, program) },
                        onProgramLongClick = onProgramLongClick?.let { cb -> { program -> cb(channel, program) } },
                        onProgramFocused = { program ->
                            // APEX TV: scroll by time, like TiviMate. A programme already partly visible never moves
                            // the timeline; one off screen is nudged in with a bit of margin.
                            val viewDurMs = (timelineViewportWidth.value / totalTimelineWidth.value * totalDuration).toLong()
                            val viewLeftMs = guideWindowStart + (horizontalScrollState.value.toFloat() /
                                with(screenDensity) { totalTimelineWidth.toPx() } * totalDuration).toLong()
                            val viewRightMs = viewLeftMs + viewDurMs
                            val targetLeftMs = when {
                                program.endTime <= viewLeftMs -> program.endTime - (viewDurMs * 0.85f).toLong()
                                program.startTime >= viewRightMs -> program.startTime - (viewDurMs * 0.15f).toLong()
                                else -> null
                            }
                            if (targetLeftMs != null) {
                                val px = (((targetLeftMs - guideWindowStart).toFloat() / totalDuration.toFloat()) *
                                    with(screenDensity) { totalTimelineWidth.toPx() }).toInt()
                                scope.launch { horizontalScrollState.animateScrollTo(px.coerceAtLeast(0)) }
                            }
                            onProgramFocused(channel, program, isFirstRow)
                        }
                    )
                    }
                }
            }
        }
    }
}
