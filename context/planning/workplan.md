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
| [ ] | [#4 WebView viewer for top screen](https://github.com/jaydenfont/ThorShowdown/issues/4) (**spike only**) | none | Run alongside #1. Answer: can the WebView log in as the user, and can injected CSS hide Showdown's controls while keeping stat tooltips? |
| [ ] | [#11 Error Handling and Logging](https://github.com/jaydenfont/ThorShowdown/issues/11) | none | Start the logging part early so #1 onward can use it. Server errors arrive as `\|pm\|~\|` and `\|popup\|`. Issue has no description yet. |

## Phase 2: Core battle loop

| Status | Issue | Depends on | Notes |
|---|---|---|---|
| [ ] | [#5 Implement Login](https://github.com/jaydenfont/ThorShowdown/issues/5) | #1, #4 spike | Fresh challstr → assertion on every connect. WebView login approach comes from the spike. |
| [ ] | [#6 Implement Showdown Session Manager](https://github.com/jaydenfont/ThorShowdown/issues/6) | #1 | One session per room ID. See open items below. |
| [ ] | [#10 Implement start of battle flow](https://github.com/jaydenfont/ThorShowdown/issues/10) | #1, #5, #6 | `\|/utm null` → `\|/search gen9randombattle` → wait for `\|init\|battle`; support cancel. Issue has no description yet. |
| [ ] | [#7 Bottom Screen Battle Functionality](https://github.com/jaydenfont/ThorShowdown/issues/7) | #6 | Functional first (moves, Tera, switch, forfeit, log). DS styling after the loop works. |
| [ ] | [#4 WebView viewer for top screen](https://github.com/jaydenfont/ThorShowdown/issues/4) (**full**) | #5, #6, spike | Follows the session's current room ID. |

## Phase 3: App shell and resilience

| Status | Issue | Depends on | Notes |
|---|---|---|---|
| [ ] | [#8 Implement main menu](https://github.com/jaydenfont/ThorShowdown/issues/8) | #5, #10 | P0 minimum: Login/Logout, Start battle, Active rooms. Ladder, Teambuilder, Settings can be stubs. |
| [ ] | [#9 Implement Gamepad controls](https://github.com/jaydenfont/ThorShowdown/issues/9) | #7, #8 | Touch first in #7/#8, then map Thor buttons for menu and battle. |
| [ ] | [#12 Reconnecting to battles](https://github.com/jaydenfont/ThorShowdown/issues/12) | #1, #6, #4 | Native client rejoins rooms and restores the latest `\|request\|`; WebView reloads the room. |
| [ ] | [#11 Error Handling and Logging](https://github.com/jaydenfont/ThorShowdown/issues/11) (**finish**) | #1, #6 | Surface server errors/popups in the UI. |

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
#4 WebView spike ──▶ #5 Login, #4 WebView (full)
#2 Signing ──▶ #3 Release Action
```

## Open items to resolve in the issues
- **#6 vs architecture:** #6 says the Session Manager sends commands to the App
  Client; `design/architecture.md` has the Repository doing it. Pick one and update the other.
- **#6 point 3 ("managing the state of the WebView"):** keep it to room ID and status,
  not the WebView object, consistent with "does not directly own the UI".
- **#4 wording:** the WebView does receive battle updates over its own connection;
  the rule is that the app never sends commands through it and it never makes choices.
- **#7 team preview / gimmicks:** not needed if P0 is gen9randombattle only (Tera is
  the only gimmick). Confirm P0 format.
- **End of battle** (`|win|` / `|tie|`, result screen, return to menu) has no issue;
  fold into #6/#7/#8 or add one.
- **#10, #11** have no descriptions yet.
