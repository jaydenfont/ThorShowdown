package com.example.thorshowdown.qa

import androidx.test.platform.app.InstrumentationRegistry
import com.example.thorshowdown.showdown.fakes.FakeShowdownServer

/**
 * Recorded server messages in `androidTest/assets/qa/`, cut from a real battle with the players
 * renamed. Each file holds one or more WebSocket messages separated by `---` lines.
 */
enum class Fixture(
    private val file: String,
) {
    /** Room init, the turn-1 request (Gengar active, rqid 2) and the turn-1 events. */
    BattleStart("battle-start.txt"),

    /** After switching to Lucario: it faints and a forced-switch request follows (rqid 3). */
    LucarioFaints("battle-lucario-faints.txt"),

    /** Landorus-Therian sent in; the turn-2 request (rqid 4). */
    TurnTwo("battle-turn-two.txt"),

    /** The server's answer to a forfeit: qa-opponent wins. */
    Forfeited("battle-forfeited.txt"),

    /** The room closing after Leave. */
    Deinit("battle-deinit.txt"),
    ;

    /** Pushes every message in this fixture to the app, in order. */
    fun pushTo(server: FakeShowdownServer) {
        messages().forEach(server::push)
    }

    private fun messages(): List<String> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val text = assets.open("$ASSET_DIR/$file").bufferedReader().use { it.readText() }
        return text.replace(WINDOWS_NEWLINE, NEWLINE).split(MESSAGE_SEPARATOR).map { it.trim(NEWLINE_CHAR) }
    }

    companion object {
        /** The fixtures' room ID. */
        const val ROOM_ID = "battle-gen9randombattle-qa1"
        private const val ASSET_DIR = "qa"
        private const val MESSAGE_SEPARATOR = "\n---\n"
        private const val WINDOWS_NEWLINE = "\r\n"
        private const val NEWLINE = "\n"
        private const val NEWLINE_CHAR = '\n'
    }
}
