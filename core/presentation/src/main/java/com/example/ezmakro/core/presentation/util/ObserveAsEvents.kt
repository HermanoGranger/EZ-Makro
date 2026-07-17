package com.example.ezmakro.core.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Observes a one-shot [events] [Flow] (e.g. ViewModel UI events) and invokes
 * [onEvent] while the composable is at least in the [minActiveState] of the
 * current [androidx.lifecycle.LifecycleOwner]. Uses [rememberUpdatedState] so
 * the latest callback is always used without restarting the collection.
 */
@Composable
fun <T> ObserveAsEvents(
    events: Flow<T>,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEvent: (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val onEventUpdated by rememberUpdatedState(onEvent)

    LaunchedEffect(events, lifecycleOwner) {
        events
            .flowWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = minActiveState,
            ).collect { onEventUpdated(it) }
    }
}
