# Login

A first-time login with the username and password form, landing in the lobby.
Test: `app/src/androidTest/.../qa/scenarios/LoginScenario.kt`.

## FormShown
- Bottom: "Log in to Pokémon Showdown" title, Username and Password fields, a disabled Log in button. No error text.
- Top: the idle status line; no WebView content yet.

## LobbyShown
- Bottom: "Logged in as qa-player" and a "Find a Random Battle" button. No spinner, no error text.
- Top: the idle status line (no battle). If the WebView is visible it shows no Showdown header, chat or controls.
