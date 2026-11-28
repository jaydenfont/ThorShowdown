package com.example.thorshowdown.ui.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownUrlsTest {
    @Test
    fun ShowdownUrls_RoomUrlForBattle_IsWebClientRoomPage() {
        val url = ShowdownUrls.roomUrl(BATTLE_ROOM)

        assertEquals("https://play.pokemonshowdown.com/battle-gen9randombattle-1", url)
    }

    @Test
    fun ShowdownUrls_RoomUrlWithoutRoom_IsBlankPage() {
        val url = ShowdownUrls.roomUrl(null)

        assertEquals("about:blank", url)
    }

    @Test
    fun ShowdownUrls_WebClientPage_IsAllowed() {
        assertTrue(ShowdownUrls.isAllowed("https://play.pokemonshowdown.com/battle-gen9randombattle-1"))
    }

    @Test
    fun ShowdownUrls_PlainHttp_IsBlocked() {
        assertFalse(ShowdownUrls.isAllowed("http://play.pokemonshowdown.com/"))
    }

    @Test
    fun ShowdownUrls_OtherSite_IsBlocked() {
        assertFalse(ShowdownUrls.isAllowed("https://example.com/"))
    }

    @Test
    fun ShowdownUrls_LookAlikeHost_IsBlocked() {
        assertFalse(ShowdownUrls.isAllowed("https://play.pokemonshowdown.com.example.com/"))
    }

    @Test
    fun ShowdownUrls_OtherShowdownSubdomain_IsBlocked() {
        assertFalse(ShowdownUrls.isAllowed("https://replay.pokemonshowdown.com/"))
    }

    @Test
    fun ShowdownUrls_Malformed_IsBlocked() {
        assertFalse(ShowdownUrls.isAllowed("https://play.pokemonshowdown.com/ bad url"))
    }

    private companion object {
        const val BATTLE_ROOM = "battle-gen9randombattle-1"
    }
}
