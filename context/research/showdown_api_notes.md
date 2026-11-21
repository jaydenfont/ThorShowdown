# Pokémon Showdown client protocol: notes from `unnipv/pokemon-slowdown`

Source: https://github.com/unnipv/pokemon-slowdown (default branch `master`, MIT licence).
Read on 2026-11-21 from a snapshot of `master`. Everything here comes from the Go source, tests and
`docs/IMPLEMENTATION_NOTES.md` unless it is marked **[INFERRED]**. **[INFERRED]** means my own
reading or general knowledge of Showdown, not something this repo's code does.

Key files (paths relative to repo root):

| File | Role |
| --- | --- |
| `internal/showdown/connection.go` | WebSocket client, reconnect loop, outbound framing |
| `internal/showdown/auth.go` | challstr -> `/api/login` -> `/trn` login flow |
| `internal/showdown/commands.go` | Typed wrappers for outbound commands |
| `internal/showdown/protocol.go` | Frame splitting, `\|` splitting, tag parsing, `ToID`, `Format`/`User` types |
| `internal/showdown/parser.go` | Line -> typed `Event` parser (global, room and battle messages) |
| `internal/showdown/events.go` | All event structs (about 90 types) |
| `internal/showdown/json.go` | `\|updatesearch\|` and `\|updatechallenges\|` JSON decoding |
| `internal/battle/request.go`, `choices.go` | `\|request\|` JSON model and `/choose` string builders |
| `internal/battle/reducer.go`, `state.go` | Applies events to a battle `State` (a pure reducer) |
| `internal/tui/app.go`, `battle.go`, `lobby.go`, `palette.go` | UI code that calls the client and handles events |
| `internal/teams/packed.go` | Packed team format for `/utm` |
| `docs/IMPLEMENTATION_NOTES.md` | Protocol pitfalls the author verified against the live server. **Worth reading in full.** |
| `testdata/battles/gen9-singles.txt` | A realistic raw battle transcript, including `\|request\|` JSON |

---

## 1. Connection

### WebSocket URL
```
wss://sim3.psim.us/showdown/websocket
```
- `connection.go:13`: `const DefaultURL = "wss://sim3.psim.us/showdown/websocket"`.
- Dialled with the header `User-Agent: pokemon-slowdown` and a 20 s dial timeout (`connection.go:204-207`).
- This is a raw WebSocket. There is no SockJS framing and no extra handshake. The server sends text right away.
- Inbound messages are limited to 1 MiB (`maxMessageBytes = 1 << 20`, `connection.go:17`). Server data is treated as untrusted.

### Inbound framing (`protocol.go:21-53`, `SplitFrames`)
- A single WebSocket text message can hold **several rooms' worth of lines**.
- Lines are separated by `\n`. A trailing `\r` is stripped to tolerate CRLF.
- A line starting with `>` sets the room for the lines after it: `>battle-gen9randombattle-123`.
  Lines before the first `>` are **global** (empty room ID).
- Empty lines are ignored.
- Example raw payload (from `connection_test.go:113`):
  ```
  >battle-gen9randombattle-1
  |player|p1|Alice|1|1500
  |turn|1
  ```
- Each line has the form `|TYPE|arg1|arg2|...`. A line that does **not** start with `|` is plain text for the room log (`RoomMessage`).
  A line that is exactly `|` is a blank spacer (`BattleClear`). `||text` is also plain text.

### Splitting lines (`protocol.go:58-72`, `parser.go:31-42`)
- **Bounded split** is required wherever the last field can contain `|`: `c`, `c:`, `chat`, `pm`, `challstr`, `formats`, `request`,
  `updatesearch`, `updatechallenges`, `queryresponse`, `updateuser`. For example `|c|user|message` is split into at most 4 fields and
  the remainder is kept whole.
- **Tags**: trailing arguments such as `[from] item: Life Orb`, `[miss]`, `[still]` or `[upkeep]` are pulled out into a map.
  A field counts as a tag only if its bracketed key matches `[a-z-]+` (`isTagArg`, `protocol.go:87-102`). Otherwise
  `|tier|[Gen 9] Random Battle` would be wrongly read as a tag.

### Outbound framing: the most important gotcha (`connection.go:280-299`, IMPLEMENTATION_NOTES "Outbound framing")
Every client-to-server message **must** have the form `ROOMID|MESSAGE`. The server **silently drops** a message
with no pipe (it actually sends a popup). Global commands therefore use an **empty room ID with a leading pipe**:

```
|/search gen9randombattle                         <- global
battle-gen9randombattle-1|/choose move 1|42       <- room-scoped
```

```go
msg := roomID + "|" + command   // connection.go:296
```
Every outbound write has a 10 s timeout. Commands are sent as plain text frames, one command per frame.

### Identifiers
`ToID(s)` (`protocol.go:134-148`) lowercases the input and keeps only `[a-z0-9]`. For example `[Gen 9] Random Battle` becomes `gen9randombattle`.
It is used to compare usernames (`tui/app.go` `isMe`) and to turn format names into IDs.

---

## 2. Authentication / login

### Flow (`auth.go`, `connection.go:229-237`)
1. Connect. The server sends `|challstr|<challstr>`. The challstr itself **contains pipes**, for example `4|abcdef...`.
   Keep everything after `|challstr|` (test: `|challstr|2|abc|def` gives `2|abc|def`, `connection_test.go:112,130`).
2. Whenever a `ChallStr` event arrives, the client stores it and runs `authenticate(challstr)` in a goroutine.
   This happens **on every connection, including reconnects**.
3. If there is no username, or **no password**, nothing is sent and the user stays a guest under the name the server assigned.
4. Otherwise the client makes an HTTP POST (`auth.go:15`, `fetchAssertion` at `auth.go:86-122`):
   ```
   POST https://play.pokemonshowdown.com/api/login
   Content-Type: application/x-www-form-urlencoded

   name=<username>&pass=<password>&challstr=<challstr>
   ```
   - HTTP client timeout is 20 s, and the response body is limited to 1 MiB.
   - The response body starts with `]` (protection against JSON hijacking). **Strip the leading `]` before parsing JSON.**
   - The response JSON is modelled as (`auth.go:22-30`):
     ```json
     {"assertion":"...","curuser":{"loggedin":true,"userid":"...","username":"..."},"actionsuccess":true}
     ```
     Only `assertion` is actually used.
   - An empty assertion produces the error `sign-in rejected for "<user>" (check the password)`.
5. The client sends (`auth.go:74`):
   ```
   |/trn <username>,0,<assertion>
   ```
   Test example: `|/trn alice,0,ASSERT-123` (`connection_test.go:210`).
6. Success: the server sends `|updateuser|<NAME>|<named 0/1>|<avatar>|<settings JSON>` (parsed at `parser.go:59-74`).
   `Named == true` means the user is logged in under that name.
7. Failure: the client emits an `AuthFailed{Err}` event (HTTP error, bad JSON or empty assertion). The server may instead reply with
   `|nametaken|<name>|<message>` or `|popup|...`; the live test treats both as failures (`live_auth_test.go:80-85`).

### Behaviour the author verified on the live server (README "Signing in", `auth.go:52-58`)
- A **bare `/trn NAME`** without an assertion is rejected with `Your authentication token was invalid`. You **cannot choose a guest name
  without going through `/api/login`**. The client therefore never sends `/trn` without a password.
- For an **unregistered** name, `/api/login` returns an assertion for **any password**, so the name is claimed.
- For a **registered** name, a wrong password returns no assertion, and the client reports an error.
- **[INFERRED]** The Showdown login server often returns an assertion starting with `;;` (for example `;;Wrong password`) to signal
  errors. This client does not check for that and only tests whether the assertion is empty. A Kotlin client should treat
  an assertion starting with `;;` as a failure.

### Mid-session sign-in (`connection.go:96-117`)
A fresh `challstr` only arrives on a new connection, so `Login(creds)` stores the credentials and then calls `Reconnect()`.
`Reconnect()` closes the socket and wakes the reconnect loop immediately without backoff. The new connection's challstr then triggers
the login. The live test confirms this pattern: it expects at least 2 challstrs and `Connected{Reconnected:true}` (`live_auth_test.go:92-97`).

### Logout (`auth.go:123-128`, `tui/palette.go:513-533`)
`Logout()` clears the credentials and sends `|/trn` (empty). The UI then also calls `Reconnect()` to get a fresh guest identity.

### Sessions and cookies
- **No cookie or `sid` handling at all.** The client keeps no session token. It logs in again with username and password plus a new
  challstr on **every** connect. The README says that "passwords, assertions, cookies and auth tokens are never logged".
- The password is stored in the OS keychain (`internal/config/keyring.go`, via `zalando/go-keyring`) only if the user chose "Remember".
  The config file stores only the username.
- **[INFERRED]** For Android, store the password in EncryptedSharedPreferences or the Keystore, the equivalent of this keychain approach.
  Alternatively, implement the official client's `sid` cookie flow with the `upkeep` action. This repo does not show how to do that.

---

## 3. Starting battles

### Formats list
On connect the server sends `|formats|...` (global). It is parsed by `parseFormats` (`parser.go:452-546`):
- The payload is a flat list separated by `|`. A field that is **empty or `,<number>`** is a section marker, and the **next** field is the section name.
  `,LL` is a local-ladder marker and is skipped.
- Other fields are `Display Name,<hex flags>`, for example `[Gen 9] Random Battle,4f`. The ID is `ToID(name)`.
- Flag bits (from the reference client, documented at `parser.go:459-469`): `1` preset team (random), `2` searchable on the ladder,
  `4` challengeable, `8` tournament, `16` level 50, `32` partner, `64` best-of default, `128` tera preview, `256` item clause.
- The legacy suffix forms `,#`, `,,` and `,` are still accepted as a fallback.
- The UI waits for `FormatsUpdated` before auto-queueing a search or sending a pending challenge (`tui/app.go:354-371`).
  That event is a convenient "server ready" signal.

### Ladder search (`tui/lobby.go:184-202`, `commands.go:40-48,70-76`)
Always upload a team **first**, then search:
```
|/utm <packed team>     (or "|/utm null" for random/preset-team formats)
|/search gen9randombattle
```
Cancel the search with `|/cancelsearch`.
- The packed team format comes from `internal/teams/packed.go` (`Pack()`): Pokémon are joined by `]`, with fields per the
  Showdown `sim/TEAMS.md` packed format. `PackedOrNull()` returns `null` for empty teams.

### Search status: `|updatesearch|<JSON>` (`json.go:5-18`)
```json
{"searching":["gen9randombattle"],"games":{"battle-gen9randombattle-123":"[Gen 9] Random Battle"}}
```
`searching` lists the formats currently being searched. `games` maps battle room IDs to their titles. The UI keeps `games` and uses it to
clear "pending battle" state (`tui/app.go:372-381`).

### Challenges (`commands.go:50-68`, `json.go:20-38`)
```
|/challenge bob, gen9randombattle      <- note the ", " separator
|/accept bob
|/reject bob
|/cancelchallenge bob
```
Send `/utm` before `/challenge` or `/accept` if the format needs a team. **[INFERRED]** The repo's challenge path in `palette.go` does
not upload a team; only `queueFormat` does.
Incoming state arrives as `|updatechallenges|<JSON>`:
```json
{"challengesFrom":{"bob":"gen9randombattle"},"challengeTo":{"to":"alice","format":"gen9ou"}}
```
`challengeTo` may be null.

### How the battle room ID arrives
- When a match is made, the **server auto-joins you** to the battle room. You start receiving frames prefixed with
  `>battle-<formatid>-<number>`, beginning with `|init|battle` and then `|title|...`, `|j|...` and so on.
- `RoomInit{Type:"battle"}` is the trigger for opening a battle view. On that event the UI calls `RememberRoom(roomID)`
  so the room is rejoined after a reconnect, and creates the battle view (`tui/app.go:389-395`).
- The room ID also shows up in `updatesearch.games`. A `|battle|ROOMID|P1|P2` or `|b|` line is parsed into `BattleStarted`
  (`parser.go:121-133`), and that also calls `RememberRoom`.
- As a fallback, any battle event in a room with no view creates one (`tui/app.go:416-427`).
- Spectating means sending `|/join battle-...`. The UI accepts a bare ID or a `replay.pokemonshowdown.com/<id>` URL (`palette.go:555-573`).

---

## 4. During a battle

### `|request|<JSON>` (`internal/battle/request.go`)
Sent to you (in the battle room) whenever you must decide or wait. The fields this client models:

```text
Request {
  wait: bool                 // true -> nothing to choose (opponent is deciding)
  teamPreview: bool          // team preview -> choose "team ..."
  forceSwitch: [bool]        // one entry per active slot; true = must switch
  active: [PokemonMoveRequest]
  side: SideRequest
  ally: SideRequest?         // multi battles
  rqid: int                  // request id: echo it back in /choose
  noCancel: bool
  maxChosenTeamSize: int
  update: bool
}
PokemonMoveRequest {
  moves: [{move, id, pp, maxpp, target, disabled (bool OR string reason), disabledSource}]
  trapped, maybeTrapped, maybeDisabled, maybeLocked: bool
  canMegaEvo, canMegaEvoX, canMegaEvoY, canUltraBurst: bool
  canZMove: [ {move, target} | null ]   // parallel to moves
  canDynamax: bool, maxMoves: {maxMoves:[{move,target,disabled}], gigantamax}
  canTerastallize: string               // tera type, e.g. "Ghost"
}
SideRequest { name, id ("p1"/"p2"), pokemon: [PokemonSwitchRequest], noCancel }
PokemonSwitchRequest {
  ident ("p1: Gengar"), details ("Gengar, L82, M"), condition ("241/241", "0 fnt", "120/300 brn"),
  active, stats {atk,def,spa,spd,spe}, moves [ids], baseAbility, ability, item, pokeball, commanding, reviving
}
```
- `disabled` can be a bool **or** a string, so it needs a custom deserializer (`request.go:100-127`). In Kotlin, use a custom
  kotlinx.serialization serializer or a `JsonElement` field.
- Request kind is decided in this order (`request.go:35-48`): `wait` gives WAIT, `teamPreview` gives TEAM, non-empty `forceSwitch` gives SWITCH,
  and anything else gives MOVE.
- The request's `side` is **authoritative** for your own team. The reducer uses it to sync party order, HP and moves (`reducer.go:480-560`).
  It also sets which side is "me" from `side.id`.
- A complete example is in `testdata/battles/gen9-singles.txt`. A trimmed version:
  ```
  |request|{"active":[{"moves":[{"move":"Shadow Ball","id":"shadowball","pp":24,"maxpp":24,"target":"normal","disabled":false}, ...],"canTerastallize":"Ghost"}],"side":{"name":"coffee_enjoyer","id":"p1","pokemon":[{"ident":"p1: Gengar","details":"Gengar, L82, M","condition":"241/241","active":true,...}, ...]},"rqid":2}
  ```
- **[INFERRED]** On the real server the `|request|` usually arrives just *before* the turn's battle log lines, as in the transcript.
  Do not show the choice UI until the log for the turn has been processed. This client simply sets `AwaitingChoice` whenever a request is
  applied and clears its "choice sent" indicator on the next `turn`, `move`, `switch`, `win`, `tie` or `error` event (`tui/battle.go:139-165`).

### Sending choices (`commands.go:78-86`, `battle/choices.go`, `request.go:162-197`)
```
<roomid>|/choose <choice>|<rqid>
```
Exact example from the test: `battle-gen9randombattle-1|/choose move 1 +1 terastalize|42` (`connection_test.go:390-395`).
- If `rqid` is 0 or missing, the `|<rqid>` suffix is left off. Echoing the rqid stops a late choice or `undo` from applying to the wrong turn.
- Choice syntax:
  - `move N`, 1-based move slot. Optional target `+N` for a foe or `-N` for an ally (doubles), then an optional mechanic:
    `mega`, `ultraburst`, `zmove`, `max`, `terastalize`. Example: `move 1 +1 terastalize`.
  - `switch N`, 1-based **party** slot (the position in `side.pokemon`). Only non-active, non-fainted Pokémon are legal (`BenchSlots`).
  - `pass`, `default` (server picks), `undo` (cancel a previous choice).
  - Team preview: `team 213456`, the slot digits concatenated (`TeamChoice`, `choices.go:36-43`). The UI fills in any
    unchosen slots in order (`tui/battle.go:440-467`).
  - Doubles: one decision per active slot, joined by `", "`. For example `move 1 +1, move 3 terastalize`.
- Singles never needs a target.
- Other in-battle commands: `<roomid>|/forfeit` (`tui/lobby.go:91`). Chat goes out as `<roomid>|<text>`, and any text
  starting with `/` is passed straight through, so the user can type `/timer on` or similar in the chat overlay (`tui/battle.go:734-748`).

### Battle protocol messages parsed (`parser.go:210-376`)
Argument positions follow `|TYPE|arg1|arg2|arg3`. "HP" fields are combined as `HP STATUS` and split on the first space (`splitHP`),
for example `50/100 brn`, `62%` or `0 fnt`. Your own Pokémon show `cur/max`; opponents show a percentage.

| Line | Fields |
| --- | --- |
| `\|init\|battle` | room type (also `chat`) |
| `\|title\|TEXT` | room title |
| `\|player\|p1\|NAME\|AVATAR\|RATING` | player info |
| `\|teamsize\|p1\|6`, `\|gametype\|singles`, `\|gen\|9`, `\|tier\|[Gen 9] Random Battle`, `\|rated\|MSG`, `\|rule\|TEXT` | header |
| `\|clearpoke`, `\|poke\|p1\|DETAILS\|ITEM`, `\|teampreview` | team preview |
| `\|start` | battle begins |
| `\|turn\|N` | new turn |
| `\|move\|p1a: X\|Move Name\|p2a: Y` + tags (`[still]`, `[miss]`, `[from]`) | move used |
| `\|switch\|p1a: X\|DETAILS\|HP STATUS`, `\|drag\|...` | switch-in (drag = forced) |
| `\|detailschange\|`, `\|-formechange\|`, `\|replace\|`, `\|swap\|` | forme / illusion / position changes |
| `\|cant\|POKE\|REASON\|MOVE`, `\|faint\|POKE` | |
| `\|-damage\|POKE\|HP STATUS` + `[from] ...`, `\|-heal\|...`, `\|-sethp\|` | HP changes |
| `\|-status\|POKE\|brn`, `\|-curestatus\|`, `\|-cureteam\|` | status |
| `\|-boost\|POKE\|atk\|1`, `\|-unboost\|`, `\|-setboost\|`, `\|-swapboost\|`, `\|-invertboost\|`, `\|-clearboost\|`, `\|-clearallboost\|`, `\|-copyboost\|` | stat stages |
| `\|-weather\|RainDance` (`[upkeep]`), `\|-fieldstart\|`, `\|-fieldend\|`, `\|-fieldactivate\|` | field |
| `\|-sidestart\|p1: NAME\|Stealth Rock`, `\|-sideend\|`, `\|-swapsideconditions` | side conditions |
| `\|-start\|POKE\|EFFECT`, `\|-end\|POKE\|EFFECT` | volatiles (e.g. Substitute) |
| `\|-item\|`, `\|-enditem\|` (`[eat]`), `\|-ability\|`, `\|-endability\|`, `\|-transform\|`, `\|-mega\|`, `\|-primal\|`, `\|-burst\|`, `\|-zpower\|`, `\|-zbroken\|` | |
| `\|-hint\|MSG`, `\|message\|MSG` / `\|-message\|` | text |
| `\|win\|USER`, `\|tie` | battle end |
| `\|inactive\|MSG`, `\|inactiveoff\|MSG` | battle timer on / off (prose; the seconds are pulled out with `firstInt`) |
| `\|upkeep`, `\|t:\|UNIXTIME` | end-of-turn marker, timestamp |
| `\|error\|MSG` | e.g. invalid choice; the UI shows it as `lastError` |
| `\|` (bare) | spacer |

- Any other `-xxx` minor action becomes a generic `BattleEffect{Kind, Args, Tags}`, for example `-supereffective`, `-crit`, `-miss`,
  `-terastallize|POKE|TYPE`. Unknown major types become `BattleUnknown`. **Nothing is dropped**, so changes in the protocol stay visible
  (`parser.go:369-375`). This is a pattern worth copying.
- Pokémon identifiers look like `p1a: Gengar` (side, slot letter, then name). DETAILS look like `Gengar, L82, M` (and may include `shiny` or `tera:Type`).
  Parsing is in `battle/state.go` / `reducer.go` (`parseDetails`, `identName`).

---

## 5. Other server messages handled (`parser.go:43-205`, `tui/app.go:301-436`)

| Message | Parsed as | Handling |
| --- | --- | --- |
| `\|challstr\|CHALLSTR` | `ChallStr` | triggers login |
| `\|updateuser\|NAME\|NAMED(0/1)\|AVATAR\|SETTINGS_JSON` | `UpdateUser` | sets the current username and logged-in flag |
| `\|nametaken\|NAME\|MSG` | `NameTaken` | toast "Name taken" |
| `\|formats\|...` | `FormatsUpdated` | format catalogue |
| `\|updatesearch\|JSON` | `SearchUpdated` | searching formats + games |
| `\|updatechallenges\|JSON` | `ChallengesUpdated` | incoming/outgoing challenges |
| `\|queryresponse\|TYPE\|JSON` | `QueryResponse` | reply to `/query TYPE ...`, e.g. `\|/query roomlist` |
| `\|popup\|MSG` | `Popup` | toast. **[INFERRED]** The real server uses `\|\|` inside popups as line breaks |
| `\|pm\|SENDER\|RECEIVER\|MSG` | `PM` | **Replies to global commands, including command errors and `/whoami` output, come back as PMs from `~`** (IMPLEMENTATION_NOTES "Command replies are PMs"). Show sender `~` or `""` as a system message |
| `\|notify\|TITLE\|MSG\|HIGHLIGHT` | `Notify` | desktop notification |
| `\|usercount\|N` | `UserCount` | |
| `\|c\|USER\|MSG`, `\|c:\|TIMESTAMP\|USER\|MSG`, `\|chat\|USER\|MSG` | `ChatMessage` | battle-room chat. Messages starting with `/raw ` or `/html ` are turned into plain text (`tui/htmltext.go`) |
| `\|html\|...`, `\|raw\|...` | `RoomHTML` | sanitised, never rendered as markup |
| `\|users\|N,USER1,USER2` | `RoomUsers` | the first character is the rank if it is not alphanumeric; `@` at index > 0 separates a status message (`Bob@!away`) |
| `\|j\|`, `\|l\|`, `\|n\|NEW\|OLDID` and uppercase `J/L/N` | join, leave, rename | uppercase = "too frequent to show" |
| `\|init\|TYPE`, `\|title\|TEXT` | room setup | |

Other outbound commands: `|/join ROOM`, `|/leave ROOM`, `|/whoami`, `|/query KIND [details...]`.

---

## 6. Reconnection, timers, errors, rate limiting

- **Reconnect loop** (`connection.go:178-198`): after any disconnect it emits `Disconnected{Err}`, then `Reconnecting{Attempt, Delay}`, and waits
  with exponential backoff `min * 2^(attempt-1)`, capped at max. Defaults are `BackoffMin = 1s` and `BackoffMax = 30s`. **The attempt counter never resets
  after a successful connection.** It resets only on an explicit `Reconnect()` (wake channel). **[INFERRED]** This looks like a minor bug: a
  long-lived session that drops a second time waits longer. In Kotlin, reset the counter after a successful connect.
- `Connected{Reconnected: bool}` tells a first connection apart from a reconnect.
- **Room rejoin** (`connection.go:262-274`): after every connect the client sends `|/join <room>` for every remembered room (battle rooms and
  spectated rooms). Login happens again automatically through the new challstr. **[INFERRED]** On reconnect, the server replays the battle
  log with `|init|` again and resends the current `|request|`, so the reducer should be able to rebuild state from scratch. This client
  does not reset its reducer explicitly; check that behaviour yourself.
- **Shutdown**: `Close()` uses `CloseNow()` rather than a graceful close, because a graceful close can block for seconds.
- **Battle timer**: there is no outbound timer command. The client only parses `|inactive|` and `|inactiveoff|`, pulls the first integer
  (seconds left) out of the message, and counts down locally from the time the message arrived (`reducer.go:96-104`, `tui/battle_render.go:172-185`).
  Users can type `/timer on` in battle chat.
- **Errors**: `|error|` in a battle (shown in the log and as `lastError`), `|popup|`, PMs from `~`, `|nametaken|`, and `AuthFailed` (HTTP/JSON/empty
  assertion). A request JSON that fails to parse is logged, not fatal (`reducer.go:480-485`).
- **Rate limiting**: **none is implemented.** **[INFERRED]** Showdown throttles fast message senders and may answer with a PM or popup. Consider
  a small outbound queue.
- **Event buffering**: the event channel holds 1024 events. `emit` blocks until there is room, which gives back-pressure, or until shutdown.
- **Security**: all server text is sanitised before display (`tui/sanitize.go`), and credentials and assertions are never logged.

---

## 7. Code structure and patterns worth copying

```
showdown (protocol layer, no UI or battle dependencies)
  Client: owns the socket, reconnect loop, credentials, remembered rooms, challstr
    Events() <-chan Event        // single typed stream of everything
    Send / SendTo(room, cmd)     // always emits "room|cmd"
    typed commands: Search, CancelSearch, SendTeam, Challenge, Accept, Reject,
                    CancelChallenge, Choose(room, choice, rqid), JoinRoom, LeaveRoom, Chat, Query, WhoAmI
    Login(creds) / Logout() / Reconnect()
  SplitFrames(raw) -> []Frame{RoomID, Lines}
  Parse(frame) -> []Event        // pure, fully unit-tested
  Event interface { Room() string }, ~90 structs embedding Base{RoomID}
battle (pure domain)
  Request (JSON model) + Choice/ChoiceSlot builders (String() -> protocol syntax)
  Reducer.Apply(Event) -> mutates State (the one place that knows protocol -> state)
tui (presentation) routes Events by room: global ones update the lobby, battle ones go to battleView.apply -> reducer
app (composition root) builds Client, sets credentials, Start()
```

Suggested Kotlin mapping **[INFERRED: my recommendation]**:

| Go | Kotlin / Android |
| --- | --- |
| `Client` (socket + reconnect + auth) | `ShowdownSocketDataSource` (OkHttp `WebSocket`), plus `LoginApi` (Retrofit/OkHttp POST to `/api/login`), coordinated by a `SessionManager` that owns credentials, challstr, remembered rooms and reconnect/backoff, and exposes `StateFlow<ConnectionState>` / `StateFlow<UserState>` |
| `Events() chan Event` | `SharedFlow<ShowdownEvent>` (sealed interface with `roomId`). Use a large buffer, or SUSPEND for back-pressure |
| `SplitFrames` + `Parse` | A pure `ProtocolParser` object. Port `parser_test.go` and `testdata/battles/*.txt` as JVM unit tests |
| typed commands | A `ShowdownCommands` class that always builds `"$roomId|$cmd"`. Assert the pipe in tests (`TestOutboundMessagesAlwaysContainPipe`) |
| `battle.Reducer` / `State` | `BattleReducer` (pure `(State, Event) -> State`, works well with immutable data classes), one per room, kept in a `BattleRepository` as `Map<roomId, StateFlow<BattleState>>` |
| `Request` / `Choice` | `@Serializable` data classes (custom serializer for `disabled`, `ignoreUnknownKeys = true`) and a `Choice.toProtocol()` builder |
| `tui` routing | ViewModels collect from repositories. `LobbyRepository` holds formats, search, challenges and user |

Notable design choices to copy:
1. **A protocol layer with no UI or domain dependencies**, turning bytes into typed events and typed commands into bytes.
2. **Never drop unknown lines.** Keep them as `Unknown` or `Effect` events so changes in the protocol stay visible.
3. **The `|request|` side is authoritative** for your own team. Derive the available mechanics (tera, mega, etc.) only from request flags, and hardcode nothing per generation.
4. **Remember rooms for rejoin.** Treat a reconnect as "replay everything".
5. **Mid-session login reconnects to get a fresh challstr.**
6. **Mock servers must enforce the `ROOMID|MSG` framing**, or tests will pass while every command gets dropped.

Other endpoints the repo uses (not part of the protocol):
- Dex data: `https://play.pokemonshowdown.com/data/pokedex.json`, `.../data/moves.json` (`internal/dex/dex.go:21`).
- Sprites: `https://play.pokemonshowdown.com/sprites/{gen5,gen5-shiny,gen5-back,gen5-back-shiny,ani,ani-shiny,ani-back,dex}/<name>.(png|gif)`.
  Naming is `toID(baseSpecies)-toID(forme)` for formes (e.g. `landorus-therian`), otherwise `toID(name)`. `ani-shiny-back` does not exist
  (IMPLEMENTATION_NOTES "Sprite naming").

---

## 8. Licence

- **MIT**, `Copyright (c) 2026 unnipv` (`LICENSE`; the GitHub API also reports `spdx_id: MIT`).
- `THIRD_PARTY.md`: the protocol was implemented independently from the public docs (`PROTOCOL.md`, `sim/SIM-PROTOCOL.md`, `sim/TEAMS.md` in
  `smogon/pokemon-showdown`). **No code from the AGPL-licensed Showdown web client was copied.** Sprites and dex data are downloaded at runtime and not redistributed.
- Go dependency of interest: `github.com/coder/websocket` (ISC).
- **[INFERRED]** You can freely reuse ideas or port code from this repo under MIT with attribution. Do not copy code from the official
  `smogon/pokemon-showdown-client`, which is AGPL, unless you accept those terms.
