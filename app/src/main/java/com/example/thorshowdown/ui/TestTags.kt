package com.example.thorshowdown.ui

/**
 * Semantics tags that workflow tests find elements by. Kept in one place so a redesign
 * only has to keep these on the right elements (and the tests' robots in step).
 */
object TestTags {
    const val LOGIN_USERNAME = "login.username"
    const val LOGIN_PASSWORD = "login.password"
    const val LOGIN_SUBMIT = "login.submit"
    const val LOBBY_USERNAME = "lobby.username"
    const val LOBBY_FIND_BATTLE = "lobby.findBattle"
    const val LOBBY_SEARCHING = "lobby.searching"
    const val LOBBY_CANCEL_SEARCH = "lobby.cancelSearch"
    const val BATTLE_WAITING = "battle.waiting"
    const val BATTLE_UNDO = "battle.undo"
    const val BATTLE_PARTY = "battle.party"
    const val BATTLE_BACK = "battle.back"
    const val BATTLE_FORFEIT = "battle.forfeit"
    const val BATTLE_FORFEIT_CONFIRM = "battle.forfeitConfirm"
    const val BATTLE_MUST_SWITCH = "battle.mustSwitch"
    const val BATTLE_RESULT = "battle.result"
    const val BATTLE_LEAVE = "battle.leave"

    private const val MOVE_PREFIX = "battle.move."
    private const val PARTY_MEMBER_PREFIX = "party.member."
    private const val SWITCH_IN_PREFIX = "party.switchIn."

    /** The move tile for request slot [slot] (1-based). */
    fun move(slot: Int): String = "$MOVE_PREFIX$slot"

    /** The party card for party slot [slot] (1-based, request order). */
    fun partyMember(slot: Int): String = "$PARTY_MEMBER_PREFIX$slot"

    /** The Switch in button on party slot [slot]'s expanded card. */
    fun switchIn(slot: Int): String = "$SWITCH_IN_PREFIX$slot"
}
