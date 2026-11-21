# Context

Project knowledge for humans and Claude. Code lives in `app/`; everything about
*why* and *what next* lives here.

| Folder | What goes here | Files |
|---|---|---|
| `design/` | The current design. Kept up to date with the code. | [architecture.md](design/architecture.md): layers and responsibilities (source of truth)<br>[battle_sequence.txt](design/battle_sequence.txt): battle flow, sequencediagram.org format |
| `design/images/` | Diagrams referenced by design docs. | `app_architecture.png`: original whiteboard architecture<br>`battle_sequence_draft.png`: first draft of the battle sequence |
| `research/` | Findings about external systems. Reference only; may go stale. | [showdown_api_notes.md](research/showdown_api_notes.md): Showdown protocol, from the pokemon-slowdown CLI client |
| `planning/` | Work tracking. | [workplan.md](planning/workplan.md): P0 build order, dependencies, GitHub issue links |

## Conventions
- New design decisions go in `design/architecture.md`, not in new files, unless
  the topic is big enough for its own doc (then link it from architecture.md).
- Images go in `design/images/` and are referenced with relative paths.
- Research docs say where their information came from and mark anything inferred.
