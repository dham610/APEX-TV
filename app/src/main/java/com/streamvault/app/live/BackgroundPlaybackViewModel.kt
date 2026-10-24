package com.streamvault.app.live

import androidx.lifecycle.ViewModel
import com.streamvault.feature.playback.preview.LivePreviewHandoffManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** APEX TV: exposes the channel that keeps playing while the user browses other tabs. */
@HiltViewModel
class BackgroundPlaybackViewModel @Inject constructor(
    private val manager: LivePreviewHandoffManager
) : ViewModel() {
    val session: StateFlow<LivePreviewHandoffManager.BackgroundLiveSession?> = manager.backgroundSessionFlow
    val pipVisible: StateFlow<Boolean> = manager.pipVisible

    fun setPipVisible(visible: Boolean) = manager.setPipVisible(visible)

    fun stop() = manager.stopBackground()
}
