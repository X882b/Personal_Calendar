# Agenda

A shared calendar for two people that runs in the browser. You install it on
both phones, each of you gets a colour, and anything one of you adds shows up
on the other phone. No account other than GitHub, no server, no monthly cost.
It opens and works offline. Changes made offline sync once you're back online.

## Files

| File | What it is |
| --- | --- |
| `index.html` | The whole app: markup, styles and logic in one file. |
| `manifest.json` | Name, icon, opens full screen, "New event" shortcut. |
| `sw.js` | Service worker. Caches the app so it opens offline. |
| `icon-192.png`, `icon-512.png`, `icon-maskable.png` | Home screen icons. |
| `tests/two-phones.js` | Optional browser test. Not needed to run the app. |
| `widget/` | The Android home-screen widget (a small separate app). |

## 1. Put it online with GitHub Pages

GitHub Pages is free for **public** repositories. This repository only holds
the app's code. Your events live in a separate private repository (step 3),
so making this one public shows nobody your calendar.

1. This repo → Settings → General → Danger zone → **Change visibility** →
   Public.
2. Settings → Pages → Source: "Deploy from a branch" → Branch: `main`,
   folder `/ (root)` → Save.

After a minute or two the app is at:

```
https://x882b.github.io/Personal_Calendar/
```

## 2. Install it on the phone

1. Open that address in Chrome on Android.
2. Menu (⋮) → **Install app**, or **Add to Home screen**.
3. It gets its own icon and opens without the browser bar.

On first open it asks for both names and whose phone it is.

Long-press the home screen icon for a **Nuevo evento** shortcut.

## On a computer

The same address works in any browser on a PC or Mac (Chrome, Edge, Firefox,
Safari). On a wide screen the tabs sit in the top bar, the month shows the
event titles in each day, and the chosen day is listed beside the month.

- **Connect it:** on a phone that is already connected, ⚙ → **Configurar el
  otro móvil**, send the link to yourself (mail, WhatsApp Web) and open it in
  the computer's browser. It asks whose it is, then syncs like a phone.
- **Install it (optional):** in Chrome or Edge, the install icon at the right
  of the address bar (Chrome: or ⋮ → Cast, save and share → Install page as
  app). It then opens in its own window, like a program.
- **Keyboard:** **N** new event, **←** **→** previous/next month, **Esc**
  closes a form, **Enter** in the title saves.
- Date and time fields follow the browser's language: in an English (US)
  browser they show month/day and AM/PM. Setting the browser to Spanish (or
  English UK) gives day/month and 24 h.

## 3. Turn on sharing between the phones

You do this once, on one phone (or on a computer).

1. On github.com create a new **private** repository, e.g. `calendar-data`.
   Tick "Add a README file".
2. Your profile picture → Settings → Developer settings → Personal access
   tokens → **Fine-grained tokens** → Generate new token.
   - Repository access: **Only select repositories** → `calendar-data`.
   - Permissions → Repository permissions → **Contents: Read and write**.
   - Expiration: pick the longest offered. When it runs out the app says so,
     and you make a new one and connect again.
3. In the app: ⚙ → Sharing between phones → type `calendar-data` (or the full
   `X882b/calendar-data`), paste the token → **Conectar**. The top bar should
   say **Sincronizado**.

## 4. Connect the second phone

On the phone that is already connected: ⚙ → **Configurar el otro móvil**. Send the
link to the other phone (WhatsApp, Signal, whatever) and open it there in
Chrome. It connects, pulls the calendar, and asks whose phone it is. Then
install it as in step 2.

The link contains the access token. Send it only to each other, and delete the
message afterwards if you like.

## 5. The home-screen widget (Android)

A small separate app, **Widget Agenda**, puts today and the coming days on
the home screen in your two colours. It reads the same calendar, so it needs
sharing (step 3) to be on. Do this on each phone.

**Install it**

1. On the phone, open
   `https://github.com/X882b/Personal_Calendar/releases/latest` and tap
   `schedule-widget.apk`.
2. Open the download. Android asks to allow installs from Chrome (or Files):
   allow it, then **Install**. Play Protect may say the app is unknown, since
   it isn't from the Play Store: tap **Install anyway**.

**Connect it**

3. Open Agenda → ⚙ → **Conectar el widget**. The widget app opens
   already connected.
4. Tap **Añadir el widget a la pantalla de inicio**. Or long-press the home screen →
   Widgets → Agenda.

**Using it**

- The days are tiles, three across: hours and title for each event, the
  colour bar showing whose it is (both colours for shared ones). Today has a
  blue outline; a free day in the coming week is a dashed **libre** tile.
- Tap a day or an event to open Agenda; **+** opens a new event.
- **Choose what it shows:** tap the label under the date (**Todo ▾**)
  and pick a person, a category, or both, e.g. **Ana · Horario laboral**.
  Events you share ("Los dos") still show under each person. The label turns
  light so you can see the widget is filtered. Each widget remembers its own
  choice, so you can place two: one with everything, one with just her work
  schedule.
- It refreshes about every 30 minutes (Android won't allow more often), and
  straight away with **↻**. A change made on one phone shows on the other
  phone's widget within that time.
- Drag its edges to resize; the list scrolls.

**Updating it**

Every change to `widget/` that reaches `main` builds a new APK and publishes
it as a new release. Install it the same way; it goes over the old one and
keeps the connection.

If the release page shows nothing yet, the build hasn't run on `main`: check
the repo's **Actions** tab.

## Categories

Every event can have a category. Three come ready: **Horario laboral**,
**Médicos**, **Cumpleaños**. Add, rename or delete them in ⚙ → Categorías, or
with **+ Nueva** right in the event form. Both phones share the same list.

- The second row of chips filters the calendar by category, on top of the
  person filter (e.g. Ana + Work schedule).
- **Shifts:** pick the category, set the time, leave the title empty. The
  event takes the category's name, and in exported images it shows as just
  the time, like `07:30`. Renaming the category renames those shifts too.
- Deleting a category keeps its events; they just lose the category.

## Subcategories (presets with hours)

Each category can hold subcategories with their own hours, e.g.
**Horario laboral** → **Mañana** 07:30–15:30, **Tarde** 15:30–23:30 (these two
come ready; add more, like a **Máster** category with its own, in ⚙ →
Categorías → **+ Subcategoría**).

In a new event they appear as buttons ("Rápido", or just the chosen
category's). **One tap saves the event** with that category and those hours,
for the day(s) and person already picked in the form. So a shift is: **+** on
the day → **Tarde**. The row then reads e.g. *Ana · Tarde*, and exports show
just the hours.

## Export as an image

**Exportar** (top right, next to the people chips) makes a JPEG laid out like a
printed rota: weekday header, then for every week a row of dates and a row of
what's on, each week in its own pastel colour.

- **Semana**, **Mes** or **Año**; ‹ › moves to the previous or next one.
- Pick the category and the person. It starts with whatever you're filtering.
- **Los días sin nada muestran:** e.g. `libre`. Saved with the category,
  shared by both phones. Week and month only; the year leaves it out.
- **Idioma** of the weekday and month names: English, Español, Srpski,
  Српски.
- **Compartir** sends it straight to WhatsApp, Viber, mail. **Guardar imagen**
  puts it in Downloads.

A month shows whole weeks, so it starts on a Monday; days from the
neighbouring months are in lighter text. A multi-day event (like a vacation
called `vaca`) appears on every day it covers.

## How syncing works

- Every phone keeps a full copy, so the app always opens instantly, even with
  no signal.
- The calendar is a single file, `calendar.json`, in your private repo. A
  phone syncs when it opens, every minute while it's on screen, and right
  after you change something.
- If you both change things at the same time, both changes are kept. If you
  both edit the *same* event, the later edit wins.
- Every sync that changes something is a commit in `calendar-data`, with a
  message like `Ana: + Dentist`. That history is a full log of who changed
  what, and a way back if something is deleted by mistake.

The dot in the top bar shows the state: green **Sincronizado**, yellow
**Pendiente/Sin conexión** (changes kept on the phone, they go out later), red **Error de sincronización** (tap it for the reason, usually an expired token).

## Updating it later

Edit `index.html`, then **change the `VERSION` string at the top of `sw.js`**
(for example `schedule-v1` to `schedule-v2`). Commit and push both. Without
that, phones keep serving the old cached copy.

## Backup

⚙ → Copia de seguridad → **Exportar archivo** saves everything as JSON. **Importar archivo** adds
whatever is missing and never overwrites newer changes. With sharing on, the
private repo is already a backup, so this is mostly for before you switch
phones without sharing.
