# The opencode surface, as the server reports it

Everything below was read off a running opencode v2.0.32 (`/openapi.json` plus live responses on
2026-10-18), not from memory. Paths are under `/api`. Auth is HTTP Basic: the user name is fixed and
the password is what `opencode serve` prints at startup.

## What a client can change

| Behaviour | Endpoint | Notes |
|---|---|---|
| Create a session | `POST /session` | Optional `title`, `agent`, `model`, `location.directory` |
| Send a prompt | `POST /session/{id}/prompt` | `{text}`; answers stream back over the event feed |
| Steer vs queue | `prompt` body `delivery` | `steer` (default) injects into a running turn, `queue` waits |
| Interrupt a run | `POST /session/{id}/interrupt` | Optional `resume` to pick the turn back up |
| Switch model | `POST /session/{id}/model` | `{model: {providerID, id, variant?}}` |
| Switch agent | `POST /session/{id}/agent` | `{agent}` |
| Run a slash command | `POST /session/{id}/command` | `{name, text}` |
| Answer a permission ask | `POST /session/{id}/permission/{requestID}/reply` | `{decision: once\|always\|reject, message?}` |
| Compact the history | `POST /session/{id}/compact` | |
| Fork a session | `POST /session/{id}/fork` | |
| Revert / stage revert | `POST /session/{id}/revert`, `/revert/stage`, `/revert/commit` | |
| Delete a session | `DELETE /session/{id}` | |
| Rename, move | `PATCH /session/{id}`, `POST /session/{id}/move` | |
| Attach files | `prompt` and `command` bodies | `files: [{uri, name}]` |
| Attach skills, agents | same bodies | `skills`, `agents` arrays |
| Answer a form | `POST /session/{id}/form/{formID}/reply` | The `question` tool |

## What a client can read

| Resource | Endpoint | Shape |
|---|---|---|
| Server | `GET /info` | `{version, pid, urls, paths}` |
| Agents | `GET /agent` | `[{id, name, mode, description}]` |
| Models | `GET /model` | 133 entries on this box, `{providerID, modelID, name, cost}` |
| Slash commands | `GET /command` | `[{name, description}]` |
| Skills | `GET /skill` | 16 on this box |
| Providers | `GET /provider` | |
| MCP servers | `GET /mcp` | `[{name, status}]` |
| Sessions | `GET /session` | Cursor paginated, `limit`, `search`, `order` |
| Messages | `GET /session/{id}/message` | `limit`, `order`, `type` |
| Pending permissions | `GET /session/{id}/permission`, `GET /permission/request` | |
| Saved approvals | `GET /permission/saved` | Durable, project scoped `allow` rules |
| Session usage | `GET /session/{id}/context` | |
| Session diff | `GET /session/{id}/diff` | |
| Files | `GET /fs/list`, `/fs/find`, `/fs/read/*` | Server side filesystem |
| Shell | `GET /shell`, `/shell/{id}/output` | Runs on the server, not the phone |
| PTY | `GET /pty`, `POST /pty/{id}/connect` | WebSocket terminal |
| Web search | `POST /websearch` | |
| VCS | `GET /vcs`, `/vcs/status`, `/vcs/diff`, `/vcs/branch` | |
| Config | `GET /config` | |

## Events

`GET /event` is a server wide SSE feed: every session and every location arrives on it, so a client
filters by `sessionID`. The names ARCHIEL acts on today:

| Event | Meaning |
|---|---|
| `session.text.delta` | Assistant text, with `assistantMessageID` and `ordinal` |
| `session.reasoning.delta` / `.ended` | Thinking trace, hidden in the app |
| `session.execution.started` / `.succeeded` | A run began or finished |
| `session.step.started` / `.streamed` / `.ended` | A step inside a run |
| `session.inbox.enqueued` / `.delivered` | Prompt queueing |
| `session.usage.updated` | Token and cost counters |

There is no replay: a reconnect starts from `server.connected` and the client reloads messages.

## Built in agents

`Build` (primary, runs tools), `Plan` (primary, read only, no edits), `General` and `Explore`
(subagents), plus the internal `Title`, `Summary` and `Compaction` agents.

## Slash commands

Commands come from `.opencode/commands/*.md` and `commands` in the config; there are no hardcoded
ones. On this machine: `init`, `review`, `ponytail`, `ponytail-audit`, `ponytail-debt`,
`ponytail-gain`, `ponytail-help`, `ponytail-review`. A template expands `$ARGUMENTS` and `$1..$n`, may
carry `agent`, `model` and `subagent` frontmatter, and may run shell through `!` backticks.

## Permissions

A rule is `{action, resource, effect}` where effect is `allow`, `deny` or `ask`; the last match wins
and an unmatched rule asks. Actions: `read`, `edit`, `glob`, `grep`, `shell`, `subagent`, `skill`,
`question`, `webfetch`, `websearch`, `external_directory`, plus `server_tool` for MCP tools. When a
run hits `ask`, the client sees a `Permission.Request` (`id`, `action`, `resources`, `save`,
`message`) and answers `once`, `always` or `reject`; `always` writes a durable project rule.

**This is why the app needs the permission card:** without it a run that hits `ask` waits forever.

## The TUI slash palette is not /api/command

The official TUI keeps a second command set that is never registered on the server: `/pair`
(pairing dialog for other devices), `/restart` (restarts the local service), `/themes`, `/status`,
`/debug`, `/clear`, `/update` and friends are TUI-only dialogs, so they never show up in
`/api/command`. The session-level ones have real HTTP calls and ARCHIEL serves them itself as
built-in dock commands: `/compact` (POST compact), `/fork` (POST fork, returns the new session),
`/undo` (interrupt + revert/stage on the last user message), `/redo` (DELETE revert),
`/rename <title>` (PATCH session), `/reload` (POST location/reload), `/new` (POST session) —
the `/pair`, `/restart`, `/themes`, `/status` ones stay TUI-only on purpose.

## What ARCHIEL does not use yet

PTY terminals, voice sessions, worktrees, VCS actions, plugin RPC, credentials and integrations,
forms beyond a plain question, and file attachments: the `prompt` body only takes a `uri`, and the
opencode server usually runs in Termux on the same phone, where an Android `content://` URI is not
readable without a copy into shared storage first.