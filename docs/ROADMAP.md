# ROADMAP

Where ARCHIEL goes next, one phase per release. Each feature line is the
smallest version that makes the feature honest; grow it only when a second
caller appears. Build machine stays: GitHub Actions, `testDebugUnitTest
assembleDebug`, APK artifact per run.

Surveyed clients (2026-10): [oc-remote](https://github.com/crim50n/oc-remote),
[opencode-mobile (RN)](https://github.com/dzianisv/opencode-mobile),
[OConnector](https://github.com/fangzx2001/OpencodeConnector4Android),
[opencode_android_client](https://github.com/grapeot/opencode_android_client),
[doza62/opencode-mobile](https://github.com/doza62/opencode-mobile) (a push
plugin), [opencode-mobile-client
(webview)](https://github.com/bmpenuelas/opencode-mobile-client),
[opencode-termux](https://github.com/stguysoft/opencode-termux) (local
runtime).

## Done

- Connection over HTTP + Basic auth, event-stream filtering by session
- Sessions, models, streaming answer with live reasoning strip and tool blocks
- GFM markdown: fences, headings, nested lists, tasks, quotes, tables,
  rules, inline code, bold, italics, strikes, clickable links
- Chat hygiene: stop button while generating (`POST /session/{id}/interrupt`)
  with a disabled state when idle, queued label on an outgoing prompt until the
  run confirms it, turn errors as a sentence with Retry, tool cards expandable
  with a copy button for command + output
- The agent's artifacts: patch tool diffs with a per-file header and copy,
  todo tool input as a compact checked panel, pending permissions and
  `question` forms as ordered inline cards (answer, approve, deny), subagent
  sessions listed read-only under their parent

## Phase 3 — session management

- Rename, fork, compact, delete from a session menu. Share links are gone from the
  server in v2.0.32 (`session.share` is deprecated), so they are not in the menu.
- Search bar over titles; time grouping Today / This week / Older.
- Draft persistence per session (input text survives navigation and death).
- Export the session as a JSON file via the share sheet.

## Phase 4 — context and cost

- Token and cost display under the model picker (from usage parts/totals).
- Context window bar: input/output/reasoning/cache with remaining capacity.
- Model picker upgrades: variants, favorites, hide models, provider icons.
- Agent switcher preserving explicit Plan/Build choice.

## Phase 5 — device integration

- Completion notification: foreground service keeps the event stream alive
  and notifies when `session.execution.succeeded` arrives.
- Credentials move to EncryptedSharedPreferences behind a biometric prompt.
- Image attachments with the Android photo picker and a pinch-zoom viewer.
- `@file` mention autocomplete backed by the server's path search.
- Share-in intent: send supported files from other apps into a new session.

## Phase 6 — connection depth

- Multiple server profiles with connect/disconnect and status dots.
- HTTPS toggle with optional self-signed trust.
- Cached sessions readable while disconnected, reconnect banner, backoff.
- Multi-project discovery (sessions across all server directories).

## Phase 7 — polish

- AMOLED theme, Material You dynamic colour, tablet two-pane chat.
- Locales: English source plus Turkish.
- In-app update check against GitHub releases with SHA-256 verification.
- Diagnostics export: privacy-sanitised log share from Settings.
- Syntax highlighting inside code fences (optional dependency, last).

## Phase 8 — maybe never

- Terminal PTY over WebSocket (full TUI in a terminal tab). Heavy, rare on
  phones; revisit only if server-side PTY endpoints prove common.
- On-device agent runtime via Termux. Contradicts ARCHIEL's remote-client
  identity; skip.

## Not doing, on record

- WebView wrapper of the web UI (opencode-mobile-client-style): ARCHIEL is
  the reading surface, not an embedded browser.
- iOS build.
- Settings sync through Gist/WebDAV: single-user app, last phase if asked.
