package org.kodein.arch.vie.timemachine.client

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


/**
 * Every number that tunes the probe, in one place.
 *
 * An application chooses two of them, through its [TimeMachineConfig]: these are then its defaults.
 * The others are the defaults of constructor parameters, which only tests override: those of the
 * connection to the inspector are gathered in [Connection].
 */
internal object ProbeTuning {

    /** [TimeMachineConfig.bufferSize] when the application does not choose. */
    const val bufferSize: Int = 1024

    /** [TimeMachineConfig.maxObservationLength] when the application does not choose: a megabyte of
     *  characters. */
    const val maxObservationLength: Int = 1024 * 1024

    /** How many characters of JSON may be in flight, sent but not acknowledged by the inspector,
     *  before the outbound queue waits. */
    const val window: Int = 256 * 1024

    /** How many injected models, intents or signals of one ViewModel may wait to be observed before
     *  the oldest is forgotten. */
    const val maxPendingInjections: Int = 32

    /** How many characters of a frame that fails to decode are printed. */
    const val frameExcerptLength: Int = 100

    /** The smallest size from which the registry of live ViewModels is pruned when one starts. */
    const val minPruneFrom: Int = 16

    /** What tunes the connection to the inspector: only tests choose anything but the defaults. */
    class Connection(
        /** How long the connection loop waits before its first retry; each failure then doubles it. */
        val firstRetryDelay: Duration = 500.milliseconds,
        /** The longest the connection loop waits between two retries, until the outage turns quiet. */
        val maxRetryDelay: Duration = 5.seconds,
        /** How long no connection must have held for the connection loop to retry less often. */
        val quietAfter: Duration = 1.minutes,
        /** The longest the connection loop waits between two retries, once the outage is quiet. */
        val quietRetryDelay: Duration = 30.seconds,
        /** How long a connection must last to hold: to be announced, and to end the outage. */
        val heldAfter: Duration = 5.seconds,
        /** How often the connection is pinged; it is closed when a pong takes twice as long. */
        val pingInterval: Duration = 5.seconds,
        /** How long the inspector has to answer the probe's hello. */
        val handshakeTimeout: Duration = 10.seconds,
    )
}
