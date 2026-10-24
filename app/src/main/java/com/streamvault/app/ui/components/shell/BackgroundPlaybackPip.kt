package com.streamvault.app.ui.components.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.app.live.BackgroundPlaybackViewModel
import com.streamvault.core.ui.design.BrowseShapes
import com.streamvault.core.ui.design.browse.FocusBorder
import com.streamvault.core.ui.design.browse.OnSurface
import com.streamvault.core.ui.design.browse.OnSurfaceDim
import com.streamvault.core.ui.design.browse.SurfaceElevated
import com.streamvault.core.ui.interaction.TvClickableSurface
import com.streamvault.player.PlayerRenderSurfaceType
import com.streamvault.player.PlayerSurfaceResizeMode
import com.streamvault.player.ui.PlayerRenderView

/**
 * APEX TV: small picture-in-picture window for a channel that keeps playing while other tabs are
 * open. OK hides or shows the video (sound keeps playing); long-press OK stops the channel.
 */
@Composable
fun BackgroundPlaybackPip(modifier: Modifier = Modifier) {
    val viewModel: BackgroundPlaybackViewModel = hiltViewModel()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val pipVisible by viewModel.pipVisible.collectAsStateWithLifecycle()
    // No channel sound once the app leaves the foreground (Home button, another app on top).
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(viewModel, context) {
        val owner = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is androidx.lifecycle.LifecycleOwner } as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.stop()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val playing = session ?: return

    TvClickableSurface(
        onClick = { viewModel.setPipVisible(!pipVisible) },
        onLongClick = viewModel::stop,
        modifier = modifier.width(if (pipVisible) 260.dp else 220.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceElevated,
            focusedContainerColor = SurfaceElevated
        ),
        shape = ClickableSurfaceDefaults.shape(BrowseShapes.Control),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, FocusBorder), shape = BrowseShapes.Control)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            if (pipVisible) {
                Box(modifier = Modifier.fillMaxWidth().size(width = 244.dp, height = 137.dp)) {
                    PlayerRenderView(
                        playerEngine = playing.engine,
                        resizeMode = PlayerSurfaceResizeMode.FIT,
                        surfaceType = PlayerRenderSurfaceType.SURFACE_VIEW,
                        modifier = Modifier.fillMaxWidth().size(width = 244.dp, height = 137.dp)
                    )
                }
            }
            Text(
                text = playing.channelName,
                style = MaterialTheme.typography.labelLarge,
                color = OnSurface,
                maxLines = 1
            )
            Text(
                text = if (pipVisible) "OK hides video, hold OK stops" else "Audio only. OK shows video, hold OK stops",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceDim,
                maxLines = 1
            )
        }
    }
}
