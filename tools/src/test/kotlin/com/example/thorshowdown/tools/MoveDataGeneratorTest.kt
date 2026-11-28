package com.example.thorshowdown.tools

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MoveDataGeneratorTest {
    @Test
    fun MoveDataGenerator_MoveWithText_CombinesStatsAndDescription() {
        val generated = generate()

        assertEquals(
            GeneratedMove(
                name = "Thunderbolt",
                type = "Electric",
                category = "Special",
                basePower = 90,
                accuracy = 100,
                priority = 0,
                description = "Has a 10% chance to paralyze the target.",
            ),
            generated.getValue("thunderbolt"),
        )
    }

    @Test
    fun MoveDataGenerator_NeverMisses_HasNoAccuracy() {
        val generated = generate()

        assertEquals(null, generated.getValue("aerialace").accuracy)
    }

    @Test
    fun MoveDataGenerator_PriorityMove_KeepsPriority() {
        val generated = generate()

        assertEquals(1, generated.getValue("accelerock").priority)
    }

    @Test
    fun MoveDataGenerator_QuotedMoveId_IsUnquoted() {
        val generated = generate()

        assertEquals("10,000,000 Volt Thunderbolt", generated.getValue("10000000voltthunderbolt").name)
    }

    @Test
    fun MoveDataGenerator_NestedObjectFields_AreIgnored() {
        val generated = generate()

        assertEquals("Electric", generated.getValue("thunderbolt").type)
    }

    @Test
    fun MoveDataGenerator_DescriptionWithEscapedQuote_IsUnescaped() {
        val generated = generate()

        assertEquals("Hits the \"target\" and ignores Protect.", generated.getValue("aerialace").description)
    }

    @Test
    fun MoveDataGenerator_OnlyShortDescription_UsesIt() {
        val generated = generate()

        assertEquals("Usually goes first.", generated.getValue("accelerock").description)
    }

    @Test
    fun MoveDataGenerator_OlderGenerationText_IsIgnored() {
        val generated = generate()

        assertFalse(generated.getValue("thunderbolt").description.contains(OLD_GEN_TEXT))
    }

    @Test
    fun MoveDataGenerator_MoveWithoutText_HasEmptyDescription() {
        val generated = generate()

        assertEquals("", generated.getValue("struggle").description)
    }

    private fun generate(): Map<String, GeneratedMove> = Json.decodeFromString(MoveDataGenerator.generate(MOVES_TS, MOVES_TEXT_TS))

    private companion object {
        const val OLD_GEN_TEXT = "Gen 1 text"

        val MOVES_TS =
            """
            export const Moves: import('../sim/dex-moves').MoveDataTable = {
            	"10000000voltthunderbolt": {
            		num: 719,
            		accuracy: true,
            		basePower: 195,
            		category: "Special",
            		name: "10,000,000 Volt Thunderbolt",
            		pp: 1,
            		priority: 0,
            		flags: {},
            		type: "Electric",
            	},
            	thunderbolt: {
            		num: 85,
            		accuracy: 100,
            		basePower: 90,
            		category: "Special",
            		name: "Thunderbolt",
            		pp: 15,
            		priority: 0,
            		secondary: {
            			chance: 10,
            			type: 'ignored',
            		},
            		target: "normal",
            		type: "Electric",
            	},
            	aerialace: {
            		accuracy: true,
            		basePower: 60,
            		category: "Physical",
            		name: "Aerial Ace",
            		priority: 0,
            		type: "Flying",
            	},
            	accelerock: {
            		accuracy: 100,
            		basePower: 40,
            		category: "Physical",
            		name: "Accelerock",
            		priority: 1,
            		type: "Rock",
            	},
            	struggle: {
            		accuracy: true,
            		basePower: 50,
            		category: "Physical",
            		name: "Struggle",
            		priority: 0,
            		type: "???",
            	},
            };
            """.trimIndent()

        val MOVES_TEXT_TS =
            """
            export const MovesText: {[id: IDEntry]: MoveText} = {
            	thunderbolt: {
            		name: "Thunderbolt",
            		desc: "Has a 10% chance to paralyze the target.",
            		shortDesc: "10% chance to paralyze the target.",
            		gen1: {
            			desc: "$OLD_GEN_TEXT",
            		},
            	},
            	aerialace: {
            		name: "Aerial Ace",
            		desc: "Hits the \"target\" and ignores Protect.",
            		shortDesc: "This move does not check accuracy.",
            	},
            	accelerock: {
            		name: "Accelerock",
            		shortDesc: "Usually goes first.",
            	},
            };
            """.trimIndent()
    }
}
