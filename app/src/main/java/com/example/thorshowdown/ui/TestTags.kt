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
}
