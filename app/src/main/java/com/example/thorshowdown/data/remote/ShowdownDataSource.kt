package com.example.thorshowdown.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Talks to the Showdown servers: a persistent WebSocket plus HTTP for login. */
class ShowdownDataSource {

    /** Raw protocol messages pushed by the server. */
    val messages: Flow<String> = emptyFlow()

    suspend fun connect() {
        // TODO: open WebSocket
    }

    suspend fun disconnect() {
        // TODO: close WebSocket
    }

    suspend fun send(command: String) {
        // TODO: write command to WebSocket
    }
}
