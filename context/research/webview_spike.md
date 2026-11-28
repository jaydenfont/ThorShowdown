# WebView Spike (#4)

Findings from a throwaway spike (never merged) run on the emulator on 2026-11-22, in a real
gen9randombattle as a registered test account. The page was inspected and styled live over
WebView remote debugging (Chrome DevTools Protocol through `adb forward`).

## Questions and answers

| # | Question | Finding |
|---|---|---|
| 1 | Does handing the native `sid` cookie to the WebView log it in? | **Yes.** `CookieManager.setCookie("https://pokemonshowdown.com", "sid=…; Domain=.pokemonshowdown.com; Path=/; Secure")` before the first load. The page shows the account and our side's hidden info (item, ability, exact stats, Tera type). Needs a **registered** name: `/api/upkeep` gives unregistered names an empty assertion. |
| 2 | Does `https://play.pokemonshowdown.com/<roomId>` open the battle directly? | **Yes**, about 1.3 s to `onPageFinished`. Every load is a new connection that starts as a Guest and logs itself in again within about 2 s. |
| 3 | Can CSS hide the controls but keep stats tooltips? | **Yes.** See the CSS below. Tooltips need a **long press** on touch (a short tap does nothing) and are positioned correctly with the battle scaled. |
| 4 | What happens when the native client leaves? | Navigating the WebView away (to another URL) drops its connection, so it doesn't linger in the room. |
| 5 | Double input | With `.battle-controls` set to `display: none` no move/switch button can be pressed. Forcing a second `/choose` through the WebView wasn't tried (it would mean meddling in a real player's battle). |

## Other findings

- **Layout:** at density 2.5 the 1920×1032 px top area is a 768×413 CSS px viewport. The battle is a
  fixed 640×360 box (`.battle`), so it scales by `min(W / 640, H / 360)` ≈ 1.15. The viewport briefly
  reported 1024 px wide during a re-layout, so the scale has to be recomputed on `resize`, not fixed once.
- **Duplicate popups:** the WebView's connection gets the same `|updatesearch|` and `|popup|` as the
  native one (Showdown sends to every connection of the user), and Showdown draws popups over the page.
- **Page structure** (battle room, `small-layout`): `#header` (logo, `.tabbar.maintabbar`, `.userbar`),
  `.ps-room` > `.battle` (`.innerbattle`, `.tooltips` with `.has-tooltip` areas per Pokémon),
  `.battle-controls`, `.battle-chat-toggle`, `.foehint`, `#adBanner`.
- **Search dropping (affects #10):** the server popped up "Sorry, your opponent … went offline before
  your battle could start." while we were `Searching`, and sent no `|updatesearch|` afterwards.

## CSS and scaling that worked

```js
var W = innerWidth, H = innerHeight, s = Math.min(W / 640, H / 360);
```

```css
#header, .tabbar, .battle-controls, .battle-chat-toggle, .battle-log, .battle-log-add,
.chat-log-add, .foehint, #adBanner { display: none !important; }
html, body { background: #000 !important; overflow: hidden !important; }
.ps-room { position: fixed !important; inset: 0 !important; background: #000 !important; overflow: hidden !important; }
.ps-room .battle { position: absolute !important; top: 0 !important; left: <(W - 640 * s) / 2>px !important;
  margin: 0 !important; transform-origin: 0 0 !important; transform: scale(<s>) !important; }
```

## How the page was inspected

```text
adb forward tcp:<free port> localabstract:webview_devtools_remote_<app pid>
GET http://127.0.0.1:<port>/json            -> webSocketDebuggerUrl
Runtime.evaluate over that WebSocket         -> run JS / inject CSS in the page
```
Needs `WebView.setWebContentsDebuggingEnabled(true)` (debug builds only).

## Found later, in the #4 manual test

- **`onPageFinished` is unusable for injection:** in one real battle the page's load event came
  ~52 s after navigation (ad scripts), leaving Showdown's move buttons visible and usable until
  then. The spike's 1.3 s was luck. Fixed by `androidx.webkit` document-start scripts.
- **Console → logcat:** the web client logs the whole protocol, including our `|request|` (team),
  to the console, which the WebView copies to logcat. Fixed by swallowing console messages.
- **User list:** `ul.battle-userlist` ("2 users") floats over the top of the battle; hidden too.
- **Private room IDs:** some battle room IDs carry a suffix (`battle-…-2718098498-4q0uj…`); the
  room URL works unchanged.
