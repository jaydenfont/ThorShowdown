# ThorShowdown P0 Workplan

Tracks the P0 build order. GitHub issues are the source of truth for scope;
this doc is for ordering, dependencies and notes.

- Board: https://github.com/users/jaydenfont/projects/4/views/1
- Issues: https://github.com/jaydenfont/ThorShowdown/issues
- Design: [architecture.md](../design/architecture.md), [battle_sequence.txt](../design/battle_sequence.txt),
  [showdown_api_notes.md](../research/showdown_api_notes.md)

**P0 goal:** log in, start a gen9randombattle from the main menu, play it to the end
from the bottom screen (touch or gamepad) while the top screen shows the battle in
a WebView, and survive a dropped connection.

## Phase 1: Foundations

| Status | Issue | Depends on | Notes |
|---|---|---|---|
| [x] | [#1 Implement Showdown App Client](https://github.com/jaydenfont/ThorShowdown/issues/1) | none | Critical path. Pure-Kotlin module, unit tested without an emulator. Protocol details in `research/showdown_api_notes.md`. |
| [x] | [#4 WebView viewer for top screen](https://github.com/jaydenfont/ThorShowdown/issues/4) (**spike only**) | none | Run alongside #1. Answer: can the WebView log in as the user, and can injected CSS hide Showdown's controls while keeping stat tooltips? |
| [ ] | [#11 Error Handling and Logging](https://github.com/jaydenfont/ThorShowdown/issues/11) | none | Start the logging part early so #1 onward can use it. Server errors arrive as `\|pm\|~\|` and `\|popup\|`. Issue has no description yet. |

## Phase 2: Core battle loop

| Status | Issue | Depends on | Notes |
|---|---|---|---|
| [x] | [#5 Implement Login](https://github.com/jaydenfont/ThorShowdown/issues/5) | #1 | Native login + saved credentials (Keystore-encrypted). Design: [login.md](../design/login.md). WebView login moved to #4 (hand over the `sid` cookie). |
| [x] | [#6 Implement Showdown Session Manager](https://github.com/jaydenfont/ThorShowdown/issues/6) | #1 | One session per room ID; room commands, leave (forfeits if unfinished), room switching. Design: [session_manager.md](../design/session_manager.md). |
| [x] | [#10 Implement start of battle flow](https://github.com/jaydenfont/ThorShowdown/issues/10) | #1, #5, #6 | Matchmaker: search / cancel / refusal / timeout; temporary bottom-screen lobby. Design: [battle_start.md](../design/battle_start.md). Manual end-to-end battle done with #4. |
| [ ] | [#7 Bottom Screen Battle Functionality](https://github.com/jaydenfont/ThorShowdown/issues/7) | #6 | **First pass done:** moves, Tera, party, switching, undo, forfeit, timer. Design: [battle_menu.md](../design/battle_menu.md). **Still to do:** battle log, DS styling, display names, move type/power. |
| [x] | [#4 WebView viewer for top screen](https://github.com/jaydenfont/ThorShowdown/issues/4) (**full**) | #5, #6, spike | Follows the current room; sid cookie via WebSession; document-start CSS/JS. Design: [webview.md](../design/webview.md). |

## Phase 3: App shell and resilience

| Status | Issue | Depends on | Notes |
|---|---|---|---|
| [ ] | [#8 Implement main menu](https://github.com/jaydenfont/ThorShowdown/issues/8) | #5, #10 | P0 minimum: Login/Logout, Start battle, Active rooms. Ladder, Teambuilder, Settings can be stubs. |
| [ ] | [#9 Implement Gamepad controls](https://github.com/jaydenfont/ThorShowdown/issues/9) | #7, #8 | Touch first in #7/#8, then map Thor buttons for menu and battle. |
| [ ] | [#12 Reconnecting to battles](https://github.com/jaydenfont/ThorShowdown/issues/12) | #1, #6, #4 | Native client rejoins rooms and restores the latest `\|request\|`; WebView reloads the room. |
| [ ] | [#11 Error Handling and Logging](https://github.com/jaydenfont/ThorShowdown/issues/11) (**finish**) | #1, #6 | Surface server errors/popups in the UI. |

## Testing track (alongside the phases)

| Status | Issue | Depends on | Notes |
|---|---|---|---|
| [ ] | [#16 Emulator workflow tests and QA reports](https://github.com/jaydenfont/ThorShowdown/issues/16) | #7 (first pass) | Hermetic scenarios on the emulator against a fake server, `:tools:qaRun` captures both screens, video and logs into an HTML report, and the `qa` skill reviews and publishes it. Design: [workflow_tests.md](../design/workflow_tests.md). **Done:** framework and four scenarios (Login, SearchAndCancel, BattleTurn, ForfeitAndLeave). **Found:** party panel stays open after a switch; bottom-screen dialogs open on the top screen. **Later:** timer, long-press description and network-loss scenarios; live smoke tests with a guest bot. |

## Phase 4: Release

| Status | Issue | Depends on | Notes |
|---|---|---|---|
| [ ] | [#2 Application Signing](https://github.com/jaydenfont/ThorShowdown/issues/2) | none | Debug builds are fine for personal testing until here. |
| [ ] | [#3 Create Action for building release package](https://github.com/jaydenfont/ThorShowdown/issues/3) | #2 | Signing key and passwords stored as GitHub secrets. |

## Dependency graph

```
#1 App Client ──┬──▶ #5 Login ──┬──▶ #10 Start battle ──▶ #8 Main menu ──▶ #9 Gamepad
                │               │                              ▲
                ├──▶ #6 Session ┼──▶ #7 Bottom screen ─────────┴──────────▶ #9 Gamepad
                │      Manager  └──▶ #4 WebView (full) ──▶ #12 Reconnect
                └──▶ #11 Errors/Logging
#4 WebView spike ──▶ #4 WebView (full, incl. WebView login via the sid cookie)
#2 Signing ──▶ #3 Release Action
```

## Open items to resolve in the issues
- **#4 wording:** the WebView does receive battle updates over its own connection;
  the rule is that the app never sends commands through it and it never makes choices.
- **#7 team preview / gimmicks:** not needed if P0 is gen9randombattle only (Tera is
  the only gimmick). Confirm P0 format.
- **End of battle:** #6 keeps finished sessions with their result until the player
  leaves; the result screen and "leave" button belong to #7, return to menu to #8.
- **#11** has no description yet. Search refusals (popup / PM from `~` right after a
  search) are already shown by #10; other popups are #11's.
- **Later (post-#4):** let the bottom screen control Showdown's display options in the
  WebView (2D/3D sprites, etc.). Likely part of Settings (#8) or its own issue.
- **#8 menu placement:** the #8 issue puts the main menu on the **top** screen, but #5
  put login on the **bottom** screen and #10 added a temporary bottom-screen lobby.
  Decide in #8: move login to the top, or put the menu on the bottom.
