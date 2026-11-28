package com.example.thorshowdown.data.moves

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveDataTest {
    @Test
    fun MoveData_BundledFormat_ParsesEveryMoveById() {
        val moves = MoveData.parse(BUNDLED)

        assertEquals(
            mapOf(
                "thunderbolt" to MoveInfo("Thunderbolt", "Electric", "Special", 90, 100, 0, "Has a 10% chance to paralyze the target."),
                "aerialace" to
                    MoveInfo("Aerial Ace", "Flying", "Physical", 60, accuracy = null, priority = 0, description = "Never misses."),
            ),
            moves,
        )
    }

    @Test
    fun MoveData_Empty_HasNoMoves() {
        assertEquals(emptyMap<String, MoveInfo>(), MoveData.parse(EMPTY))
    }

    private companion object {
        const val EMPTY = "{}"
        val BUNDLED =
            """
            {"thunderbolt":{"name":"Thunderbolt","type":"Electric","category":"Special","basePower":90,"accuracy":100,"priority":0,"description":"Has a 10% chance to paralyze the target."},
             "aerialace":{"name":"Aerial Ace","type":"Flying","category":"Physical","basePower":60,"accuracy":null,"priority":0,"description":"Never misses."}}
            """.trimIndent()
    }
}
