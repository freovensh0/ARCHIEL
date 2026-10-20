# ARCHIEL

An Android client for an [opencode](https://opencode.ai) server. Kotlin + Jetpack Compose, talking to
the opencode HTTP API (`/api/*`) with an SSE stream for live answers. The server owns the sessions and
runs the agent; the app owns the connection and the reading surface.

## Design

Concept: *an engineer's panel — graphite ground, one amber signal, flat surfaces, measured type.*

Grounded in the `spec-mockup-design` skill and the `lunanoir21/desktop-widget-control` palette
(`#11120e` graphite, `#e0a458` amber). The tokens are generated, not hand-picked:

```sh
python3 scripts/palette.py --from-hex "#e0a458" --mood crisp --scheme mono --css design/palette.css
python3 scripts/scales.py --target mobile --density normal --css design/scales.css
```

Dark only for now, and the values are structural rather than picked: a ground, a text colour, one
accent, and two mixed from them, the way `lunanoir21/tally-omarchy` does it.

| token | value |
|---|---|
| ground / surface / hairline | `#0F0F10` `#17171A` `#26262A` |
| text / secondary / faint | `#ECECEC` `#A8A8AD` `#5D5D63` |
| accent fill (the only signal) | `#E0A458`, ink `#14110C` on top |
| danger / ok | `#DC615A` `#74BF85` |

Contrast on the ground: text 16.6:1, secondary 6.8:1, accent 7.8:1, danger 5.4:1.

- Type: **Instrument Sans** (body), **Syne** (titles), **DM Mono** (code and tool output) — the
  reference project's own fonts, OFL licensed (`licenses/`).
- A 4 px base scale: 4 / 8 / 12 / 16 / 24 / 32. Radii 5 / 10 / 16.
- Separation is a 1 px hairline, never a shadow, a gradient or a glow. No emoji.
- The assistant never gets a bubble; only the user turn is filled. The send control becomes the stop
  control in the same position while a run is in flight.

The mockup these numbers come from is `design/archiel-mockup.html`: open it in a browser, drag to
pan, ctrl+wheel to zoom, `F` to focus a board, `0` to fit. `design/spec.md` holds the measurements.

## Server

The app asks for an address and a password, both changeable in Settings.

```sh
# desktop — the phone connects over the same network
opencode serve --hostname 0.0.0.0 --port 4096
# the "server password ..." line it prints is the password

# or pin it
OPENCODE_SERVER_PASSWORD=... opencode serve --hostname 0.0.0.0 --port 4096
```

| Where | Address |
|---|---|
| Phone, server in Termux | `http://127.0.0.1:4096` |
| Desktop on the same Wi-Fi | `http://192.168.x.x:4096` |
| Android emulator | `http://10.0.2.2:4096` (default) |

## Build

The GitHub Actions workflow runs the unit tests, builds `assembleDebug` and uploads the APK:
**Actions → build → Artifacts → archiel-debug**. It also opens in Android Studio as is (JDK 17+,
Gradle wrapper included).

```sh
gh run download -n archiel-debug -D /tmp/archiel
adb install -r /tmp/archiel/app-debug.apk
```

## What it does

- Lists the sessions on the server, with the last message of each as a one line preview.
- Sends a message and renders the answer as it streams; the send button stops a run in place.
- Shows tool calls with the command and the raw output, read only.
- Keeps the stream alive with exponential backoff when the phone suspends the process.
- The connection screen reports what the server actually says: version, latency, session count.

## The assistant

A foreground service that turns the app into an assistant:

- **Screen capture.** The dock's *Screen* item (or the *Capture* action on the assistant
  notification) snaps the screen of whatever is in front and drops the picture into the chat as an
  attachment. Android asks once for the screen capture; the share chip at the top of the screen
  turns it off, and the service stops with it.
- **Run alerts.** The service keeps the event stream open, so the phone says when the run of the
  session it last talked to has finished, even with the app in the background. Tapping the
  notification opens that session.

Both live behind *Connection → Assistant*. The service is not sticky: the screen capture consent
cannot be replayed, so the assistant is turned on again by hand after the process dies.

## Layout

```
app/src/main/java/dev/scdeal/archiel/
  OpenCodeClient.kt   HTTP + SSE client, serializable models
  ChatViewModel.kt    session list, stream state, errors
  MainActivity.kt     three screens: sessions / chat / connection
  Theme.kt            the whole design system, dark only
  Cfg.kt              server address + password (SharedPreferences)
```

## Version 1 limits

Plain text (no Markdown or syntax colouring), `reasoning` parts hidden, tool output is read-only, no
permission prompts and no file attachments. The password is stored in plain SharedPreferences: anyone
with the device has the server.
