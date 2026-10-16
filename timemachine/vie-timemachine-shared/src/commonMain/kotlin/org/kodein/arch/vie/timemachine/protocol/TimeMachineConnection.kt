package org.kodein.arch.vie.timemachine.protocol


/**
 * The coordinates a TimeMachine client and server agree on to establish a connection, plus the
 * protocol version they must share.
 */
public object TimeMachineConnection {

    /** Default port the TimeMachine server listens on. */
    public const val port: Int = 13229

    /** WebSocket path the TimeMachine server exposes. Not configurable by consumers. */
    public const val path: String = "/timemachine"

    /**
     * Version of the wire protocol. Bumped by hand on breaking wire changes — this is not the
     * artifact version, and nothing in the build generates it.
     */
    public val protocolVersion: TimeMachineProtocolVersion = TimeMachineProtocolVersion(1)
}
