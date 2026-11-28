# SearchAndCancel

Search for a Gen 9 Random Battle from the lobby, then cancel before a match is found.
Test: `app/src/androidTest/.../qa/scenarios/SearchAndCancelScenario.kt`.

## Searching
- Bottom: "Logged in as qa-player", a progress spinner, "Searching…" text and a Cancel button. No Find button, no error text.
- Top: the idle status line (no battle yet).

## Cancelled
- Bottom: back to "Logged in as qa-player" with the "Find a Random Battle" button. No spinner, no error text.
- Top: unchanged idle status line.
