package com.example.thorshowdown.showdown

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * What the server is asking this player to decide, from `|request|`.
 * Only the fields the app needs are modelled; the raw JSON is kept on [ShowdownEvent.Request].
 */
@Serializable
public data class BattleRequest(
    /** True when there is nothing to choose (the opponent is deciding). */
    val wait: Boolean = false,
    /** One entry per active slot; true means that slot must switch. */
    val forceSwitch: List<Boolean> = emptyList(),
    val active: List<ActivePokemon> = emptyList(),
    val side: Side? = null,
    /** Request ID to echo back with the choice. */
    val rqid: Int? = null,
)

/** Choices available for one active Pokémon. */
@Serializable
public data class ActivePokemon(
    val moves: List<MoveSlot> = emptyList(),
    val trapped: Boolean = false,
    /** Tera type if Terastallizing is allowed this turn, else null. */
    val canTerastallize: String? = null,
)

/** One move button. */
@Serializable
public data class MoveSlot(
    val move: String,
    val id: String,
    val pp: Int = 0,
    @SerialName("maxpp") val maxPp: Int = 0,
    val disabled: MoveDisabled = MoveDisabled.Enabled,
)

/** Whether a move can be used. The server sends `false`, `true`, or a reason string. */
@Serializable(with = MoveDisabledSerializer::class)
public sealed interface MoveDisabled {
    public data object Enabled : MoveDisabled

    public data class Disabled(
        val reason: String?,
    ) : MoveDisabled
}

/** This player's side and full team. Authoritative for the player's own Pokémon. */
@Serializable
public data class Side(
    val name: String,
    val id: String,
    val pokemon: List<SidePokemon> = emptyList(),
)

/** One Pokémon on this player's team. */
@Serializable
public data class SidePokemon(
    val ident: String,
    val details: String,
    /** e.g. "241/241", "120/300 brn", "0 fnt". */
    val condition: String,
    val active: Boolean = false,
    val stats: Stats? = null,
    val moves: List<String> = emptyList(),
    val item: String = "",
    val ability: String = "",
    val teraType: String? = null,
)

/** Battle stats, excluding HP. */
@Serializable
public data class Stats(
    val atk: Int,
    val def: Int,
    val spa: Int,
    val spd: Int,
    val spe: Int,
)

internal object MoveDisabledSerializer : KSerializer<MoveDisabled> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): MoveDisabled {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: throw SerializationException("Expected a JSON primitive for 'disabled'")
        val flag = primitive.booleanOrNull
        return when {
            flag == false -> MoveDisabled.Enabled
            flag == true -> MoveDisabled.Disabled(reason = null)
            else -> MoveDisabled.Disabled(reason = primitive.content)
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: MoveDisabled,
    ) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("MoveDisabled only supports JSON")
        val element =
            when (value) {
                MoveDisabled.Enabled -> JsonPrimitive(false)
                is MoveDisabled.Disabled -> value.reason?.let(::JsonPrimitive) ?: JsonPrimitive(true)
            }
        json.encodeJsonElement(element)
    }
}
