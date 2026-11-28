# BattleTurn

One turn of a Gen 9 Random Battle against qa-opponent: choose Shadow Ball and undo it, switch to Lucario,
which faints, then the forced switch to Landorus-Therian and the start of turn 2.
Test: `app/src/androidTest/.../qa/scenarios/BattleTurnScenario.kt`.

## MovesShown
- Bottom: header "qa-player vs. qa-opponent" with "Turn 1" and a Timer button; a "Terastallize (Ghost)" chip; four enabled move tiles (Shadow Ball 24/24 PP, Sludge Wave 16/16, Focus Blast 8/8, Nasty Plot 32/32) in a 2×2 grid; Party and Forfeit buttons below. Nothing clipped or overlapping.
- Top: the battle WebView for the room. The room is fake, so Showdown may show that it doesn't exist; it must not show Showdown's own move or switch buttons, header, chat or ads.

## ChoiceSent
- Bottom: a spinner with "Waiting for your opponent…" and a "Cancel choice" button above the move tiles.
- Top: as in MovesShown.

## PartyOpen
- Bottom: the party list with six Pokémon in request order (Gengar "In battle" first, then Lucario, Landorus-Therian, Ting-Lu, Rotom-Wash, Iron Valiant), each with level and HP. Lucario's card is expanded with item, ability, stats, moves and a "Switch in" button. A Back button at the bottom.
- Top: as in MovesShown.

## ForcedSwitch
- Bottom: "Choose a Pokémon to send in" above the party list. Lucario is first and marked "Fainted" with 0 HP; no move tiles and no Forfeit button.
- Top: as in MovesShown.

## TurnTwo
- Bottom: "Turn 2" in the header, a "Terastallize (Ground)" chip and Landorus-Therian's four moves (Earthquake, U-turn, Stealth Rock, Stone Edge), all enabled.
- Top: as in MovesShown.
