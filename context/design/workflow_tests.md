# Emulator Workflow Tests and QA Reports

Design for [#16 Emulator workflow tests and QA reports](https://github.com/jaydenfont/ThorShowdown/issues/16): workflow tests that run the real app on the emulator against a scripted
fake Showdown server, capture both screens and the logs, and produce an HTML report that a QA
agent can review and publish. Where it fits: [architecture.md](architecture.md).

## Purpose

Unit tests cover logic. Workflow tests cover the app as a user sees it: both displays, the real
Activity, `Presentation`, ViewModel, Repository, SessionManager and `ShowdownClient`, with only the
network and device storage replaced. Every run leaves evidence (screenshots per checkpoint, video
of both screens, logcat) and a self-contained report. An agent can add visual judgement to that
report, but the report is complete without it.

## Decisions

| Topic | Decision |
|---|---|
| Layers | Unit (existing, CI) → **hermetic workflow tests** (this doc, emulator, local only) → live smoke tests (later issue: guest bot challenges `thorshowdowntest`). |
| Target | The emulator only. The runner refuses any serial that isn't `emulator-*`, so a plugged-in Thor is never touched. |
| Fake server | A fake `Transport` + `LoginApi`, the client's only network seams. Login is answered automatically (challstr → `updateuser`); everything after is scripted by the test: `awaitSent(command)` then `push(frames)`. |
| Fakes location | `FakeShowdownServer` and `FakeLoginApi` move to `:showdown-client` **`java-test-fixtures`** (built-in Gradle plugin), used by the client's unit tests and `app/androidTest`. |
| Injection (architecture change) | New `AppContainer` interface: `transport`, `loginApi`, `credentialStore`, `webSession`. `ThorShowdownApp` builds the repository from `DefaultAppContainer` unless a test called `installContainer(...)` first; installing after the repository exists throws. Composition, no inheritance, no flavor. First step towards Hilt. |
| Hermetic state | `FakeAppContainer` uses an in-memory `CredentialStore` and a recording `WebSession`. No `pm clear`, so the emulator's saved login is untouched. One `am instrument` per scenario, so every scenario gets a fresh process and repository. |
| Top screen | The WebView still loads the real site for the fake room ("room doesn't exist"). Top checks are agent-judged only: status line, no Showdown controls visible. Making it hermetic (web origin in the container) is a later option. |
| Fixtures | Per-scenario frame files in `app/src/androidTest/assets/qa/`, cut from real transcripts, players renamed `qa-player` / `qa-opponent`. |
| Driving the UI | Scenarios → **robots** (`LoginRobot`, `LobbyRobot`, `BattleRobot`) → `TestTags` (one object, used by the composables). Robots start on `createAndroidComposeRule<MainActivity>`; if it can't reach the `Presentation` window they switch to UI Automator (`By.displayId`), a dependency that needs approval first. Scenarios never touch selectors, so UI redesigns (#8 main menu, #14 second activity) change robots only. |
| Checkpoints | Per-scenario enum (`BattleTurnCheckpoint.MovesShown`). `qa.checkpoint(c)` logs `QA_CHECKPOINT <n> <Scenario>.<Name>`; with `qaCapture=true` it waits for `/data/local/tmp/qa/ack-<n>` (polled via `UiAutomation.executeShellCommand`), failing after 10 s. Without the argument it only logs, so tests run normally from Android Studio. |
| Runner | `:tools:qaRun` (`JavaExec`, depends on `:app:installDebug` + `:app:installDebugAndroidTest`). Per scenario: check target and secondary display → clear logcat → start both videos → `am instrument -w -e class <Scenario> -e qaCapture true` → capture both screens on each checkpoint and ack → stop videos → save logcat → run log checks → write results. `-Pscenarios=Login,BattleTurn`; default all. |
| Capture | Top: `adb exec-out screencap -p`, `adb shell screenrecord` (mp4, 3 min cap). Bottom: `adb emu screenrecord screenshot <dir> --display 1`, `adb emu screenrecord start --display 1 <file>.webm` (console display index 1 = Android display 2). |
| Secondary display | If missing: `adb emu multidisplay add 1 1240 1080 400 0` (Thor's bottom screen size). |
| Log checks | Deterministic, by the runner: canary values (fake password, sid, assertion) anywhere in logcat, `FATAL EXCEPTION`, `ANR in`, and `SEVERE` from our loggers each fail the scenario. |
| Specs | `qa/scenarios/<Scenario>.md`: purpose, then one heading per checkpoint enum name with the expected top and bottom screens. The runner reports any checkpoint without a heading, or heading without a checkpoint, as an error. |
| Output | `qa-runs/<yyyyMMdd-HHmmss>/` at the repo root (gitignored): `report.json`, `report.html`, and per scenario `checkpoints/NN-Name-{top,bottom}.png`, `top.mp4`, `bottom.webm`, `logcat.txt`, `instrument.txt`. |
| Report | One renderer, `:tools:qaReport <runDir>`, always run by `qaRun`. Static HTML, inline CSS, no JS dependencies, relative links (opens from disk, like Gradle's reports). Summary bar, scenario table, a card per scenario with checkpoints (top and bottom side by side, spec text, verdict), log-check hits, assertion failure, links to videos and logcat. Visual checks show **Not reviewed** until a review exists. Built with stdlib + an HTML-escape helper (no kotlinx.html). |
| Agent review | The QA skill writes `review.json` (verdict `Pass` / `Fail` / `NeedsHuman`, reason, per scenario/checkpoint/screen) and re-runs `qaReport`, which merges it. The agent never edits HTML, tests or specs. |
| Publishing | The QA skill publishes the run folder as a private multi-file artifact (HTML, screenshots, logcat; videos only if each is under 15 MB, otherwise the local path is shown) and replies with the link. |
| QA skill | `.claude/skills/qa/SKILL.md` in the repo: run → read report and specs → review screenshots and logs → `review.json` → re-render → publish → summary. Exploratory adb-driven runs are later. |
| First scenarios | `Login`, `SearchAndCancel`, `BattleTurn` (moves, choose + undo, switch, forced switch after a faint), `ForfeitAndLeave`. Timer, long-press descriptions and network loss follow. |
| CI | Unchanged: `:tools` unit tests run in `test`. Scenarios and `qaRun` are local only. |

## Architecture

```mermaid
flowchart TB
    subgraph host ["Host (Windows)"]
        Skill["QA skill<br/>(.claude/skills/qa)"]
        Run[":tools:qaRun<br/>QaRunner"]
        Rep[":tools:qaReport<br/>ReportRenderer"]
        Adb["Adb (interface)<br/>AdbCli: adb, adb emu"]
        Out[("qa-runs/&lt;ts&gt;/<br/>report.json, review.json,<br/>report.html, png, mp4, webm, logcat")]
        Specs["qa/scenarios/*.md"]
    end

    subgraph emu ["Emulator (emulator-5554)"]
        subgraph test ["app androidTest (same process)"]
            Sc["Scenario<br/>(BattleTurnScenario)"]
            Rob["Robots<br/>Login / Lobby / Battle"]
            QA["QaCheckpoints"]
            FC["FakeAppContainer<br/>FakeShowdownServer, FakeLoginApi,<br/>InMemoryCredentialStore, RecordingWebSession"]
        end
        subgraph app ["app"]
            App["ThorShowdownApp<br/>installContainer()"]
            MA["MainActivity + Presentation"]
            Repo["Repository → SessionManager → ShowdownClient"]
        end
        Disp["Display 0 (top) / display 2 (bottom)"]
    end

    Skill -- "runs, reviews, writes review.json, publishes" --> Run
    Skill --> Rep
    Run -- "am instrument, logcat, screencap,<br/>emu screenrecord, ack files" --> Adb
    Adb --> emu
    Run --> Out
    Rep -- "reads report.json + review.json + specs" --> Out
    Rep --> Specs
    Sc --> Rob --> MA
    Sc -- "awaitSent / push" --> FC
    Sc --> QA
    FC -- "installed before launch" --> App
    App --> Repo
    Repo -- "Transport / LoginApi" --> FC
    MA --> Disp
```

## Sequences

### One scenario with a checkpoint

```mermaid
sequenceDiagram
    participant R as QaRunner (host)
    participant E as Emulator (adb)
    participant S as Scenario (androidTest)
    participant F as FakeShowdownServer
    participant A as App

    R->>E: check target, secondary display, logcat -c
    R->>E: start screenrecord (top) + emu screenrecord (bottom)
    R->>E: am instrument -e class BattleTurnScenario -e qaCapture true
    E->>S: run
    S->>A: installContainer(FakeAppContainer), launch MainActivity
    A->>F: open, (auto) challstr
    S->>A: login robot: enter fake credentials, Log in
    A->>F: |/trn qa-player,0,ASSERT  (auto) |updateuser|
    S->>F: push(battle init + |request|)
    S->>S: qa.checkpoint(MovesShown)
    S-->>E: log "QA_CHECKPOINT 1 BattleTurn.MovesShown"
    R->>E: logcat sees checkpoint
    R->>E: screencap (top) + emu screenshot (bottom)
    R->>E: touch /data/local/tmp/qa/ack-1
    S->>E: poll ack-1 (UiAutomation shell)
    S->>A: battle robot: chooseMove(1)
    S->>F: awaitSent("/choose move 1|<rqid>")
    S->>F: push(turn frames + next |request|)
    S-->>E: instrumentation result
    E-->>R: OK / failure stack
    R->>E: stop recordings, logcat dump
    R->>R: log checks, spec match, write report.json
    R->>R: qaReport → report.html
```

### Agent review

```mermaid
sequenceDiagram
    participant Q as QA skill (agent)
    participant R as :tools:qaRun
    participant H as :tools:qaReport
    participant D as qa-runs/<ts>/
    participant P as Artifact (published page)

    Q->>R: gradlew :tools:qaRun [-Pscenarios=...]
    R->>D: captures, report.json, report.html (Not reviewed)
    Q->>D: read report.json, specs, screenshots, logcat
    Q->>D: write review.json
    Q->>H: gradlew :tools:qaReport -PrunDir=<ts>
    H->>D: report.html with verdicts merged
    Q->>P: publish report.html + files
    Q-->>Q: reply: link + summary
```

## Code examples

### Composition root (app)

```kotlin
/** What the network and device provide. Tests install a fake before the repository exists. */
interface AppContainer {
    val transport: Transport
    val loginApi: LoginApi
    val credentialStore: CredentialStore
    val webSession: WebSession
}

class ThorShowdownApp : Application() {
    private var container: AppContainer? = null
    private var repositoryCreated = false

    /** Replaces the default container. Must be called before the repository is first used. */
    fun installContainer(replacement: AppContainer) {
        check(!repositoryCreated) { REPOSITORY_ALREADY_CREATED }
        container = replacement
    }

    val repository by lazy {
        repositoryCreated = true
        val deps = container ?: DefaultAppContainer(this, ShowdownConfig())
        val client = DefaultShowdownClient(ShowdownConfig(), deps.transport, deps.loginApi, applicationScope)
        ShowdownRepository(client, LocalDataSource(deps.credentialStore), deps.webSession, applicationScope)
    }
}
```

### Fake server additions (test fixtures)

```kotlin
class FakeShowdownServer : Transport {
    /** Answers the login handshake like the real server: challstr on open, updateuser on /trn. */
    fun autoLogin(username: String) { /* ... */ }

    /** Suspends until the client sends a message ending in [command] (rqid-agnostic), or times out. */
    suspend fun awaitSent(command: String, timeout: Duration = 5.seconds): String { /* ... */ }
}
```

### A scenario

```kotlin
class BattleTurnScenario {
    @get:Rule val rule = QaRule(Scenario.BattleTurn)   // installs FakeAppContainer, launches MainActivity

    @Test
    fun BattleTurn_ChooseUndoAndSwitch_SendsEachChoice() = rule.run { server, qa ->
        login.logIn(Fakes.USERNAME, Fakes.CANARY_PASSWORD)
        server.push(Frames.load(Fixture.BattleTurnStart))
        battle.assertMovesShown(count = 4)
        qa.checkpoint(BattleTurnCheckpoint.MovesShown)

        battle.chooseMove(1)
        server.awaitSent(Commands.MOVE_1)
        battle.undo()
        server.awaitSent(Commands.UNDO)
        qa.checkpoint(BattleTurnCheckpoint.UndoneBackToChoose)

        battle.switchTo(3)
        server.awaitSent(Commands.SWITCH_3)
        server.push(Frames.load(Fixture.BattleTurnFaint))
        qa.checkpoint(BattleTurnCheckpoint.ForcedSwitch)
    }
}

enum class BattleTurnCheckpoint { MovesShown, UndoneBackToChoose, ForcedSwitch }
```

### Spec (`qa/scenarios/BattleTurn.md`)

```markdown
# BattleTurn
Choose a move, undo it, switch, then handle a forced switch after a faint.

## MovesShown
- Bottom: battle header, four move tiles with PP, Tera toggle, Party and Forfeit buttons.
- Top: status line for the room; no Showdown move or switch controls visible.

## UndoneBackToChoose
- Bottom: move tiles enabled again, no "Waiting" banner.
```

### Runner (tools)

```kotlin
/** Narrow view of adb the runner needs; AdbCli shells out, FakeAdb backs the unit tests. */
interface Adb {
    suspend fun shell(vararg args: String): String
    suspend fun emu(vararg args: String): String
    suspend fun pull(remote: String, local: File)
    fun logcat(): Flow<String>
}

class QaRunner(private val adb: Adb, private val runDir: File) {
    suspend fun run(scenario: ScenarioId): ScenarioResult {
        checkTarget()
        val recordings = startRecordings(scenario)
        val outcome = instrumentWithCheckpoints(scenario)
        recordings.stop()
        val log = saveLogcat(scenario)
        return ScenarioResult(scenario, outcome, LogChecks.scan(log, Canaries.ALL))
    }
}
```

### Review file

```json
{
  "reviewer": "qa-skill",
  "scenarios": {
    "BattleTurn": {
      "checkpoints": {
        "MovesShown": {
          "bottom": { "verdict": "Pass", "reason": "Four tiles with PP, Tera toggle present." },
          "top": { "verdict": "NeedsHuman", "reason": "Room-not-found page; controls hidden." }
        }
      }
    }
  }
}
```

## Test seams

| Seam | Tests |
|---|---|
| `FakeShowdownServer` / `FakeLoginApi` (test fixtures) | Auto-login handshake; `awaitSent` matches and times out. |
| `ThorShowdownApp.installContainer` | Installed container is used; installing after first repository use throws. |
| `:tools` runner logic behind `Adb` (with `FakeAdb`) | Checkpoint line parsing; target check refuses non-emulators; missing display is added; spec matching both ways; canary, crash, ANR and `SEVERE` detection; `report.json` contents. |
| `ReportRenderer` | Valid HTML with no review (Not reviewed), review merged, HTML escaping of log and spec text, missing captures shown as missing. |
| Scenarios | The app end to end on the emulator (run by hand via `qaRun`). |

## Files

- `showdown-client/src/testFixtures/...`: `FakeShowdownServer`, `FakeLoginApi` (moved), auto-login, `awaitSent`.
- `app/src/main/.../AppContainer.kt`, `DefaultAppContainer.kt`; `ThorShowdownApp` uses them; `ui/TestTags.kt`.
- `app/src/androidTest/.../qa/`: `QaRule`, `QaCheckpoints`, `FakeAppContainer`, robots, scenarios; `assets/qa/` fixtures.
- `tools/src/main/.../qa/`: `Adb`, `AdbCli`, `QaRunner`, `LogChecks`, `SpecMatcher`, `ReportRenderer`, mains; tasks `qaRun`, `qaReport`.
- `qa/scenarios/*.md`; `.claude/skills/qa/SKILL.md`; `.gitignore` adds `/qa-runs`.
- Docs: `architecture.md` (AppContainer, test layers), `CLAUDE.md` (commands), `context/README.md`, `workplan.md`.

## Risks

- **Compose test rule and the `Presentation`**: the rule may not see the second window; the robot layer
  keeps the UI Automator fallback local.
- **Emulator console commands** (`adb emu screenrecord`, `multidisplay`) are emulator-version specific;
  the runner fails with a clear message if one is rejected.
- **Checkpoint timing**: captures happen while the test waits, but animations may still be running;
  robots wait for idle before each checkpoint.
- **Top screen** depends on the real site in hermetic runs, so its verdicts stay agent-judged.
