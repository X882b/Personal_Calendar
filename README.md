# Schedule

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

Long-press the home screen icon for a **New event** shortcut.

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
3. In the app: ⚙ → Sharing between phones → paste `X882b/calendar-data` and
   the token → **Connect**. The top bar should say **Synced**.

## 4. Connect the second phone

On the phone that is already connected: ⚙ → **Set up other phone**. Send the
link to the other phone (WhatsApp, Signal, whatever) and open it there in
Chrome. It connects, pulls the calendar, and asks whose phone it is. Then
install it as in step 2.

The link contains the access token. Send it only to each other, and delete the
message afterwards if you like.

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

The dot in the top bar shows the state: green **Synced**, yellow
**Waiting/Offline** (changes kept on the phone, they go out later), red **Sync
problem** (tap it for the reason, usually an expired token).

## Updating it later

Edit `index.html`, then **change the `VERSION` string at the top of `sw.js`**
(for example `schedule-v1` to `schedule-v2`). Commit and push both. Without
that, phones keep serving the old cached copy.

## Backup

⚙ → Backup → **Export file** saves everything as JSON. **Import file** adds
whatever is missing and never overwrites newer changes. With sharing on, the
private repo is already a backup, so this is mostly for before you switch
phones without sharing.

## A widget on the home screen?

A web app like this one can't put a widget on the Android home screen; only
installed native apps can. What it does have is the home screen icon and
the long-press **New event** shortcut.

A real widget is possible as a small separate Android app that reads the same
`calendar.json` and shows today and the next few days in your colours. See
`CLAUDE.md` → Open threads.
