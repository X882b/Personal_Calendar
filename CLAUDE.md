# Schedule — working notes

A shared calendar for two people (the user and their wife). One self-contained
HTML file, vanilla JS, no build step, no dependencies. Built in the same spirit
as the user's training log, Plates (`X882b/plates`): simple, readable, tuned
to their needs, and it has to still work unchanged in three years.

Planned home: `https://x882b.github.io/Personal_Calendar/` from `main`, root
folder. Current cache version: **schedule-v1**.

Prefer small, direct changes to the existing file over refactors, frameworks
or a build pipeline.

---

## Files

| File | What it is |
| --- | --- |
| `index.html` | The entire app: styles, markup and logic. |
| `sw.js` | Service worker. Caches the app for offline use. Leaves GitHub API calls alone. |
| `manifest.json` | Name, icons, standalone display, "New event" shortcut (`#new`). |
| `icon-*.png` | Home-screen icons. |
| `tests/two-phones.js` | Playwright test: two phones syncing through a fake GitHub. |
| `README.md` | End-user setup and deploy notes. |

## THE DEPLOY GOTCHA

**Every change to `index.html` must bump `VERSION` at the top of `sw.js`**
(`schedule-v1` → `schedule-v2`), and both files must be pushed. The service
worker serves the cached copy first; without the bump phones keep the old app.

Upload files by drag-and-drop, never by pasting into GitHub's web editor (a
paste once truncated Plates' index.html).

---

## Architecture

Same pattern as Plates. `<style>` (variables first, wide-screen `@media` block
last), a fixed shell (header, empty `<main id="view">`, "+ Event" button, tab
bar, empty sheet), and `<script>`.

All rendering rebuilds the view from state. Taps go through one delegated
click listener that dispatches on `data-act`. Every `data-act` must have an
`a === "..."` branch:

```bash
python3 -c "
import re; h=open('index.html').read()
acts=set(re.findall(r'data-act=\"([a-zA-Z]+)\"',h)); hand=set(re.findall(r'a === \"([a-zA-Z]+)\"',h))
print('no handler:',sorted(acts-hand),'| unused:',sorted(hand-acts))"
```

### Two kinds of state

- `S`: the shared calendar. Synced between phones. localStorage `schedule_doc`.
- `L`: this phone only: `me` (whose phone), `filter`, `tab`, `repo`, `token`,
  `dirty`, `last`. localStorage `schedule_local`. The token and `me` must
  never end up in `S`.

Every change to `S` goes through `changed()`: bumps `edits`, sets `L.dirty`,
saves, renders, schedules a sync.

### Shape of S

```js
S = {
  people: [{id:"a", name, color, u}, {id:"b", name, color, u}],
  events: [{ id, title, who:"a"|"b"|"ab", date:"YYYY-MM-DD", last:"",   // last = final day of a trip
             from:"HH:MM"|"", to:"", repeat:""|"w"|"2w"|"m"|"y", until:"",
             skip:["YYYY-MM-DD"], note, by:"a"|"b", u }]
}
```

- `u` is the last-change time in ms. `stamp(old)` makes it at least
  `old.u + 1`, so an edit always beats the version it was made from even if
  the phone clocks disagree a little.
- Deleting writes a tombstone `{id, del:1, by, u}`. Never drop tombstones.
  Without them a delete on one phone comes back from the other.
- `skip` holds occurrence start dates removed from a repeating event ("Remove
  this day only").
- Unknown fields on events are preserved on edit (`Object.assign` onto the old
  record), so an older copy of the app won't strip fields a newer one added.

### Dates

Dates are `YYYY-MM-DD` strings; arithmetic is UTC (`D()`, `ymd()`, `addDays`,
`diffDays`) so DST can't shift a day. Weeks start on Monday. Times are 24h.
`occStart(ev, day)` answers "is this event on this day, and which occurrence";
everything that lists events uses it via `dayEvents()`.

### Sync

The calendar is one file, `calendar.json`, in a private GitHub repo the user
owns, read and written with the Contents API and a fine-grained token
(Contents: read and write, that one repo only).

`sync()`: GET the file (404 = none yet) → `merge(S, remote)` record by record,
newer `u` wins → if that differs from the remote, PUT it with the sha that was
read. GitHub refuses a stale sha with 409 (422 when the file appeared
meanwhile), and the loop goes round again with the other phone's version, up
to 5 times. After a successful round `S = merge(S, merged)`, so edits made
while the request was in flight survive. `edits` tells whether `L.dirty` can
be cleared.

Sync runs on boot, when the app becomes visible, on `online`, every 60 s while
visible, and 1.2 s after any change. `serialize()` writes one event per line,
sorted by id, so the data repo's history shows one-line diffs; commit messages
read like `Ana: + Dentist, ~ Gym`.

Fetch failure (no signal) shows "Waiting"/"Offline", never an error. HTTP
errors get a plain-language reason in Settings (`failure()`).

"Set up other phone" builds `index.html#join=<base64 {repo, token}>`. Boot
reads it, stores it in `L`, and strips it from the address bar.

### UI rules carried over from Plates

- Sheets use `dvh`, not `vh` (Android URL bar).
- No native `<select>`; choices are pill rows (`seg()`). Date and time use
  the native inputs (`color-scheme: dark` keeps them dark).
- Opening a sheet pushes a history entry so Android's back button closes it.
- Deleting needs two taps on the button (no `confirm()` dialogs).

---

## Testing

Chromium and Playwright are available in the cloud environment, so the app can
be driven for real:

```bash
node tests/two-phones.js          # SHOTS=/some/dir to also save screenshots
```

It serves the folder on localhost, fakes the GitHub Contents API (with sha
compare-and-swap, 401 for a bad token, an injected write race), and runs two
phone-sized browsers through: first-run, adding events (timed, shared,
weekly, trip, yearly), filters, month view, removing one occurrence, bad
token, connect, joining via the setup link, both phones editing offline, a
conflicting write, delete propagation and a colour change.

Minimum before shipping: that test, `node --check` on the extracted script,
balanced CSS braces, and the `data-act` audit above.

---

## Open threads

**Home-screen widget.** The user asked for the schedule as a phone widget. A
PWA can't provide an Android home-screen widget. Options discussed:

1. A small native Android companion app (Kotlin, Glance app widget) that reads
   `calendar.json` from the private repo with the same token and shows today
   plus the next few days in the two colours; tap opens the web app. Can be
   built to an APK by GitHub Actions, so no Android Studio is needed, and
   sideloaded. Android refreshes widgets every ~30 min at best, plus a refresh
   button. This is the recommended route if both phones are Android.
2. Mirroring into a shared Google Calendar and using Google's widget: needs a
   Google Cloud OAuth client; heavier.
3. iPhone: a widget needs a native Swift app and Apple signing; not practical.

Waiting on: whether the wife's phone is Android too.

**Not built (on purpose, for now):** reminders/notifications (need a push
server), search, per-event colours, week view.

---

## Working style that fits this project

- The user tests on a real phone and sends screenshots; treat those as
  authoritative.
- Say plainly when something can't be verified without a device.
- Design preferences are deliberate and often about removing things.
- Direct communication, no filler.
