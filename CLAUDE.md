# ThorShowdown

Pokémon Showdown client for the AYN Thor (dual-screen Android handheld).
Kotlin + Jetpack Compose. Modules:
- `app/`: Android app (UI, ViewModel, Repository, SessionManager).
- `showdown-client/`: pure-Kotlin JVM library for the Showdown protocol
  (see `context/design/showdown_client.md`).

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
- All design docs should contain a sequence diagram and architecture diagram in addition to code snippet examples

## Build and test
- Build: `.\gradlew.bat assembleDebug`
- Install on running emulator/device: `.\gradlew.bat installDebug`
- Unit tests (all modules): `.\gradlew.bat test`
- Client library tests only: `.\gradlew.bat :showdown-client:test`
- Live test against the real Showdown server (manual, needs network, never in CI):
  `.\gradlew.bat :showdown-client:liveTest`
- Instrumented tests (Keystore, DataStore; manual, needs the emulator, never in CI). Set
  `ANDROID_SERIAL=emulator-5554` first so they don't run on a plugged-in Thor:
  `.\gradlew.bat :app:connectedDebugAndroidTest`
- Lint (whole project, must pass before merging): `.\gradlew.bat ktlintCheck`
  (auto-fix: `.\gradlew.bat ktlintFormat`)
- If Gradle can't find Java, set `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`.
- CI (`.github/workflows/ci.yml`) runs `ktlintCheck`, `test` and `assembleDebug` on
  every PR and on pushes to `main`.
- Bottom-screen testing on the emulator needs a secondary display
  (Extended Controls → Displays → Add secondary display → Apply changes).
