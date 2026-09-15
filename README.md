# Attendance

Tap-to-check-in attendance tracker for Android. Hold an NFC tag to the phone, the person is marked present for today, and their history builds up as a row of dots. Barcodes work as a backup for anyone who forgot their tag.

## Building the APK

1. Open the `AttendanceTracker` folder in Android Studio (Ladybug or newer) and let Gradle sync — it downloads the dependencies on first run, so you need a connection for that step only.
2. **Run** (green arrow) installs a debug build straight onto a connected phone.
3. For a shareable file: **Build → Build Bundle(s)/APK(s) → Build APK(s)**. The result lands in `app/build/outputs/apk/debug/app-debug.apk`.

From the command line: `./gradlew assembleDebug`.

A debug APK installs on any phone with "install from unknown sources" enabled. For a Play Store build, use **Build → Generate Signed Bundle / APK**.

Requirements: JDK 17, compileSdk 34, minSdk 26 (Android 8.0+).

NFC needs a real phone — emulators don't have a reader. Neither NFC nor the camera is marked as required in the manifest, so the app still installs and runs on hardware missing either.

## How it works

**Today** — the round button opens the NFC reader. Hold a tag to the back of the phone and it checks in immediately. The reader stays open between taps, reporting each result in place, so a queue of people can tap through one after another without reopening it.

The barcode fallback sits directly under the round button as an outlined `Scan barcode` pill — one tap, but visually secondary so it doesn't compete with the main target. It's also offered inside the NFC screen (`Scan barcode instead`) and again at the bottom of the scanner (`Enter ID manually`) for someone with no tag and no readable badge.

After any check-in, by any route:
- Known ID → the person is stamped present for today. **One crisp buzz.**
- Known ID, already used today → says so, changes nothing. **Two light taps.**
- Unknown ID → asks who it belongs to. **One longer buzz.**

The three vibration patterns are deliberately distinguishable, so you can read the result without looking at the screen — which is the normal case when you're holding the phone at a door and watching the queue. Phones with no vibrator simply skip it.

When an unknown ID comes up, you can create a new person or attach it to someone already on the roster. **The scanned value is pre-filled into its matching field** — tap an unknown tag and the NFC field is already populated, leaving the barcode field blank to fill in later (and the reverse for an unknown barcode). Both can be captured at once if the person has both on them.

Skipped it? The ID is saved under **Unmatched IDs** on the People tab along with the days it was seen, so you can name it later without losing that attendance. Matching it to a person carries those days across.

**Attendance** — everyone ranked by how many days they've attended. Each dot is one day; tap it to see the date.

**People** — the roster. Search by name, tag or barcode. Tap anyone to rename them, add a tag or barcode, or remove an ID. A half-filled record shows `No NFC tag yet` or `No barcode yet` so it's obvious what's still missing.

## Admin mode

The lock icon on the People tab takes the password (`vortx`). It re-locks when the app closes. Unlocked, you can:

- **Add an attendance day** — Attendance tab, `Add day`, then pick a date. For someone who turned up but was never scanned. Future dates are blocked, and a day already on the record won't be double-added. This appears even for people with no attendance yet.
- **Remove a single day** — Attendance tab, tap a dot, then `Remove day`. The fine-grained fix for one bad scan.
- **Clear one person's attendance** — `Clear all N days`. Keeps the person and their IDs.
- **Delete a person** — People tab, trash icon. Removes their record, IDs and all attendance.
- **Delete an unmatched ID** — People tab, the ✕ on an unmatched row. Discards it and the days banked against it.
- **Erase everything** — People tab, at the very bottom. Wipes every person, day and unmatched ID, back to a fresh install.

Destructive actions confirm first. `Erase everything` additionally requires typing `ERASE`, since it's the one action with nothing left to recover from — export a CSV first.

## CSV

The two icons at the top right import and export. Export writes wherever you choose through the normal Android file picker:

```csv
name,barcode_ids,nfc_ids,days_attended,dates
Ada Lovelace,10039 | 10040,04A2B1C3D5,3,2026-09-01 | 2026-09-04 | 2026-09-11
Alan Turing,,9F1E2D4C,1,2026-09-11
```

Multi-values are pipe-separated inside their cell, so it stays one row per person and opens cleanly in Sheets or Excel. `days_attended` is written for readability and ignored on import — the `dates` column is the source of truth.

Columns are found **by header name, not position**, so reordered columns are fine and CSVs exported by the earlier barcode-only build still import correctly. `ids`/`barcodes` are accepted as aliases for `barcode_ids`, and `nfc`/`tag` for `nfc_ids`.

Import **merges**, matching on name, case-insensitively. Matching people gain the imported IDs and dates; unrecognised names are added. Nothing is ever deleted by an import, so re-importing the same file twice is harmless.

## Storage

Everything lives in one JSON file in the app's private storage (`roster.json`). No server, no account, works offline. The barcode model is bundled at install time so the first scan works without a connection.

The file carries a schema version. Data written by the earlier barcode-only build is read transparently: those barcodes land in the barcode list and the NFC list starts empty.

Because it's on-device only, **export a CSV before uninstalling** — that's your backup.

## Layout

Built for 1080 × 2400 (≈393 × 873 dp). All sizing is in `dp` with scrolling lists, so it adapts to other phones; the dot grid and ID chips reflow to fit the width.

## Screenshots

<img width="216" height="480" alt="Screenshot_2026-09-14-20-54-58-71_da38f1c02d387487ac66390dc6064505" src="https://github.com/user-attachments/assets/00dd6448-07cd-407c-933d-9d7c862e046a" />
<img width="216" height="480" alt="Screenshot_2026-09-14-20-55-05-78_da38f1c02d387487ac66390dc6064505" src="https://github.com/user-attachments/assets/737b4302-3fe0-4429-b807-42a08c804420" />
<img width="216" height="480" alt="Screenshot_2026-09-14-20-55-46-23" src="https://github.com/user-attachments/assets/1b5f4eb3-34f9-4d47-9211-807946b212b5" />
<img width="216" height="480" alt="Screenshot_2026-09-14-20-55-52-37_da38f1c02d387487ac66390dc6064505" src="https://github.com/user-attachments/assets/b0c9435b-f5fb-4d17-8801-8385964e651f" />




