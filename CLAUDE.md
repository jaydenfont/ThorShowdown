# ThorShowdown

Pokémon Showdown client for the AYN Thor (dual-screen Android handheld).
Kotlin + Jetpack Compose, single module `app/`.

## Context docs (read first)
- `context/README.md`: index of the context folder and its conventions.
- `context/design/architecture.md`: the design. Source of truth for layers and responsibilities.
- `context/planning/workplan.md`: P0 build order, dependencies, links to GitHub issues.
- `context/research/showdown_api_notes.md`: Showdown protocol (WebSocket framing, login, battle messages).
- `context/design/battle_sequence.txt`: battle flow sequence diagram (sequencediagram.org format).

## Workflow for every task
1. **Read `context/design/architecture.md`** before any design or implementation work.
2. **/grilling**: stress-test the task and its design against the architecture before writing code.
3. **/tdd**: write failing tests first, then make them pass (red → green → refactor).
4. **/implement**: implement the agreed design.

## Architecture guardrails
- **Call out design violations and changes.** If a task, request or code change
  would break or change anything in `context/design/architecture.md`, say so explicitly
  before doing it: what changes, why, and the alternatives. Don't silently deviate.
- **Keep `context/design/architecture.md` up to date.** Any agreed design change is
  reflected there in the same change that implements it. Also update
  `context/design/battle_sequence.txt` if the flow changes, and tick off
  `context/planning/workplan.md` when an issue is done.

## Build and test
- Build: `.\gradlew.bat assembleDebug`
- Install on running emulator/device: `.\gradlew.bat installDebug`
- Unit tests: `.\gradlew.bat test`
- If Gradle can't find Java, set `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`.
- Bottom-screen testing on the emulator needs a secondary display
  (Extended Controls → Displays → Add secondary display → Apply changes).
