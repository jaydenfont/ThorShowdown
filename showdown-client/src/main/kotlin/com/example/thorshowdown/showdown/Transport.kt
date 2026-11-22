package com.example.thorshowdown.showdown

import kotlinx.coroutines.flow.Flow

/** A text-frame socket. The only way the client touches the WebSocket. */
public interface Transport {
    /** Messages and closure received since [open]. Buffered, so nothing is lost before collection. */
    public val incoming: Flow<TransportEvent>

    /** Opens the socket to [url]. Throws [java.io.IOException] if it can't be opened. */
    public suspend fun open(url: String)

    /** Sends one text frame. */
    public suspend fun send(text: String)

    /** Closes the socket. [incoming] then delivers [TransportEvent.Closed]. */
    public suspend fun close()
}

/** Something that happened on a [Transport]. */
public sealed interface TransportEvent {
    public data class Message(
        val text: String,
    ) : TransportEvent

    public data class Closed(
        val reason: String?,
    ) : TransportEvent
}
