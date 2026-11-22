package com.example.thorshowdown.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolParserTest {
    @Test
    fun ProtocolParser_ChallstrContainingPipes_KeepsWholeChallstr() {
        val raw = "|challstr|4|abc|def"

        val events = ProtocolParser.parse(raw)

        assertEquals(listOf(ShowdownEvent.ChallStr("4|abc|def")), events)
    }

    @Test
    fun ProtocolParser_LinesAfterRoomMarker_BelongToThatRoom() {
        val raw = ">battle-gen9randombattle-123\n|turn|8"

        val events = ProtocolParser.parse(raw)

        assertEquals(listOf(ShowdownEvent.Turn(BATTLE_ROOM, 8)), events)
    }

    @Test
    fun ProtocolParser_UnknownTypeWithTags_ReturnsUnparsedWithArgsAndTags() {
        val raw = ">$BATTLE_ROOM\n|-damage|p2a: Gengar|50/100|[from] item: Life Orb|[still]"

        val events = ProtocolParser.parse(raw)

        val expected =
            ShowdownEvent.Unparsed(
                roomId = BATTLE_ROOM,
                type = "-damage",
                args = listOf("p2a: Gengar", "50/100"),
                tags = mapOf("from" to "item: Life Orb", "still" to ""),
            )
        assertEquals(listOf(expected), events)
    }

    @Test
    fun ProtocolParser_BracketedTextThatIsNotATag_StaysAnArg() {
        val raw = ">$BATTLE_ROOM\n|tier|[Gen 9] Random Battle"

        val events = ProtocolParser.parse(raw)

        val expected = ShowdownEvent.Unparsed(BATTLE_ROOM, "tier", listOf("[Gen 9] Random Battle"), emptyMap())
        assertEquals(listOf(expected), events)
    }

    @Test
    fun ProtocolParser_CrlfAndEmptyLines_AreIgnored() {
        val raw = ">$BATTLE_ROOM\r\n\r\n|turn|3\r\n\n"

        val events = ProtocolParser.parse(raw)

        assertEquals(listOf(ShowdownEvent.Turn(BATTLE_ROOM, 3)), events)
    }

    @Test
    fun ProtocolParser_MalformedTypedLine_FallsBackToUnparsed() {
        val raw = ">$BATTLE_ROOM\n|turn|abc"

        val events = ProtocolParser.parse(raw)

        assertEquals(listOf(ShowdownEvent.Unparsed(BATTLE_ROOM, "turn", listOf("abc"), emptyMap())), events)
    }

    @Test
    fun ProtocolParser_UpdateUserWithRankAndSettings_ReturnsNamedUser() {
        val raw = "|updateuser| Alice|1|170|{\"blockChallenges\":false,\"note\":\"a|b\"}"

        val events = ProtocolParser.parse(raw)

        assertEquals(listOf(ShowdownEvent.UpdateUser(name = "Alice", named = true, avatar = "170")), events)
    }

    @Test
    fun ProtocolParser_NameTaken_ReturnsNameAndMessage() {
        val raw = "|nametaken|Alice|Someone is already using the name \"Alice\"."

        val events = ProtocolParser.parse(raw)

        assertEquals(listOf(ShowdownEvent.NameTaken("Alice", "Someone is already using the name \"Alice\".")), events)
    }

    @Test
    fun ProtocolParser_PopupContainingPipes_KeepsWholeMessage() {
        val raw = "|popup|Line one||Line two"

        val events = ProtocolParser.parse(raw)

        assertEquals(listOf(ShowdownEvent.Popup("Line one||Line two")), events)
    }

    @Test
    fun ProtocolParser_PrivateMessageWithPipes_KeepsWholeMessage() {
        val raw = "|pm| Alice|+Bob|hi|there"

        val events = ProtocolParser.parse(raw)

        assertEquals(listOf(ShowdownEvent.PrivateMessage(from = "Alice", to = "Bob", message = "hi|there")), events)
    }

    @Test
    fun ProtocolParser_PrivateMessageFromSystem_KeepsSystemSender() {
        val raw = "|pm|~| Alice|/error Unknown command"

        val events = ProtocolParser.parse(raw)

        assertEquals(listOf(ShowdownEvent.PrivateMessage(from = "~", to = "Alice", message = "/error Unknown command")), events)
    }

    @Test
    fun ProtocolParser_RoomLifecycle_ReturnsInitTitleAndDeinit() {
        val raw = ">$BATTLE_ROOM\n|init|battle\n|title|Alice vs. B|ob\n|deinit"

        val events = ProtocolParser.parse(raw)

        val expected =
            listOf(
                ShowdownEvent.RoomInit(BATTLE_ROOM, RoomType.BATTLE),
                ShowdownEvent.Title(BATTLE_ROOM, "Alice vs. B|ob"),
                ShowdownEvent.RoomDeinit(BATTLE_ROOM),
            )
        assertEquals(expected, events)
    }

    @Test
    fun ProtocolParser_BattleEnd_ReturnsWinAndTie() {
        val raw = ">$BATTLE_ROOM\n|win|Alice\n|tie"

        val events = ProtocolParser.parse(raw)

        assertEquals(listOf(ShowdownEvent.Win(BATTLE_ROOM, "Alice"), ShowdownEvent.Tie(BATTLE_ROOM)), events)
    }

    @Test
    fun ProtocolParser_ErrorAndTimer_ReturnsBattleErrorAndInactiveEvents() {
        val raw =
            ">$BATTLE_ROOM\n" +
                "|error|[Invalid choice] Can't move: Gengar's Shadow Ball is disabled\n" +
                "|inactive|Battle timer is ON: inactive players will automatically lose when time's up.\n" +
                "|inactiveoff|Battle timer is now OFF."

        val events = ProtocolParser.parse(raw)

        val expected =
            listOf(
                ShowdownEvent.BattleError(BATTLE_ROOM, "[Invalid choice] Can't move: Gengar's Shadow Ball is disabled"),
                ShowdownEvent.Inactive(BATTLE_ROOM, "Battle timer is ON: inactive players will automatically lose when time's up."),
                ShowdownEvent.InactiveOff(BATTLE_ROOM, "Battle timer is now OFF."),
            )
        assertEquals(expected, events)
    }

    @Test
    fun ProtocolParser_UpdateSearchWithGames_ReturnsSearchingAndGames() {
        val raw = "|updatesearch|{\"searching\":[\"gen9randombattle\"],\"games\":{\"$BATTLE_ROOM\":\"[Gen 9] Random Battle\"}}"

        val events = ProtocolParser.parse(raw)

        val expected =
            ShowdownEvent.UpdateSearch(
                searching = listOf("gen9randombattle"),
                games = mapOf(BATTLE_ROOM to "[Gen 9] Random Battle"),
            )
        assertEquals(listOf(expected), events)
    }

    @Test
    fun ProtocolParser_UpdateSearchWithNullGames_ReturnsEmptyGames() {
        val raw = "|updatesearch|{\"searching\":[],\"games\":null}"

        val events = ProtocolParser.parse(raw)

        assertEquals(listOf(ShowdownEvent.UpdateSearch(searching = emptyList(), games = emptyMap())), events)
    }

    private companion object {
        const val BATTLE_ROOM = "battle-gen9randombattle-123"
    }
}
