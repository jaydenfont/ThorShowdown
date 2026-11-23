package com.example.thorshowdown.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonInfoTest {
    @Test
    fun PokemonDetails_SpeciesLevelGender_ParsesAll() {
        val details = PokemonDetails.parse("Eelektross, L87, M")

        assertEquals(PokemonDetails("Eelektross", 87, Gender.Male), details)
    }

    @Test
    fun PokemonDetails_NoLevel_IsLevel100() {
        val details = PokemonDetails.parse("Koraidon")

        assertEquals(PokemonDetails("Koraidon", 100, gender = null), details)
    }

    @Test
    fun PokemonDetails_FemaleWithShinyAndTera_IgnoresExtraFlags() {
        val details = PokemonDetails.parse("Whimsicott, L84, F, shiny, tera:Steel")

        assertEquals(PokemonDetails("Whimsicott", 84, Gender.Female), details)
    }

    @Test
    fun PokemonDetails_HyphenatedForm_KeepsFullSpecies() {
        val details = PokemonDetails.parse("Urshifu-Rapid-Strike, L76")

        assertEquals(PokemonDetails("Urshifu-Rapid-Strike", 76, gender = null), details)
    }

    @Test
    fun PokemonCondition_HealthyHp_HasNoStatus() {
        val condition = PokemonCondition.parse("290/290")

        assertEquals(PokemonCondition(hp = 290, maxHp = 290, status = null, fainted = false), condition)
    }

    @Test
    fun PokemonCondition_HpWithStatus_ParsesStatus() {
        val condition = PokemonCondition.parse("120/300 brn")

        assertEquals(PokemonCondition(hp = 120, maxHp = 300, status = Status.Burn, fainted = false), condition)
    }

    @Test
    fun PokemonCondition_EveryStatusCode_MapsToItsStatus() {
        val codes =
            mapOf(
                "brn" to Status.Burn,
                "par" to Status.Paralysis,
                "slp" to Status.Sleep,
                "frz" to Status.Freeze,
                "psn" to Status.Poison,
                "tox" to Status.Toxic,
            )

        val parsed = codes.keys.associateWith { PokemonCondition.parse("50/100 $it").status }

        assertEquals(codes, parsed)
    }

    @Test
    fun PokemonCondition_Fainted_HasZeroHpAndNoMax() {
        val condition = PokemonCondition.parse("0 fnt")

        assertEquals(PokemonCondition(hp = 0, maxHp = null, status = null, fainted = true), condition)
    }

    @Test
    fun PokemonCondition_UnknownStatus_IsIgnored() {
        val condition = PokemonCondition.parse("50/100 xyz")

        assertEquals(PokemonCondition(hp = 50, maxHp = 100, status = null, fainted = false), condition)
    }
}
