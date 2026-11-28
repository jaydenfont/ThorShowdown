# ForfeitAndLeave

Forfeit a battle (with confirmation), see the loss, then leave the room and land back in the lobby.
Test: `app/src/androidTest/.../qa/scenarios/ForfeitAndLeaveScenario.kt`.

## ConfirmForfeit
- Bottom: a dialog "Forfeit this battle?" with "It counts as a loss." and two buttons, "Keep playing" and "Forfeit", over the battle menu.
- Top: the battle WebView for the room (fake room; no Showdown controls visible).

## Lost
- Bottom: "You lost." and a "Leave battle" button. No move tiles, no Timer button.
- Top: as in ConfirmForfeit.

## BackInLobby
- Bottom: the lobby again: "Logged in as qa-player" and "Find a Random Battle".
- Top: the idle status line (no battle).
