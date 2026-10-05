# Schedule — working notes

A shared calendar for two people (the user and their wife). One self-contained
HTML file, vanilla JS, no build step, no dependencies. Built in the same spirit
as the user's training log, Plates (`X882b/plates`): simple, readable, tuned
to their needs, and it has to still work unchanged in three years.

Planned home: `https://x882b.github.io/Personal_Calendar/` from `main`, root
folder. Current cache version: **schedule-v2**.

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
| `widget/` | Android home-screen widget app (Java, no libraries). See "The widget" below. |
| `.github/workflows/widget.yml` | Builds the widget APK; on `main` also publishes it as a release. |

## THE DEPLOY GOTCHA

**Every change to `index.html` must bump `VERSION` at the top of `sw.js`**
(`schedule-v2` → `schedule-v3`), and both files must be pushed. The service
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

## The widget (`widget/`)

A PWA can't provide an Android home-screen widget, so there is a small
companion app, package `io.github.x882b.schedule`, minSdk 26, plain Java
with no dependencies beyond the Android Gradle Plugin (AGP 8.7.3, Gradle
8.11.1, JDK 17).

- `Agenda.java`: parses `calendar.json` and decides what falls on each day.
  **It is a line-for-line port of `occStart()`, `dayEvents()` and
  `renderUp()` in index.html. Change one, change the other.** It was checked
  by running both over two years of awkward events (31st-of-month, Feb 29,
  skips, `until`, trips over New Year) and diffing: identical.
- `Sync.java`: GET of the contents API with `Accept:
  application/vnd.github.raw+json`. Read-only; the widget never writes.
- `ScheduleWidget.java`: the AppWidgetProvider. `updatePeriodMillis` = 30
  min (Android's minimum) plus a ↻ button. Fetches in `goAsync()` on a
  thread, at most every 5 min unless ↻ is pressed. Taps open the web app
  URL (Chrome hands it to the installed PWA); + opens `…#new`.
- `RowsService.java`: the list rows. The colour bar is two stacked
  ImageViews tinted with `setColorFilter` (plain `View` isn't allowed in
  RemoteViews), so "both" shows both colours.
- `SetupActivity.java` + `Link.java`: the one screen. Connects from
  `schedulewidget://setup?d=<base64 {repo,token}>&u=<app url>` (the web
  app's "Connect the phone widget" button builds an `intent://` URL for it,
  Android only, falling back to the releases page if the app isn't
  installed) or from a pasted "Set up other phone" link. Then offers
  `requestPinAppWidget`.
- The token lives in the app's private SharedPreferences; `allowBackup` is
  off.

**Building.** There is no Android SDK in the cloud dev environment
(`dl.google.com` is blocked, and Google's Maven redirects there), so the APK
is built by GitHub Actions. Every push touching `widget/` builds; on `main`
the APK is also published as release `widget-<run number>`, which is what the
phones download. `versionCode` = run number, so new APKs install over old.
First CI build (run 1) passed. The `@v4` actions log a Node 20 deprecation
warning but run on Node 24; bump them when their next majors are certain.

Local checks that do work here: type-check the Java against Robolectric's
`org.robolectric:android-all` jar from Maven Central with a stand-in `R`
class generated from the resource files, and run `Agenda`/`Link` on the plain
JVM (they only touch `org.json`, `android.net.Uri` and
`android.util.Base64`, which run fine from that jar).

**Signing key.** `widget/widget.keystore` (password `schedule`) is committed
on purpose: updates must be signed with the same key to install over the old
app, and CI has nowhere else to keep it without the user setting up secrets.
Whoever can change this repo can already change the web app, which holds the
same token, so the key adds no real exposure. If that ever changes, move it
to an Actions secret.

---

## Open threads

**Widget on a real phone.** Built and checked as far as possible without a
device: compiles, logic matches the web app, link parsing tested. Layout,
refresh behaviour and the intent hand-off from the installed PWA have not
been seen on a phone yet; expect screenshots and adjust.

**Not built (on purpose, for now):** reminders/notifications (need a push
server), search, per-event colours, week view, a person filter on the widget.

---

## Working style that fits this project

- The user tests on a real phone and sends screenshots; treat those as
  authoritative.
- Say plainly when something can't be verified without a device.
- Design preferences are deliberate and often about removing things.
- Direct communication, no filler.
