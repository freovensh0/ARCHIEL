# Working on this project

ARCHIEL is an Android client for an [opencode](https://opencode.ai) server: Kotlin + Jetpack Compose,
HTTP + SSE for streaming. The server owns the sessions and the agent; the app owns the connection and
the reading surface. This file is the contract for anyone changing things here, humans and agents alike.

## Language

The repository is **English**: code, identifiers, comments, docs, commit messages, CI step names and
UI strings. Add a Turkish string only when a task explicitly asks for one, and put it next to the
English one with a comment naming the locale.

## Shape of the code

- `OpenCodeClient.kt` is the only file that speaks HTTP. Everything above it works with `Session`,
  `Message` and `Part`. When the server adds a field, it lands in the `@Serializable` models, not in
  the UI.
- The event stream (`GET /api/event`) is server-wide: every session and every directory arrives on it.
  `ChatViewModel` filters by `sessionID` before touching state, and that filter is the only reason the
  UI does not cross sessions.
- The server sends `session.execution.succeeded`, not a final message. Treat it as the signal to reload
  messages, and keep the streamed text only as an optimistic preview. `session.reasoning.delta` feeds
  the live "thinking" strip; both previews are dropped when the reload lands. Tool calls stream on the
  wire too: `session.tool.*` events keyed by the call id build the live tool block, cleared and replaced
  by the message's finished tool part on reload.
- `ChatViewModel` is an `AndroidViewModel` and builds its client from `Cfg`, so changing the server
  password means recreating it. `App()` keys the view model on the config for that reason.
- No dependency injection, no database, no navigation library. Three screens and a switch are enough;
  add structure when there is a second caller, not before.

## Design

The visual language is defined once in `Theme.kt` and nowhere else.

- Never hardcode a colour, a corner radius, a font size or a duration in a screen. If a value is not in
  `Theme.kt`, it goes there first.
- One accent (`primary`), one danger (`error`). Status is never colour alone: pair it with a label, an
  icon or a shape.
- Surfaces separate with a 1 px hairline (`outline`) or a fill step. Not both, and never a shadow,
  a gradient or a glow on top.
- No emoji. Icons come from `material-icons-extended`, outline variants, one stroke weight.
- Type comes from the typography scale; weights carry hierarchy (400 body, 500-600 labels, Syne only
  for the screen title).
- Every screen shows its real states: empty, loading, error, busy. Empty means one honest sentence,
  not a grey box.

## Before every commit

1. GitHub Actions is the build machine: it runs `./gradlew testDebugUnitTest assembleDebug` on every
   push and pull request and uploads the debug APK. The development machine has no Android SDK and
   needs none — install nothing locally, read the run with `gh run list` / `gh run watch` instead.
   Add a test when you touch `SseBuffer` or the serializable models; the stream framing is the one
   place a silent break is invisible.
2. UI strings are English and say what happened and what to do next. No "Oops", no exclamation marks.
3. Look at it. Open the screen on a phone or emulator, in both light and dark, with a long session
   title and a long message.
4. The commit follows the convention below and lands **without** pushing.

## Commits

Conventional Commits, imperative mood, English, one line of intent:

```
feat(chat): stream assistant text from the SSE event stream
fix(auth): send the Basic header on the event stream too
docs: describe how the server password is obtained
```

`feat` `fix` `docs` `refactor` `test` `ci` `chore`. A scope is the area, not the file.

**Do not push after every commit.** Commits are local until the user asks for a push or the work
reaches a state worth publishing. When it does, push once with a clean history, not ten times.

## Conventions

- Comments explain *why* — a server quirk, a decision, a trap — not what the next line does. If a
  comment restates the code, delete it.
- Keep `Message`/`Part` models tolerant: the server adds fields, so `ignoreUnknownKeys` stays and new
  fields are nullable with defaults.
- Errors read as sentences the user can act on: what failed, and what to change.
