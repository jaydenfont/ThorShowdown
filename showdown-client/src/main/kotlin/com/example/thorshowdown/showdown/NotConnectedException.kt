package com.example.thorshowdown.showdown

/** Thrown when a command is sent while there is no open connection. */
public class NotConnectedException : IllegalStateException(MESSAGE) {
    private companion object {
        const val MESSAGE = "Not connected to the Showdown server"
    }
}
