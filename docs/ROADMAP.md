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
- Session management: rename / fork / compact / delete from a session menu,
  title search over the server with Today / This week / Older grouping,
  per-session drafts kept on disk, and the server's JSON export handed to the
  share sheet
- Context and cost: session token totals and cost from the usage event, a
  context bar against the model's window, model variants, favourites and
  hidden models, the agent list grouped by mode, and a default model and agent
  for new sessions kept on the device
- Attachments: an image or a file picked on the phone travels as a data URL
  the server decodes inline, shows up in the transcript as a thumbnail (open
  full screen, pinch to zoom) or a file card; a file the server already has is
  attached by its `file://` path through an `@file` mention, and a share from
  another app lands in the dock
- Delegation: an `@agent` mention in the dock names a subagent, and the prompt
  carries it so the agent can hand the work over; a subagent's own session
  opens read-only under its parent
- The server's own tools: a shell runner that executes one command and pages
  its output, and a terminal that runs a program over the PTY socket with a
  line input
- Several servers: the connection screen keeps a list of profiles (a name, an
  address, a password), the active one carries the status dot, and switching
  one remakes the connection
- The assistant: a foreground service that holds the screen capture and the
  event stream, so the dock's Screen item (or the notification action) snaps the
  screen from any app, and the phone says when the watched run has finished

## Phase 7 — shells and terminals

- The terminal is a program over the server's PTY socket, with a line input and
  no ANSI rendering; the shell runner executes one command and pages its output.

## Phase 8 — device integration

- Credentials move to EncryptedSharedPreferences behind a biometric prompt.
- The agent reaches the phone: the phone serves a small endpoint on the loopback
  address, the prompt names it, and a reminder the agent posts is scheduled
  locally and fires when it is due.

## Phase 9 — connection depth

- HTTPS toggle with optional self-signed trust.
- Cached sessions readable while disconnected, reconnect banner, backoff.
- Multi-project discovery (sessions across all server directories).

## Phase 10 — polish

- AMOLED theme, Material You dynamic colour, tablet two-pane chat.
- Locales: English source plus Turkish.
- In-app update check against GitHub releases with SHA-256 verification.
- Diagnostics export: privacy-sanitised log share from Settings.
- Syntax highlighting inside code fences (optional dependency, last).

## Phase 11 — maybe never

- On-device agent runtime via Termux. Contradicts ARCHIEL's remote-client
  identity; skip.

## Not doing, on record

- WebView wrapper of the web UI (opencode-mobile-client-style): ARCHIEL is
  the reading surface, not an embedded browser.
- Session share links: removed from the server in v2.0.32 (`session.share` is
  deprecated), so the menu has no share/unshare.
- iOS build.
- Settings sync through Gist/WebDAV: single-user app, last phase if asked.
