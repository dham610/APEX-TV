package com.streamvault.feature.live.presentation.epg

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay

/**
 * APEX TV: a dialog opened by holding OK must not treat the rest of that same hold (key repeats and the
 * release) as a click on its first option. OK presses are ignored until the held key is released, or
 * [maxWaitMs] has passed, whichever comes first.
 */
fun Modifier.swallowHeldOkKey(maxWaitMs: Long = 2500L): Modifier = composed {
    var swallowing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(maxWaitMs)
        swallowing = false
    }
    onPreviewKeyEvent { event ->
        val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (swallowing && isOk) {
            if (event.type == KeyEventType.KeyUp) swallowing = false
            true
        } else {
            false
        }
    }
}
