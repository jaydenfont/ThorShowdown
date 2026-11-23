package com.example.thorshowdown.showdown

/** Species, level and gender from a Pokémon's `details` string, e.g. `Eelektross, L87, M`. */
public data class PokemonDetails(
    val species: String,
    val level: Int,
    val gender: Gender?,
) {
    public companion object {
        private const val FIELD_SEPARATOR = ","
        private const val LEVEL_PREFIX = "L"
        private const val DEFAULT_LEVEL = 100

        /** Unknown extra fields (`shiny`, `tera:…`) are ignored. */
        public fun parse(details: String): PokemonDetails {
            val fields = details.split(FIELD_SEPARATOR).map(String::trim)
            val extras = fields.drop(1)
            return PokemonDetails(
                species = fields.first(),
                level = extras.firstNotNullOfOrNull(::level) ?: DEFAULT_LEVEL,
                gender = extras.firstNotNullOfOrNull { field -> Gender.entries.firstOrNull { it.code == field } },
            )
        }

        private fun level(field: String): Int? =
            if (field.startsWith(LEVEL_PREFIX)) field.removePrefix(LEVEL_PREFIX).toIntOrNull() else null
    }
}

public enum class Gender(
    internal val code: String,
) {
    Male("M"),
    Female("F"),
}

/** HP and status from a Pokémon's `condition` string, e.g. `120/300 brn` or `0 fnt`. */
public data class PokemonCondition(
    val hp: Int,
    /** Null when the server only sends the current HP (fainted). */
    val maxHp: Int?,
    val status: Status?,
    val fainted: Boolean,
) {
    public companion object {
        private const val PART_SEPARATOR = " "
        private const val HP_SEPARATOR = "/"
        private const val FAINTED = "fnt"

        /** Unknown status codes are ignored. */
        public fun parse(condition: String): PokemonCondition {
            val parts = condition.trim().split(PART_SEPARATOR)
            val hp = parts.first().split(HP_SEPARATOR)
            val flag = parts.getOrNull(1)
            return PokemonCondition(
                hp = hp.first().toIntOrNull() ?: 0,
                maxHp = hp.getOrNull(1)?.toIntOrNull(),
                status = Status.entries.firstOrNull { it.code == flag },
                fainted = flag == FAINTED,
            )
        }
    }
}

/** Non-volatile status conditions, with Showdown's codes. */
public enum class Status(
    internal val code: String,
) {
    Burn("brn"),
    Paralysis("par"),
    Sleep("slp"),
    Freeze("frz"),
    Poison("psn"),
    Toxic("tox"),
}
