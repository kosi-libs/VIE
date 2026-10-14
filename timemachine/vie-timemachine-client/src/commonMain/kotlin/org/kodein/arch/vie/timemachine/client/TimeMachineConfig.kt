package org.kodein.arch.vie.timemachine.client

import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection


/**
 * How [TimeMachine.start] runs the probe.
 *
 * The WebSocket path is not configurable: it is part of the protocol ([TimeMachineConnection.path]).
 *
 * @property appName Names this application's session in the inspector. Several running instances
 *   may share it.
 * @property host The inspector's host. The `localhost` default reaches an inspector on the same
 *   machine, which covers desktop apps, the Android emulator (through `adb reverse`) and the iOS
 *   simulator; a physical device on wifi needs the inspector machine's address.
 * @property port The inspector's port. Must be in 1..65535.
 * @property enabled When `false`, [TimeMachine.start] does nothing: no socket, no allocation, and
 *   [TimeMachine.middleware] returns a middleware that does nothing. Wire it to a debug flag.
 * @property bufferSize How many messages wait for the inspector, e.g. while it is not connected, before
 *   the oldest are dropped. Dropped messages are reported to the inspector as a gap. A waiting message
 *   holds the observed object itself, not a description of it: while no inspector is connected, up to
 *   this many past models, intents and signals stay reachable. Successive models built with `copy()`
 *   share most of their content, so that usually costs little; lower it when models hold heavy objects
 *   that each model replaces, such as decoded images.
 */
public class TimeMachineConfig(
    public val appName: String,
    public val host: String = "localhost",
    public val port: Int = TimeMachineConnection.port,
    public val enabled: Boolean = true,
    public val bufferSize: Int = 1024,
) {
    init {
        require(port in 1..65535) { "port must be in 1..65535, was $port" }
        require(bufferSize > 0) { "bufferSize must be positive, was $bufferSize" }
    }
}
