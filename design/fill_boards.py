"""ARCHIEL mockup: fills the generated canvas with real boards.

Run once after scripts/new-canvas.py. Tokens come from design/palette.css and
design/scales.css; fonts are the TTFs the app ships, inlined so the canvas stays
self-contained.
"""
import base64
import io
import os
import re

ROOT = os.path.dirname(os.path.abspath(__file__))
CANVAS = os.path.join(ROOT, "archiel-mockup.html")
FONTS = os.path.join(ROOT, "..", "app", "src", "main", "res", "font")


def face(family, filename, weight="100 900"):
    with open(os.path.join(FONTS, filename), "rb") as fh:
        b64 = base64.b64encode(fh.read()).decode()
    return (
        "@font-face{font-family:%s;src:url(data:font/ttf;base64,%s) format('truetype');"
        "font-weight:%s;font-display:swap}" % (family, b64, weight)
    )


PALETTE = """
:root{
  --bg:#f6f4ef; --surface:#fffdf8; --line:#ddd8cc; --text:#1b1a17; --muted:#6b6862; --faint:#a49e92;
  --accent:#7d5206; --accent-fill:#e0a458; --on_accent:#14110c; --accent_soft:#f6e6c8;
  --danger:#a3322c; --ok:#3d6b45;
  --canvas:#e4e0d6; --chrome:#6b6862;
}
:root[data-theme="dark"]{
  --bg:#0f0f10; --surface:#17171a; --line:#26262a; --text:#ececec; --muted:#a8a8ad; --faint:#5d5d63;
  --accent:#e0a458; --accent-fill:#e0a458; --on_accent:#14110c; --accent_soft:#2a2013;
  --danger:#dc615a; --ok:#74bf85;
}
"""

SCALES = io.open(os.path.join(ROOT, "scales.css"), encoding="utf8").read()
SCALES = re.sub(r"/\*.*?\*/", "", SCALES, flags=re.S).strip()

FONTS_CSS = "\n".join([
    face("Instrument", "instrument_sans.ttf"),
    face("Syne", "syne.ttf"),
    face("DMMono", "dm_mono.ttf", "400"),
])

APP_CSS = """
:root{
  --font-ui:"Instrument",system-ui,sans-serif;
  --font-display:"Syne","Instrument",serif;
  --font-mono:"DMMono",ui-monospace,monospace;
}
.app{height:100%;display:flex;flex-direction:column;background:var(--bg);color:var(--text);
  font:400 var(--fs-s)/1.5 var(--font-ui);overflow:hidden}
.app.dark{background:var(--bg)}
.topbar{flex:0 0 auto;height:52px;display:flex;align-items:center;gap:var(--sp-2);
  padding:0 var(--sp-3) 0 var(--sp-4);border-bottom:1px solid var(--line)}
.topbar .title{font:700 var(--fs-l)/1.2 var(--font-display);letter-spacing:-.01em;
  white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0}
.topbar .sub{font:var(--fs-2xs)/1 var(--font-mono);color:var(--faint);letter-spacing:.04em}
.grow{flex:1}
.ib{width:40px;height:40px;display:grid;place-items:center;color:var(--muted);background:none;
  border:0;cursor:pointer;border-radius:var(--radius-s)}
.ib.acc{color:var(--accent)}
.ib svg{width:var(--icon-m);height:var(--icon-m);fill:none;stroke:currentColor;
  stroke-width:1.5;stroke-linecap:round;stroke-linejoin:round}
.scroll{flex:1;min-height:0;overflow:hidden}
.pad{padding:0 var(--sp-4)}
.hair{height:1px;background:var(--line)}

/* session rows */
.row{display:flex;gap:var(--sp-3);align-items:flex-start;padding:var(--sp-3) var(--sp-4);
  border-bottom:1px solid var(--line)}
.row .t{font:500 var(--fs-s)/1.35 var(--font-ui);white-space:nowrap;overflow:hidden;
  text-overflow:ellipsis}
.row .p{font:var(--fs-xs)/1.35 var(--font-ui);color:var(--muted);margin-top:2px;
  white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.row .when{flex:0 0 auto;margin-left:auto;font:400 var(--fs-2xs)/1 var(--font-mono);
  color:var(--faint);font-variant-numeric:tabular-nums;padding-top:3px}
.row.new .t{color:var(--accent)}

/* chat turns */
.turn{padding:var(--sp-3) var(--sp-4) 0}
.turn.role{padding-top:var(--sp-4)}
.turn.user{display:flex;justify-content:flex-end}
.bubble{max-width:86%;background:var(--surface);border-radius:var(--radius-m);
  padding:var(--sp-3) var(--sp-4);font:var(--fs-s)/1.5 var(--font-ui)}
.asst{font:var(--fs-s)/1.5 var(--font-ui)}
.asst p{margin:0 0 var(--sp-3)}
.asst p:last-child{margin-bottom:0}
.asst code{font:400 14px var(--font-mono);background:var(--surface);border:1px solid var(--line);
  border-radius:var(--radius-s);padding:1px 5px}
.stamp{font:var(--fs-2xs)/1 var(--font-mono);color:var(--faint);margin-top:var(--sp-2);
  font-variant-numeric:tabular-nums}
.stamp.r{text-align:left}
.tool{margin:0 var(--sp-4) var(--sp-3);border:1px solid var(--line);border-radius:var(--radius-s);
  background:var(--surface);padding:var(--sp-2) var(--sp-3)}
.tool .head{display:flex;align-items:center;gap:var(--sp-2);font:var(--fs-2xs)/1 var(--font-mono);
  color:var(--muted);letter-spacing:.04em}
.tool .cmd{font:var(--fs-xs)/1.45 var(--font-mono);margin-top:var(--sp-2);color:var(--text);
  white-space:pre-wrap;word-break:break-all}
.tool .out{font:var(--fs-xs)/1.45 var(--font-mono);color:var(--muted);margin-top:var(--sp-2);
  padding-top:var(--sp-2);border-top:1px solid var(--line);white-space:pre-wrap}
.dot{width:6px;height:6px;border-radius:999px;background:var(--accent-fill);flex:0 0 auto}
.dot.idle{background:var(--faint)}
.dot.bad{background:var(--danger)}
.caret{display:inline-block;width:8px;height:16px;background:var(--accent-fill);
  vertical-align:-3px;margin-left:2px}

/* composer */
.dock{flex:0 0 auto;border-top:1px solid var(--line);background:var(--bg)}
.composer{padding:var(--sp-2) var(--sp-3) var(--sp-1);display:flex;gap:var(--sp-2);align-items:flex-end}
.field{flex:1;display:flex;align-items:center;background:var(--surface);border:1px solid var(--line);
  border-radius:var(--radius-l);padding:var(--sp-3) var(--sp-4);min-height:44px;
  font:var(--fs-s)/1.4 var(--font-ui);color:var(--faint)}
.field.focus{border-color:var(--accent)}
.send{flex:0 0 auto;width:44px;height:44px;border-radius:var(--radius-pill);
  background:var(--accent-fill);color:var(--on_accent);display:grid;place-items:center;border:0}
.send.off{background:var(--surface);border:1px solid var(--line);color:var(--faint)}
.send svg{width:var(--icon-m);height:var(--icon-m);fill:none;stroke:currentColor;stroke-width:1.5;
  stroke-linecap:round;stroke-linejoin:round}
.stop{width:13px;height:13px;background:var(--on_accent);border-radius:2px}
.hint{display:flex;justify-content:space-between;font:var(--fs-2xs)/1 var(--font-mono);
  color:var(--faint);padding:3px var(--sp-1) 5px;letter-spacing:.02em;white-space:nowrap}

/* states */
.center{flex:1;display:grid;place-content:center;gap:var(--sp-2);padding:var(--sp-6);
  text-align:center}
.center .big{font:500 var(--fs-m)/1.4 var(--font-ui);color:var(--text)}
.center .small{font:var(--fs-xs)/1.5 var(--font-ui);color:var(--muted);max-width:30ch}
.center .btn{justify-self:center;width:200px}
.banner{display:flex;gap:var(--sp-2);align-items:center;padding:var(--sp-3) var(--sp-4);
  border-bottom:1px solid var(--line);background:var(--surface);font:var(--fs-xs)/1.4 var(--font-ui)}
.banner.bad{color:var(--text)}
.banner .act{margin-left:auto;color:var(--accent);font:500 var(--fs-xs)/1 var(--font-ui)}
.skel{display:flex;flex-direction:column;gap:var(--sp-3);padding:var(--sp-4)}
.sk{height:11px;border-radius:var(--radius-s);background:var(--surface);
  animation:none;opacity:.85}
.live{display:flex;align-items:center;gap:var(--sp-2);padding:6px var(--sp-4);
  font:var(--fs-2xs)/1 var(--font-mono);color:var(--muted);letter-spacing:.04em;
  border-bottom:1px solid var(--line)}
.pulse{width:6px;height:6px;border-radius:999px;background:var(--accent-fill)}

/* connection form */
.fgroup{padding:var(--sp-4) 0;border-bottom:1px solid var(--line)}
.flabel{font:var(--fs-2xs)/1 var(--font-mono);color:var(--faint);letter-spacing:.04em}
.fval{font:var(--fs-s)/1.4 var(--font-ui);margin-top:6px}
.fval.ph{color:var(--faint)}
.input{background:var(--surface);border:1px solid var(--line);border-radius:var(--radius-s);
  padding:var(--sp-3);font:400 var(--fs-s)/1.3 var(--font-ui);color:var(--text);margin-top:6px;
  display:flex;justify-content:space-between;align-items:center}
.input.focus{border-color:var(--accent)}
.input .caretmark{width:1px;height:18px;background:var(--accent)}
.btn{display:flex;align-items:center;justify-content:center;gap:var(--sp-2);width:100%;
  min-height:48px;border-radius:var(--radius-m);border:1px solid transparent;
  background:var(--accent-fill);color:var(--on_accent);font:500 var(--fs-s)/1 var(--font-ui)}
.card{border:1px solid var(--line);border-radius:var(--radius-m);padding:var(--sp-3) var(--sp-4);
  background:var(--surface)}
.kv{display:flex;justify-content:space-between;gap:var(--sp-3);padding:7px 0;
  font:var(--fs-xs)/1.3 var(--font-ui)}
.kv + .kv{border-top:1px solid var(--line)}
.kv .k{color:var(--muted)}
.kv .v{font-family:var(--font-mono);font-variant-numeric:tabular-nums}
"""

ICON = {
    "plus": '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
    "gear": '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/>'
            '<path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 9 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.6 9a1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1z"/></svg>',
    "back": '<svg viewBox="0 0 24 24"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>',
    "send": '<svg viewBox="0 0 24 24"><path d="M4.5 12h15M13 5.5l6.5 6.5-6.5 6.5"/></svg>',
    "refresh": '<svg viewBox="0 0 24 24"><path d="M20 11a8 8 0 1 0-1.5 6"/><path d="M20 5v6h-6"/></svg>',
}


def topbar(left, right, sub=""):
    return (
        '<div class="topbar">%s<div class="grow"></div>%s</div>'
        % (left, right)
    )


def sessions_body(rows):
    return '<div class="scroll">%s</div>' % "".join(rows)


SESSION_ROWS = [
    ("Fix the SSE reconnect when the phone sleeps", "opencode serve --hostname 0.0.0.0 …", "19:42"),
    ("Rewrite the palette generator in Rust", "Ran `rg --files` and read 6 matches, then wrote a new …", "18:07"),
    ("Why does the tray icon stay hidden on Wayland", "Checked the compositor layer shell protocol; the widget needs …", "Dün"),
    ("Rename every string to sentence case", "Done. 41 strings moved, one left in the tour because it names a …", "12 Eki"),
    ("Sweep the shell scripts for unbound variables", "Added `set -u` to bind.sh and install.sh; both needed a default …", "11 Eki"),
    ("Draft the release notes for 0.2.1", "Two entries were stale: the cover art and the local-only artwork note.", "9 Eki"),
]


def session_rows(new=True, limit=None):
    out = []
    if new:
        out.append(
            '<div class="row new"><div style="flex:1">'
            '<div class="t">New session</div>'
            '<div class="p">Starts a session on the connected server</div></div>'
            '<span class="ib acc">%s</span></div>' % ICON["plus"]
        )
    for t, p, w in (SESSION_ROWS[:limit] if limit else SESSION_ROWS):
        out.append(
            '<div class="row"><div style="flex:1;min-width:0">'
            '<div class="t">%s</div><div class="p">%s</div></div>'
            '<div class="when">%s</div></div>' % (t, p, w)
        )
    return "".join(out)


def chat_composer(busy=False, focus=False, text=""):
    body = (
        '<div class="field%s"><span style="color:var(--text)">%s</span>'
        '<span class="caretmark" style="margin-left:auto"></span></div>'
        % (" focus" if focus else "", text or "What should we build?")
    )
    if busy:
        body += '<div class="send"><span class="stop"></span></div>'
    else:
        body += '<div class="send off">%s</div>' % ICON["send"]
    return (
        '<div class="dock"><div class="composer">' + body + "</div>"
        '<div class="hint"><span>enter to send</span>'
        '<span>opencode &middot; groq/gpt-oss-120b</span></div></div>'
    )


BOARDS = {
    "chat-overflow": lambda: (
        topbar(
            '<button class="ib">%s</button><span class="title" style="font-size:var(--fs-m)">'
            'Investigate why the Android stream keeps dropping after the device sleeps for more than a minute</span>' % ICON["back"],
            '<button class="ib">%s</button>' % ICON["gear"],
        )
        + '<div class="scroll">'
        '<div class="turn user"><div class="bubble">'
        'Dogrudan sunucudan dinle: curl -N http://127.0.0.1:4096/api/event ve kuyruğu kontrol et.</div></div>'
        '<div class="tool"><div class="head"><span class="dot bad"></span>shell &middot; error</div>'
        '<div class="cmd">$ shell -lc "curl -N --max-time 30 http://127.0.0.1:4096/api/event"</div>'
        '<div class="out">curl: (28) Operation timed out after 30001 milliseconds with 0 bytes received</div></div>'
        '<div class="turn role"><div class="asst"><p>The socket stays open, so the drop is not the transport. '
        'The phone suspends the process and the server sees a half dead connection: '
        'keepalivedoesnotreapthedanglingwriteforhours.</p></div>'
        '<div class="stamp">cut short &middot; 19:44</div></div>'
        '</div>'
        + chat_composer(text="Keep the reconnect, drop the replay"),
        "Overflow board: a title that wants three lines, a mono error that cannot wrap mid-token, an "
        "unbroken 40 character word in the answer and the composer holding a long line. Nothing clips, "
        "nothing reflows the bar.",
    ),
    "sessions-list": lambda: (
        topbar('<span class="title">ARCHIEL</span>', '<button class="ib">%s</button><button class="ib">%s</button>' % (ICON["refresh"], ICON["gear"]))
        + '<div class="live"><span class="pulse"></span>127.0.0.1:4096 · connected</div>'
        + sessions_body(session_rows()),
        "The list is the whole first screen: no floating button, the primary action is a row. "
        "Live strip under the bar replaces a spinner.",
    ),
    "sessions-empty": lambda: (
        topbar('<span class="title">ARCHIEL</span>', '<button class="ib">%s</button><button class="ib">%s</button>' % (ICON["refresh"], ICON["gear"]))
        + '<div class="live"><span class="pulse"></span>127.0.0.1:4096 · connected</div>'
        + '<div class="scroll"><div class="row new">'
        '<div style="flex:1"><div class="t">New session</div>'
        '<div class="p">Starts a session on the connected server</div></div>'
        '<span class="ib acc">%s</span></div>'
        '<div class="center"><div class="big">Nothing here yet</div>'
        '<div class="small">The server is reachable but has no sessions. Open one, or let opencode '
        'start one for you.</div></div></div>'
        + chat_composer(),
        "First run on a live server. Empty state names the server instead of drawing a placeholder box.",
    ),
    "sessions-loading": lambda: (
        topbar('<span class="title">ARCHIEL</span>', '<button class="ib">%s</button><button class="ib">%s</button>' % (ICON["refresh"], ICON["gear"]))
        + '<div class="scroll"><div class="row new"><div style="flex:1">'
        '<div class="t">New session</div>'
        '<div class="p">Starts a session on the connected server</div></div>'
        '<span class="ib acc">%s</span></div>' % ICON["plus"]
        + "".join('<div class="row"><div style="flex:1">'
                  '<div class="sk" style="width:%s"></div>'
                  '<div class="sk" style="width:%s;height:9px;margin-top:8px"></div></div>'
                  '<div class="when">%s</div></div>' % r
                  for r in (("64%", "40%", "19:42"), ("78%", "52%", "18:07"), ("58%", "44%", "17:30"),
                            ("70%", "36%", "Dün"), ("52%", "46%", "12 Eki")))
        + '</div>',
        "Loading is skeleton rows at the same rhythm as the real rows, so nothing jumps when data lands.",
    ),
    "sessions-offline": lambda: (
        topbar('<span class="title">ARCHIEL</span>', '<button class="ib">%s</button><button class="ib">%s</button>' % (ICON["refresh"], ICON["gear"]))
        + '<div class="center"><div class="big">The server is not answering</div>'
        '<div class="small">127.0.0.1:4096 refused the connection. In Termux run '
        '<span class="mono">opencode serve --port 4096</span>, then retry.</div>'
        '<div style="height:8px"></div>'
        '<button class="btn" style="width:200px">Retry</button>'
        '<div class="small mono" style="color:var(--faint)">last try 19:44:02</div></div>',
        "Offline is a state, not a toast: it says what failed, what to run, and offers the retry.",
    ),
    "chat-history": lambda: (
        topbar(
            '<button class="ib">%s</button><span class="title" style="font-size:var(--fs-m)">Fix the SSE reconnect</span>' % ICON["back"],
            '<button class="ib">%s</button>' % ICON["gear"],
        )
        + '<div class="scroll">'
        '<div class="turn user"><div class="bubble">When the phone sleeps the stream drops and never comes back. '
        'Fix it without touching the desktop client.</div></div>'
        '<div class="turn role"><div class="asst"><p>Reading the client first: the stream has no reconnect, so a dropped '
        'socket stays dropped until the process restarts.</p>'
        '<p>Two changes: reconnect with backoff, and resend the last event id so the server can replay.</p></div>'
        '<div class="stamp r">19:41</div></div>'
        '<div class="turn user"><div class="bubble">Replay is fine as long as it does not double the message.</div></div>'
        '<div class="turn role"><div class="asst"><p>Agreed — the client keeps the last seen id in memory only, so a '
        'fresh process starts clean. Dedupe happens on <code>event.id</code> before anything reaches state.</p></div>'
        '<div class="stamp r">19:42</div></div>'
        '</div>'
        + chat_composer(text="Then write the test"),
        "Assistant text is the page: no bubbles, full measure. Only the user side is filled, so the eye "
        "always knows who is talking.",
    ),
    "chat-streaming": lambda: (
        topbar(
            '<button class="ib">%s</button><span class="title" style="font-size:var(--fs-m)">Fix the SSE reconnect</span>' % ICON["back"],
            '<span class="live" style="border:0;padding:0"><span class="pulse"></span>working</span>',
        )
        + '<div class="scroll">'
        '<div class="turn user"><div class="bubble">Then write the test for the reconnect.</div></div>'
        '<div class="turn role"><div class="asst"><p>Here is the case that matters: the socket dies mid-event, so the '
        'half-written frame must not be parsed on the next attempt.<span class="caret"></span></p></div></div>'
        '</div>'
        + chat_composer(busy=True),
        "Live state: the amber caret is the only moving part, the top bar says it is working, and the send "
        "button becomes the stop control in the same position.",
    ),
    "chat-tool": lambda: (
        topbar(
            '<button class="ib">%s</button><span class="title" style="font-size:var(--fs-m)">Sweep the shell scripts</span>' % ICON["back"],
            '<button class="ib">%s</button>' % ICON["gear"],
        )
        + '<div class="scroll">'
        '<div class="turn user"><div class="bubble">Check bind.sh for unbound variables.</div></div>'
        '<div class="tool"><div class="head"><span class="dot"></span>shell · completed</div>'
        '<div class="cmd">$ shell -lc "set -u; . ./bind.sh"</div>'
        '<div class="out">bind.sh: line 41: OMARCHY_INSTALL_DIR: unbound variable</div></div>'
        '<div class="turn role"><div class="asst"><p>Found one, at the top of <code>bind.sh</code>. The script assumes '
        'the desktop layout and is run from Termux too, where that variable never exists.</p></div>'
        '<div class="stamp r">12:04</div></div>'
        '</div>'
        + chat_composer(),
        "Tool calls are a hairline block with the command in mono and the raw output kept. Read only in v1, "
        "but it shows what the agent did instead of hiding it.",
    ),
    "chat-error": lambda: (
        topbar(
            '<button class="ib">%s</button><span class="title" style="font-size:var(--fs-m)">Draft the release notes</span>' % ICON["back"],
            '<button class="ib">%s</button>' % ICON["gear"],
        )
        + '<div class="banner bad"><span class="dot bad"></span>'
        '<span>The stream ended before the answer finished. The partial text below is all the server sent.</span></div>'
        + '<div class="scroll">'
        '<div class="turn user"><div class="bubble">Write the 0.2.1 notes.</div></div>'
        '<div class="turn role"><div class="asst" style="color:var(--muted)">Two entries were stale: the cover art and '
        'the local-only<span class="caret"></span></p></div>'
        '<div class="stamp r" style="color:var(--danger)">cut short · 19:44</div></div>'
        '</div>'
        + chat_composer(busy=False, text="Resume"),
        "A broken run says so in place: the banner names the failure, the text greys out, the stamp turns to "
        "the danger colour. Nothing is silently dropped.",
    ),
    "connection-form": lambda: (
        topbar('<button class="ib">%s</button><span class="title" style="font-size:var(--fs-m)">Connection</span>' % ICON["back"], "")
        + '<div class="scroll pad">'
        '<div class="fgroup"><div class="flabel">SERVER ADDRESS</div>'
        '<div class="input focus"><span>http://127.0.0.1:4096</span><span class="caretmark"></span></div></div>'
        '<div class="fgroup"><div class="flabel">PASSWORD</div>'
        '<div class="input"><span style="letter-spacing:.12em;color:var(--muted)">••••••••••••••••</span></div></div>'
        '<div style="height:var(--sp-4)"></div>'
        '<div class="small" style="color:var(--muted);font:var(--fs-xs)/1.5 var(--font-ui)">'
        'The password is the <span class="mono">server password</span> line that '
        '<span class="mono">opencode serve</span> prints when it starts. On Termux the server is this phone, '
        'so 127.0.0.1 is right.</div>'
        '<div style="height:var(--sp-5)"></div>'
        '<button class="btn">Connect</button>'
        '</div>',
        "Two fields, one action, and the sentence that tells you where the password comes from.",
    ),
    "connection-live": lambda: (
        topbar('<button class="ib">%s</button><span class="title" style="font-size:var(--fs-m)">Connection</span>' % ICON["back"], "")
        + '<div class="scroll pad">'
        '<div class="card"><div class="kv"><span class="k">Status</span>'
        '<span class="v" style="color:var(--accent);display:flex;align-items:center;gap:6px">'
        '<span class="dot"></span>connected</span></div>'
        '<div class="kv"><span class="k">Server</span><span class="v">2.0.32</span></div>'
        '<div class="kv"><span class="k">Latency</span><span class="v">41 ms</span></div>'
        '<div class="kv"><span class="k">Sessions</span><span class="v">6</span></div>'
        '<div class="kv"><span class="k">Model</span><span class="v">groq/gpt-oss-120b</span></div></div>'
        '<div style="height:var(--sp-4)"></div>'
        '<div class="fgroup"><div class="flabel">SERVER ADDRESS</div>'
        '<div class="input"><span>http://127.0.0.1:4096</span><span style="color:var(--muted)">edit</span></div></div>'
        '<div style="height:var(--sp-4)"></div>'
        '<button class="btn">Save and reconnect</button>'
        '</div>',
        "Connected state shows real numbers from the server, so this screen is worth opening instead of being "
        "a dead end.",
    ),
}


def reference_board():
    return """<div style="display:grid;grid-template-columns:1fr 1fr;gap:24px;padding:20px;height:100%">
  <div>
    <div class="flabel">BORROWED</div>
    <div style="height:8px"></div>
    <div style="font:var(--fs-xs)/1.7 var(--font-ui);color:var(--muted)">
      Structure from <span class="mono">lunanoir21/tally-omarchy</span> and
      <span class="mono">desktop-widget-control</span>:<br>
      &middot; three base colours (card, text, accent) and two mixed from them<br>
      &middot; hairlines at 7% white, never a shadow<br>
      &middot; motion at 140&ndash;220 ms, one easing family<br>
      &middot; mono for code and numbers only<br>
      &middot; every state drawn: empty, loading, offline, error
    </div>
    <div style="height:16px"></div>
    <div class="flabel">NEW</div>
    <div style="height:8px"></div>
    <div style="font:var(--fs-xs)/1.7 var(--font-ui);color:var(--muted)">
      The layout for a phone, and the reading surface for an agent run:
      turns instead of bubbles, the send control doubling as the stop control,
      a live strip instead of a spinner, and a connection screen that reports
      numbers the server actually returned.
    </div>
  </div>
  <div>
    <div class="card" style="background:var(--bg)">
      <div class="kv" style="padding-top:0"><span class="k">ground</span>
        <span class="v">#0F0F10</span></div>
      <div class="kv"><span class="k">text</span><span class="v">#ECECEC</span></div>
      <div class="kv"><span class="k">muted</span><span class="v">#A8A8AD</span></div>
      <div class="kv"><span class="k">faint</span><span class="v">#5D5D63</span></div>
      <div class="kv"><span class="k">accent</span><span class="v" style="color:var(--accent)">#E0A458</span></div>
      <div class="kv"><span class="k">hairline</span><span class="v">rgba(255,255,255,.07)</span></div>
    </div>
    <div style="height:12px"></div>
    <div style="font:var(--fs-2xs)/1.5 var(--font-mono);color:var(--faint)">
      contrast on #0F0F10 &middot; text 16.6:1 &middot; muted 6.8:1<br>
      accent 7.8:1 &middot; on_accent 8.9:1 &middot; danger 7.5:1
    </div>
  </div>
</div>"""


def tokens_board():
    dark = [("bg", "#0F0F10"), ("surface", "#17171A"), ("line", "#26262A"),
            ("text", "#ECECEC"), ("muted", "#A8A8AD"), ("faint", "#5D5D63"),
            ("accent", "#E0A458"), ("accent_fill", "#E0A458"), ("danger", "#DC615A"),
            ("ok", "#74BF85")]
    light = []

    def column(title, rows, fg, bg):
        sw = "".join(
            '<div style="display:flex;align-items:center;gap:10px;padding:4px 0">'
            '<span style="width:20px;height:20px;border-radius:4px;background:%s;'
            'box-shadow:inset 0 0 0 1px rgba(128,128,128,.25)"></span>'
            '<span style="font:400 12px var(--font-mono);color:%s;width:104px">%s</span>'
            '<span style="font:400 12px var(--font-mono);color:%s">%s</span></div>'
            % (hexv, fg, name, fg, hexv) for name, hexv in rows
        )
        return ('<div style="background:%s;padding:12px;border-radius:var(--radius-m)">'
                '<div class="flabel" style="color:%s">%s</div>'
                '<div style="height:8px"></div>%s</div>' % (bg, fg, title, sw))

    type_rows = [
        ("title", "Syne 700", "23 / 28", "Screen titles"),
        ("row", "Instrument 500", "16 / 22", "Row and turn titles"),
        ("body", "Instrument 400", "16 / 24", "Answers, placeholders"),
        ("meta", "Instrument 400", "13.5 / 20", "Previews, help"),
        ("stamp", "DMMono 400", "11 / 16", "Times, counts, commands"),
    ]
    trows = "".join(
        '<div style="display:flex;align-items:baseline;gap:16px;padding:6px 0;'
        'border-bottom:1px solid var(--line)">'
        '<span style="width:58px;font:400 12px var(--font-mono);color:var(--faint)">%s</span>'
        '<span style="width:132px;font:400 13px var(--font-ui)">%s</span>'
        '<span style="width:64px;white-space:nowrap;font:400 12px var(--font-mono);color:var(--muted)">%s</span>'
        '<span style="font:400 13px var(--font-ui);color:var(--muted)">%s</span></div>'
        % r for r in type_rows
    )
    shapes = "".join(
        '<span style="width:40px;height:40px;border-radius:%s;background:var(--surface);'
        'box-shadow:inset 0 0 0 1px var(--line)"></span>' % r
        for r in ("5px", "10px", "16px", "999px")
    )
    left = (
        '<div style="display:grid;grid-template-columns:1fr;gap:10px">'
        + column("DARK, THE ONLY THEME FOR NOW", dark, "#ECECEC", "#0F0F10")
        + "</div>"
    )
    right = (
        '<div><div class="flabel">TYPE</div><div style="height:8px"></div>' + trows +
        '<div class="flabel" style="margin-top:16px">SPACE, RADIUS, MOTION</div>'
        '<div style="height:8px"></div>'
        '<div style="display:flex;gap:22px">'
        '<div style="font:400 12px var(--font-mono);color:var(--muted);line-height:1.8">'
        'space 4&middot;8&middot;12&middot;16&middot;24&middot;32<br>'
        'radius 5&middot;10&middot;16&middot;pill<br>stroke 1.5 px</div>'
        '<div style="font:400 12px var(--font-mono);color:var(--muted);line-height:1.8;white-space:nowrap">'
        'motion 120/200/300 ms<br>ease-out .2,.8,.2,1<br>hairline 1px, no shadow</div></div>'
        '<div style="height:12px"></div>'
        '<div style="display:flex;gap:8px;align-items:center">' + shapes + '</div>'
        '<div class="flabel" style="margin-top:16px">CONTRAST ON #0F0F10</div>'
        '<div style="height:6px"></div>'
        '<div style="font:400 12px var(--font-mono);color:var(--muted);line-height:1.8">'
        'text 16.6:1 &middot; muted 6.8:1 &middot; accent 7.8:1<br>'
        'on accent 8.9:1 &middot; danger 7.5:1 &middot; ok 8.2:1</div></div>'
    )
    return (
        '<div style="display:grid;grid-template-columns:1fr 1.15fr;gap:22px;padding:18px">'
        + left + right + "</div>"
    )


def fonts_board():
    sample = "ĞÜŞİÖÇ ğüşıöç · 0123456789 · Istanbul ılık"
    faces = [
        ("Syne", "var(--font-display)", "700", "Titles and hero numbers", True),
        ("Instrument Sans", "var(--font-ui)", "400", "Body, labels, rows", True),
        ("DM Mono", "var(--font-mono)", "400", "Code, times, counts", True),
        ("System default", "system-ui, sans-serif", "400", "What we had before", False),
    ]
    out = []
    for name, fam, w, use, ok in faces:
        out.append(
            '<div style="padding:12px 0;border-bottom:1px solid var(--line);display:flex;gap:20px;align-items:baseline">'
            '<span style="width:140px;font:400 12px var(--font-mono);color:%s">%s</span>'
            '<span style="flex:1;font:%s 19px/1.4 %s">Fix the SSE reconnect</span>'
            '<span style="font:400 11px var(--font-mono);color:var(--faint)">%s</span>'
            '<span style="font:400 11px var(--font-mono);color:%s">%s</span></div>'
            % ("var(--accent)" if ok else "var(--faint)", name, w, fam, sample,
               "var(--accent)" if ok else "var(--faint)", "seçildi" if ok else "elendi")
        )
    return ('<div style="padding:20px">'
            '<div class="flabel">SAME STRING, FOUR FACES · 19 PX · TURKISH GLYPHS INCLUDED</div>'
            '%s</div>' % "".join(out))


def main():
    with io.open(CANVAS, encoding="utf8") as fh:
        html = fh.read()

    html = html.replace(
        "</style>",
        FONTS_CSS + "\n" + PALETTE + SCALES + "\n" + APP_CSS + "\n</style>",
        1,
    )

    extra = {"reference": reference_board, "tokens": tokens_board, "fonts": fonts_board}
    for board_id, builder in BOARDS.items():
        extra[board_id] = builder

    for board_id, builder in extra.items():
        built = builder()
        note = ""
        if isinstance(built, tuple):
            built, note = built
        html_out = '<div class="app">' + built + '</div>'
        pat = re.compile(
            r'(<section class="board" id="%s" data-board="%s".*?<div class="artboard"[^>]*>)(.*?)(</div>)'
            % (board_id, board_id),
            re.S,
        )
        match = pat.search(html)
        if not match:
            print("board not found:", board_id)
            continue
        html = html[: match.start()] + match.group(1) + html_out + match.group(3) + html[match.end():]
        if note:
            npat = re.compile(r'(<section class="board" id="%s".*?</div>\s*)<div class="note">.*?</div>' % board_id, re.S)
            html = npat.sub(lambda m: m.group(1) + '<div class="note">' + note + "</div>", html, count=1)

    with io.open(CANVAS, "w", encoding="utf8") as fh:
        fh.write(html)
    print("boards filled:", len(extra))


if __name__ == "__main__":
    main()
