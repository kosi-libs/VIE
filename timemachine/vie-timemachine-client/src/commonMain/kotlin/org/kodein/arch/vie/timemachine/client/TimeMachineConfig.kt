package org.kodein.arch.vie.timemachine.client

import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection


/**
 * How [TimeMachine.start] runs the probe.
 *
 * The WebSocket path is not configurable: it is part of the protocol ([TimeMachineConnection.path]).
 *
 * Building a configuration never throws, whatever its values: a release build builds one too, for
 * a probe that is switched off. [TimeMachine.start] checks an [enabled] configuration, prints what
 * is wrong with an invalid one, and then does not start the probe.
 *
 * @property appName Names this application's session in the inspector. Several running instances
 *   may share it.
 * @property enabled Whether [TimeMachine.start] starts the probe. It has no default: every
 *   application has to say where it comes from, and it must come from a debug flag, since the probe
 *   streams every model in cleartext and lets the inspector drive every ViewModel. When `false`,
 *   [TimeMachine.start] does nothing: no socket, no allocation, and [TimeMachine.middleware] returns
 *   a middleware that does nothing.
 * @property host The inspector's host. The `localhost` default reaches an inspector on the same
 *   machine, which covers desktop apps, the Android emulator (through `adb reverse`) and the iOS
 *   simulator; a physical device on wifi needs the inspector machine's address.
 * @property port The inspector's port. Invalid outside 1..65535.
 * @property bufferSize How many messages wait for the inspector, e.g. while it is not connected, before
 *   the oldest are dropped. Dropped messages are reported to the inspector as a gap. A waiting message
 *   holds the observed object itself, not a description of it: while no inspector is connected, up to
 *   this many past models, intents and signals stay reachable. Successive models built with `copy()`
 *   share most of their content, so that usually costs little; lower it when models hold heavy objects
 *   that each model replaces, such as decoded images. Invalid unless positive.
 * @property maxObservationLength How many characters one observation may take on the wire, counting
 *   its `toString()` and its JSON together. A longer `toString()` is cut at half of it, with a marker
 *   saying how much was left out; an observation that still overruns it is sent without its JSON,
 *   and the inspector shows it as too large. Either way the observation says it was truncated. This
 *   keeps an observation under the inspector's frame limit, which closes the connection on a larger
 *   one. It does not spare the app the cost of rendering a heavy model, which is done before it is
 *   measured: keep such models out of the observed ones. Invalid unless positive.
 * @property logger Receives everything the probe has to say: the banner it starts with, the state of
 *   its connection to the inspector, and each failure it survives. Every call hands it one message,
 *   ready to print, that starts with `TimeMachine:`; only the banner spans several lines. The
 *   `println` default prints them on the standard output; pass the application's own logger to route
 *   them, or `{}` to silence the probe. It is called from whichever thread has something to say, a
 *   ViewModel's included, so it must be thread-safe and should not block. A logger that throws does
 *   not break the probe: its message is printed with `println` instead. A probe that is not [enabled]
 *   never calls it.
 */
public class TimeMachineConfig(
    public val appName: String,
    public val enabled: Boolean,
    public val host: String = "localhost",
    public val port: Int = TimeMachineConnection.port,
    public val bufferSize: Int = 1024,
    public val maxObservationLength: Int = defaultMaxObservationLength,
    public val logger: (String) -> Unit = ::println,
) {
    /** What makes this configuration invalid, i.e. what keeps a probe from starting with it, or
     *  `null` when nothing does. */
    internal val problem: String?
        get() = when {
            port !in 1..65535 -> "port must be in 1..65535, was $port"
            bufferSize <= 0 -> "bufferSize must be positive, was $bufferSize"
            maxObservationLength <= 0 -> "maxObservationLength must be positive, was $maxObservationLength"
            else -> null
        }

    /** The inspector's WebSocket, as the probe connects to it and prints it. */
    internal val inspectorAddress: String get() = "ws://$host:$port${TimeMachineConnection.path}"
}

/** [TimeMachineConfig.maxObservationLength] when the app does not choose: a megabyte of characters. */
internal const val defaultMaxObservationLength: Int = 1024 * 1024
