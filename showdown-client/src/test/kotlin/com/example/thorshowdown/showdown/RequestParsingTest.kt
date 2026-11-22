package com.example.thorshowdown.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestParsingTest {
    @Test
    fun ProtocolParser_RequestWithDisabledReasonAndForceSwitch_ReturnsTypedRequestAndRawJson() {
        val json =
            """{"active":[{"moves":[""" +
                """{"move":"Shadow Ball","id":"shadowball","pp":0,"maxpp":24,"target":"normal","disabled":true},""" +
                """{"move":"Nasty Plot","id":"nastyplot","pp":32,"maxpp":32,"target":"self","disabled":"Taunt"},""" +
                """{"move":"Focus Blast","id":"focusblast","pp":8,"maxpp":8,"target":"normal","disabled":false}""" +
                """],"trapped":true,"canTerastallize":"Ghost"}],""" +
                """"forceSwitch":[true],""" +
                """"side":{"name":"Alice","id":"p1","pokemon":[""" +
                """{"ident":"p1: Gengar","details":"Gengar, L82, M","condition":"120/241 brn","active":true,""" +
                """"stats":{"atk":106,"def":131,"spa":359,"spd":186,"spe":189},""" +
                """"moves":["shadowball","nastyplot","focusblast"],"item":"choicespecs","ability":"cursedbody",""" +
                """"teraType":"Ghost","pokeball":"pokeball","unknownFutureKey":1}""" +
                """]},"rqid":7}"""
        val raw = ">$BATTLE_ROOM\n|request|$json"

        val events = ProtocolParser.parse(raw)

        val expectedRequest =
            BattleRequest(
                active =
                    listOf(
                        ActivePokemon(
                            moves =
                                listOf(
                                    MoveSlot(
                                        "Shadow Ball",
                                        "shadowball",
                                        pp = 0,
                                        maxPp = 24,
                                        disabled = MoveDisabled.Disabled(reason = null),
                                    ),
                                    MoveSlot(
                                        "Nasty Plot",
                                        "nastyplot",
                                        pp = 32,
                                        maxPp = 32,
                                        disabled = MoveDisabled.Disabled(reason = "Taunt"),
                                    ),
                                    MoveSlot("Focus Blast", "focusblast", pp = 8, maxPp = 8, disabled = MoveDisabled.Enabled),
                                ),
                            trapped = true,
                            canTerastallize = "Ghost",
                        ),
                    ),
                forceSwitch = listOf(true),
                side =
                    Side(
                        name = "Alice",
                        id = "p1",
                        pokemon =
                            listOf(
                                SidePokemon(
                                    ident = "p1: Gengar",
                                    details = "Gengar, L82, M",
                                    condition = "120/241 brn",
                                    active = true,
                                    stats = Stats(atk = 106, def = 131, spa = 359, spd = 186, spe = 189),
                                    moves = listOf("shadowball", "nastyplot", "focusblast"),
                                    item = "choicespecs",
                                    ability = "cursedbody",
                                    teraType = "Ghost",
                                ),
                            ),
                    ),
                rqid = 7,
            )
        assertEquals(listOf(ShowdownEvent.Request(BATTLE_ROOM, expectedRequest, json)), events)
    }

    @Test
    fun ProtocolParser_WaitRequest_ReturnsWaitWithDefaults() {
        val json = """{"wait":true,"side":{"name":"Alice","id":"p1","pokemon":[]},"rqid":8}"""
        val raw = ">$BATTLE_ROOM\n|request|$json"

        val events = ProtocolParser.parse(raw)

        val expectedRequest = BattleRequest(wait = true, side = Side("Alice", "p1", emptyList()), rqid = 8)
        assertEquals(listOf(ShowdownEvent.Request(BATTLE_ROOM, expectedRequest, json)), events)
    }

    private companion object {
        const val BATTLE_ROOM = "battle-gen9randombattle-123"
    }
}
