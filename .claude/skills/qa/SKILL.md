---
name: qa
description: Run ThorShowdown's emulator workflow tests, review the screenshots and logs against the scenario specs, add a review to the HTML report and publish it. Use when asked to run QA, workflow tests, emulator tests, or to review a qa-runs report.
---

# QA: emulator workflow tests

Runs the scenarios in `qa/scenarios/` on the emulator, judges what code can't (layout, rendering,
log health), and publishes the report. Design: `context/design/workflow_tests.md`.

## Rules
- **Emulator only** (`emulator-5554`). Never target the Thor; the runner refuses non-emulator serials.
- **Never edit a test, spec, fixture or robot to make something pass.** Report the mismatch and say
  whether you think the app, the test or the spec is wrong, and why. The user decides.
- Code assertions and the runner's log checks are final. You add judgement; you don't overrule them.
- The report is written by `:tools:qaReport`, never by hand. You only write `review.json`.

## Steps

1. **Check the emulator** is running with its secondary display:
   `adb -s emulator-5554 shell cmd display get-displays` lists two displays (the runner adds the second if missing).
2. **Run** (all scenarios, or the ones asked for):
   `.\gradlew.bat :tools:qaRun` or `.\gradlew.bat :tools:qaRun -Pscenarios=Login,BattleTurn`.
   Set `JAVA_HOME` as in CLAUDE.md if Gradle can't find Java. The run folder is the last line:
   `qa-runs/<yyyyMMdd-HHmmss>/`.
3. **Read** `report.json` in the run folder: status, failure, log failures, spec problems and
   checkpoint screenshot paths for each scenario.
4. **Review each checkpoint**: open both screenshots (`<Scenario>/checkpoints/NN-Name-{top,bottom}.png`)
   and compare them with that checkpoint's text in `report.json` (`spec.checkpoints`). Judge each screen:
   - `Pass`: everything the spec names is there and nothing looks broken (clipping, overlap, wrong text,
     unreadable contrast, a stray dialog, an error message).
   - `Fail`: something the spec names is missing or wrong, or the screen is visibly broken. Say exactly what.
   - `NeedsHuman`: you can't tell from the image (for example the top WebView depends on the live site).
   Unreached checkpoints of a failed scenario need no verdict; explain the failure instead.
5. **Review the logs**: the `logs.errors` lines in `report.json`, and `logcat.txt` for the app's process
   (pid from the `TestRunner` lines) around any failure. Note anything that isn't emulator noise
   (OpenGLRenderer, EGL, Gralloc, ziparchive and hidden-API warnings are noise).
6. **For each failed scenario**, find the cause from `instrument.txt`, the last screenshots, the video
   if needed, and the code. Put it in that scenario's `notes`: what happened, where in the code, and
   whether the app or the test is at fault.
7. **Write `review.json`** in the run folder:
   ```json
   {
     "reviewer": "qa-skill",
     "summary": "One or two sentences: the overall result and anything the user must look at.",
     "scenarios": {
       "<Scenario>": {
         "notes": "Optional: cause of a failure or anything notable.",
         "logs": { "verdict": "Pass", "reason": "Only emulator renderer noise." },
         "checkpoints": {
           "<Checkpoint>": {
             "top": { "verdict": "Pass", "reason": "..." },
             "bottom": { "verdict": "Fail", "reason": "..." }
           }
         }
       }
     }
   }
   ```
   Verdicts are `Pass`, `Fail` or `NeedsHuman`. Reasons are short and specific.
8. **Re-render**: `.\gradlew.bat :tools:qaReport -PrunDir=qa-runs/<run>`.
9. **Privacy check before publishing**: search every `logcat.txt` for the emulator's real Showdown
   account name, the user's email and `sid=` values other than `<redacted>`. The canary values are
   fake and fine. Read the error lines the report embeds and every `instrument.txt`: everything you
   publish must have been seen. If a real identifier appears anywhere, stop and ask the user.
10. **Publish** `report.page.html` with the Artifact tool, with `root` set to the run folder, `icon`
    "checklist", and `files` listing each `<Scenario>/checkpoints/*.png` and `<Scenario>/instrument.txt`
    at their relative paths. Logcat files and videos stay local (their links in the published page
    don't resolve; say so). Re-publishing a later review of the same run reuses that file path.
11. **Reply** with the link and a short summary: pass/fail counts, each failure's cause in one line,
    any `Fail` or `NeedsHuman` screenshots, and questions for the user.
