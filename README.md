# ARCHIEL

An Android client for an [opencode](https://opencode.ai) server. Kotlin + Jetpack Compose, talking to
the opencode HTTP API (`/api/*`) with an SSE stream for live answers. The server owns the sessions and
runs the agent; the app owns the connection and the reading surface.

## Design

Concept: *an engineer's panel — graphite ground, one amber signal, flat surfaces, measured type.*

Grounded in the `spec-mockup-design` skill and the `lunanoir21/desktop-widget-control` palette
(`#11120e` graphite, `#e0a458` amber). The tokens are generated, not hand-picked:

```sh
python3 scripts/palette.py --hue 75 --mood crisp --scheme mono --name amber --css design/palette.css
python3 scripts/scales.py --target mobile --density normal --css design/scales.css
```

| | dark | light |
|---|---|---|
| ground / surface / line | `#100F0E` `#161513` `#2A2928` | `#F5F3F1` `#FBF9F7` `#D9D7D5` |
| text / secondary | `#F0EEEC` `#A09E9B` | `#131110` `#6B6866` |
| accent (the only signal) | `#DB9E3D` | `#7E5403` |

- Type: **Instrument Sans** (body), **Syne** (titles), **DM Mono** (code and tool output) — the
  reference project's own fonts, OFL licensed (`licenses/`).
- A 4 px base scale: 4 / 8 / 12 / 16 / 24 / 32. Radii 5 / 10 / 16.
- Separation is a 1 px hairline, never a shadow, a gradient or a glow. No emoji.

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

## Layout

```
app/src/main/java/dev/scdeal/archiel/
  OpenCodeClient.kt   HTTP + SSE client, serializable models
  ChatViewModel.kt    session list, stream state, errors
  MainActivity.kt     three screens: sessions / chat / connection
  Theme.kt            the whole design system
  Cfg.kt              server address + password (SharedPreferences)
```

## Version 1 limits

Plain text (no Markdown or syntax colouring), `reasoning` parts hidden, tool output is read-only, no
permission prompts and no file attachments. The password is stored in plain SharedPreferences: anyone
with the device has the server.
