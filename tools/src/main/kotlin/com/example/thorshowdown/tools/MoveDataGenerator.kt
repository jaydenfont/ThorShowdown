package com.example.thorshowdown.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One move as bundled in the app's `assets/data/moves.json`. [accuracy] is null for moves that never miss. */
@Serializable
data class GeneratedMove(
    val name: String,
    val type: String,
    val category: String,
    val basePower: Int,
    val accuracy: Int?,
    val priority: Int,
    val description: String,
)

/**
 * Builds the app's move data from the Showdown server repo (MIT): `data/moves.ts` (stats) and
 * `data/text/moves.ts` (descriptions). Both are TypeScript object literals with one block per move;
 * only each block's own `key: value,` lines are read, so nested objects, functions and older
 * generations' overrides are skipped.
 */
object MoveDataGenerator {
    private const val NAME = "name"
    private const val TYPE = "type"
    private const val CATEGORY = "category"
    private const val BASE_POWER = "basePower"
    private const val ACCURACY = "accuracy"
    private const val PRIORITY = "priority"
    private const val DESC = "desc"
    private const val SHORT_DESC = "shortDesc"
    private const val NEVER_MISSES = "true"
    private const val NO_DESCRIPTION = ""
    private const val DOUBLE_QUOTE = "\""
    private const val SINGLE_QUOTE = "'"

    private val STAT_FIELDS = setOf(NAME, TYPE, CATEGORY, BASE_POWER, ACCURACY, PRIORITY)
    private val TEXT_FIELDS = setOf(DESC, SHORT_DESC)

    /** A move's block starts at one tab: `\tid: {` (some IDs are quoted). */
    private val BLOCK_START = Regex("""^\t"?([a-z0-9]+)"?: \{$""")

    /** A field directly in the block sits at exactly two tabs. */
    private val FIELD = Regex("""^\t\t(\w+): (.+),$""")

    private val output = Json { prettyPrint = false }

    fun generate(
        movesTs: String,
        movesTextTs: String,
    ): String {
        val texts = blockFields(movesTextTs, TEXT_FIELDS)
        val moves =
            blockFields(movesTs, STAT_FIELDS).mapValues { (id, fields) ->
                moveFrom(fields, texts[id].orEmpty())
            }
        return output.encodeToString(moves)
    }

    private fun moveFrom(
        stats: Map<String, String>,
        text: Map<String, String>,
    ): GeneratedMove =
        GeneratedMove(
            name = string(stats.getValue(NAME)),
            type = string(stats.getValue(TYPE)),
            category = string(stats.getValue(CATEGORY)),
            basePower = stats.getValue(BASE_POWER).toInt(),
            accuracy = stats.getValue(ACCURACY).takeUnless { it == NEVER_MISSES }?.toInt(),
            priority = stats.getValue(PRIORITY).toInt(),
            description = (text[DESC] ?: text[SHORT_DESC])?.let(::string) ?: NO_DESCRIPTION,
        )

    /** For each block, the raw literal of each wanted field, keyed by move ID. */
    private fun blockFields(
        source: String,
        wanted: Set<String>,
    ): Map<String, Map<String, String>> {
        val blocks = linkedMapOf<String, MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        source.lineSequence().forEach { line ->
            BLOCK_START.find(line)?.let { current = blocks.getOrPut(it.groupValues[1]) { mutableMapOf() } }
            val field = FIELD.find(line) ?: return@forEach
            if (field.groupValues[1] in wanted) current?.put(field.groupValues[1], field.groupValues[2])
        }
        return blocks
    }

    /** A JS string literal: double-quoted ones decode as JSON strings; single-quoted ones have no escapes here. */
    private fun string(literal: String): String =
        if (literal.startsWith(SINGLE_QUOTE)) {
            literal.removeSurrounding(SINGLE_QUOTE)
        } else {
            try {
                Json.decodeFromString<String>(literal)
            } catch (e: SerializationException) {
                literal.removeSurrounding(DOUBLE_QUOTE)
            }
        }
}
