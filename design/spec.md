# ARCHIEL interface spec

The mockup in `archiel-mockup.html` is the source of truth for this document. Numbers here and pixels
there must agree.

## Concept

An agent's terminal in your pocket: near-black graphite, one amber signal, hairline structure, code in
mono, everything else quiet. Bold move: the amber appears exactly three ways, as a fill on the one
active control, as a 6 px dot for live state, and as the streaming caret.

## Structure

Three screens, no navigation library, no floating action button.

| Screen | Dominant element | Everything else |
|---|---|---|
| Sessions | the list, even 64 px rows | title bar, live strip |
| Chat | the answer text, full measure | turn metadata, tool blocks, dock |
| Connection | the status card | two fields, one action |

Rules that hold everywhere:

- One separation device per surface: a 1 px hairline, or a fill step. Never both, never a shadow.
- The assistant never gets a bubble; the user turn is a filled panel with a 10 px radius, max 86%.
- Anything that changes with time (timestamps, counts, commands) is DM Mono with tabular figures.
- Status is never colour alone: the live dot always sits next to the word "connected" or "working".
- The send control becomes the stop control in the same position while a run is in flight.

## Theme

Dark only for now. One ground, one accent, and the values below; there is no light variant to keep in
step until the app earns one.

## Measurements

- Top bar 52 px, title Syne 700 at 23/28, one 40 px hit target per action.
- Live strip 24 px: 6 px amber dot, DM Mono 11/16, address and state.
- Session row 64 px: title Instrument 500 16/22 single line with ellipsis, preview 13.5/20 one line,
  time right in DM Mono 11/16.
- Chat turn: 16 px above a role change, 12 px between paragraphs of the same turn.
- Tool block: 5 px radius, 1 px hairline, command in DM Mono 13.5/20, output below a hairline.
- Dock: 1 px top hairline, 44 px field at a 16 px radius, 44 px circular action, hint line 11/16.
- Space ladder 4 / 8 / 12 / 16 / 24 / 32. Radii 5 / 10 / 16 / pill.

## States

| Screen | States drawn |
|---|---|
| Sessions | list, empty on a live server, loading, offline |
| Chat | history, streaming, tool call, cut short, overflow |
| Connection | form, connected |

Offline and cut-short say what failed, what to run, and offer the retry. There are no toasts.

## Motion

| Moment | Duration | Easing |
|---|---|---|
| press on any control | 120 ms | ease-out `.2,.8,.2,1` |
| caret blink | 500 ms loop, opacity only | linear |
| screen change | 200 ms fade, no travel | ease-out |
| stream reconnect | backoff 1 s, 2 s, 4 s, capped at 15 s | n/a |

Reduced motion: the caret becomes a static block, everything else holds still.

## Data

Real strings only: session titles from actual work, commands that ran, `2.0.32` for the server
version, `41 ms` for latency, `19:42` for a time, `groq/gpt-oss-120b` for the model. Turkish glyphs
are tested on the font board because the server runs on the user's terms, not because the UI is
Turkish: the interface ships in English.

## Non-goals

No Markdown rendering, no syntax highlighting, no permission prompts, no file attachments, no
notification history, no theme picker and no light theme. One accent, one danger, one mono.
