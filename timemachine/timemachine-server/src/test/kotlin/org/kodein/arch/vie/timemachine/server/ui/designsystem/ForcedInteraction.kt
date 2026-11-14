package org.kodein.arch.vie.timemachine.server.ui.designsystem

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow


/**
 * An interaction source that hands its last interaction to a collector that starts after it, as well as
 * to those already collecting: an indication shows what was emitted however late it is attached.
 */
internal class LatchedInteractionSource : MutableInteractionSource {
    override val interactions: MutableSharedFlow<Interaction> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override suspend fun emit(interaction: Interaction) = interactions.emit(interaction)
    override fun tryEmit(interaction: Interaction): Boolean = interactions.tryEmit(interaction)
}

/**
 * What a specimen in [state] reports as its interactions: the pointer over it when hovered, pressed on it
 * when pressed, nothing otherwise. Given to the specimen's `interactionSource`, it makes a still render
 * show that state.
 */
@Composable
internal fun rememberSpecimenInteraction(state: SpecimenState): MutableInteractionSource = remember(state) {
    LatchedInteractionSource().apply {
        when (state) {
            SpecimenState.Enabled, SpecimenState.Disabled -> Unit
            SpecimenState.Hovered -> tryEmit(HoverInteraction.Enter())
            SpecimenState.Pressed -> tryEmit(PressInteraction.Press(Offset.Zero))
        }
    }
}
