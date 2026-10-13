package org.kodein.arch.vie.timemachine.protocol


/**
 * A [TimeMachineClientMessage] that occupies a position in the session's timeline.
 *
 * Implemented by [TimeMachineClientMessage.ViewModelStarted], [TimeMachineClientMessage.ViewModelClosed],
 * [TimeMachineClientMessage.Observed] and [TimeMachineClientMessage.ObservationsDropped] — the four
 * messages the inspector places on a timeline. [TimeMachineClientMessage.Hello],
 * [TimeMachineClientMessage.ViewModelInfo] and [TimeMachineClientMessage.InterventionResult] do not
 * implement it: they carry no timeline position of their own.
 *
 * [sequence] is the ordering key, not the display-only `at` some of these messages also carry: it is
 * assigned from a single session-wide monotonic counter, in the order the probe sends the messages,
 * so it lets the inspector sort one list. Observations the probe dropped take no sequence: an
 * [TimeMachineClientMessage.ObservationsDropped] reports them, in their place. A missing sequence is
 * a message lost after the probe handed it to the transport, which names no ViewModel.
 */
public sealed interface TimeMachineTimelineMessage : TimeMachineClientMessage {

    /** Session-wide monotonic position of this message in the timeline. */
    public val sequence: TimeMachineSequence
}
