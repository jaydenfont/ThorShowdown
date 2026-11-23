package com.example.thorshowdown.data.moves

import android.content.res.AssetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.util.logging.Logger

/**
 * The bundled move data (`assets/data/moves.json`), read once in the background when created.
 * A missing or broken file is logged and leaves the dex empty: moves still work, without details.
 */
class AssetMoveDex(
    private val assets: AssetManager,
    scope: CoroutineScope,
) : MoveDex {
    private val loaded = MutableStateFlow<Map<String, MoveInfo>>(emptyMap())
    override val moves: StateFlow<Map<String, MoveInfo>> = loaded.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) { loaded.value = load() }
    }

    private fun load(): Map<String, MoveInfo> =
        try {
            MoveData.parse(assets.open(ASSET_PATH).bufferedReader().use { it.readText() }).also {
                logger.info { "Loaded ${it.size} moves" }
            }
        } catch (e: IOException) {
            logger.warning { "Couldn't read $ASSET_PATH: ${e.message}" }
            emptyMap()
        } catch (e: SerializationException) {
            logger.warning { "Couldn't parse $ASSET_PATH: ${e.message}" }
            emptyMap()
        }

    private companion object {
        val logger: Logger = Logger.getLogger(AssetMoveDex::class.java.name)
        const val ASSET_PATH = "data/moves.json"
    }
}
