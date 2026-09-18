# Amehrug, state at 0.5.0

Amehrug is an offline, private note taking app for Android. It is a fork of
Notally 6.2 by OMGodse (`com.omgodse.notally`), rewritten in Jetpack Compose.

`com.amehrug.app`, versionCode 9, versionName 0.5.0, minSdk 31, compileSdk and
targetSdk 37. AGP 9.4.0 with built-in Kotlin, Gradle 9.7.1, Kotlin 2.4.0
(Compose compiler plugin), Compose 1.12.1, material3 1.4.0, activity 1.13.0,
core 1.19.0, Room 2.8.5 with KSP 2.3.11, kotlinx.coroutines 1.11.0, SQLCipher for Android 4.18.0 with androidx.sqlite 2.7.0. Versions read on developer.android.com and plugins.gradle.org on
2026-09-17. Individual versions are pinned instead of the Compose BOM, because
the BOM number could not be read on an official page.

Roadmap task 1 is done: one screen, dynamic color, edge to edge, original
adaptive icon with a monochrome layer, English and French strings, no
permission, backup off. Never built in the sandbox (no Android SDK).

The Notally source sits in `reference/notally-6.2`, outside the build. The
Gradle wrapper jar and scripts were copied from Notally. Only the properties
file was changed to Gradle 9.7.1.

## Identity

- App name: Amehrug, from Kabyle. Renamed from Ahrag in 0.2.1, before the
  first build, so nothing carries the old name. Checked on 2026-09-17: no
  app or repository called Amehrug on Google Play, GitHub or F-Droid.
- applicationId: `com.amehrug.app`. It must differ from Notally so both apps can
  be installed side by side and nobody mistakes one for the other.
- License: GPL-3.0, inherited and mandatory. The LICENSE file stays, the
  README credits Notally and its author, and the source stays public.
- First version: 0.1.0, versionCode 1.

## Decisions taken

- **Full rewrite in Compose.** Notally is XML Views on Material 2
  (`material:1.4.0`, 2021). Material 3 Expressive is a Compose system, so a
  migration of the Views would stop halfway.
- **minSdk 31.** Old devices are not a goal. 31 brings dynamic color, the
  system splash screen and the current exact alarm model without
  workarounds. Each of these gets confirmed on developer.android.com when the
  task that uses it is built. The level is raised only if a feature needs it.
- **Offline, always.** No INTERNET permission, ever. Notally already has none
  and this is the property that makes the security promise believable.
- **Notally backups are importable** (the ZIP with the Room database, and the
  older XML format).

## The source, Notally 6.2

- 106 Kotlin files, about 10k lines, no tests, no CI workflow.
- AGP 9.0.1 with legacy flags (`android.newDsl=false`,
  `android.builtInKotlin=false`), minSdk 21, targetSdk 35, compileSdk 36.
- Views, Fragments, ViewBinding, Navigation 2.3.5.
- Room, schema version 5, one `BaseNote` table. Labels, spans, list items,
  images, audio and the reminder are JSON text columns inside it.
- Media are files named in those JSON columns.
- Features: text notes, checklists, bold, italic, monospace, strikethrough,
  colors, pins, labels, archive, trash, images, audio recording, reminders,
  one widget, search, export to TXT, JSON, HTML and PDF (PDF via WebView
  printing), ZIP backup and scheduled auto backup to a chosen folder.
- 30 translations, crowd sourced.

## Security defects found in the source

These are the reasons the security track exists. None may survive the rewrite.

1. `allowBackup="true"`. The database, every note in clear, goes to the cloud
   backup. Only preferences are excluded.
2. Images and audio live in `externalMediaDirs` (`Android/media/<package>`),
   a folder the media scanner can index. Other apps with photo access can see
   them.
3. The database is not encrypted. Fixed in 0.3.0.
4. Backup ZIPs are not encrypted. Fixed in 0.5.0.
5. No `FLAG_SECURE`: screenshots and the recents preview show notes.
6. A dependency comes from Jitpack (`swipe-layout-android`), unmaintained and
   an unsigned supply chain.
7. `allowMainThreadQueries()` is set on the whole database, not only where a
   comment claims.

## What the import must read

- ZIP: entry `NotallyDatabase` (SQLite, read only), folders `Images/` and
  `Audios/`. Only media referenced by a note are imported, renamed to a random
  UUID, which also neutralises path traversal in entry names. Keep both.
- XML: the legacy format read by `legacy/XMLUtils.kt`.
- Room schemas 1 to 5 are in `app/schemas` and are the reference for columns.

## Open

1. Build not yet confirmed on a machine or in CI. If the wrapper script is
   too old, `gradle wrapper --gradle-version 9.7.1` regenerates it.
2. The permission check in CI assumes aapt2 prints `uses-permission` lines.
   Read its output in the first run log before trusting a green result.
3. Nine versions stacked without a build (0.1.0 to 0.5.0). The first failure
   log must be read file by file. Most likely places, in order: KSP with
   built-in Kotlin, the Room annotations on the entities, the DAO queries
   (Room checks them at compile time).
4. After the first local build, commit `app/schemas/`. The version 1 JSON is
   the base of every migration.
5. Reminders are replaced on every save, so their row ids change. Task 16
   must key alarms on the note id and the time, never on the row id.
6. **The riskiest point of the build**: FTS4 with the unicode61 tokenizer
   under SQLCipher. If the first run fails while creating `notes_fts`, the
   fallback is one line in `Entities.kt`: drop the `tokenizer` argument of
   `@Fts4`, which loses accent folding in search until FTS5 is tried.
7. SQLCipher is resolved by plain coordinates. If Gradle pulls no artifact,
   the README form is `net.zetetic:sqlcipher-android:4.18.0@aar`.
8. The lock cannot be tried in an emulator without a screen lock set: the
   switch stays greyed out, which is the intended behaviour.
9. PBKDF2 at 600000 iterations takes a noticeable moment on a phone, once
   per backup or restore. Measure it in the log on the first real run and
   lower the number only if it is unbearable, never below 210000.
10. Next: scheduled backup (task 8b), which needs WorkManager, a folder the
   user grants, and the backup password wrapped by the Keystore.

## Diagnostics, since 0.1.3

Home screen, top bar, Diagnostics. Newest entry first. Refresh, Copy all,
Save as .txt (system file picker, no permission), Clear.

- `DiagnosticLog` is pure Kotlin, 500 entries, thread safe, checked under
  kotlinc 2.4.0 by `tools/harness/DiagnosticLogCheck.kt` (all passed).
- **Privacy rule**: no note content, ever. Messages are fixed strings written
  by the caller, flattened to one line and cut at 300 characters. Exceptions
  keep class names and 15 frames per cause, never their message, because a
  message can quote user data.
- Timings: process start to Application, and to the first Activity.
- A crash is written to `noBackupFilesDir/last-crash.txt` by the default
  handler, shown on the next launch as `previous crash`, then deleted.
- File writes run on the `amehrug-diagnostics` single thread executor, not on
  the main thread. No coroutines dependency yet.
- Navigation is a two value enum with `BackHandler`. A navigation library
  comes with task 10.

## Hardening, since 0.1.2

- `allowBackup=false` and `data_extraction_rules.xml` excluding root, file,
  database, sharedpref and external from cloud backup and device transfer.
  allowBackup alone does not stop device transfer on some Android 12+
  devices (developer.android.com, behavior changes 12).
- INTERNET and ACCESS_NETWORK_STATE removed with `tools:node="remove"`.
- `usesCleartextTraffic=false`, as a second lock.
- `FLAG_SECURE` always on, until the setting of task 19 exists.
- `dependenciesInfo` off for APK and bundle.
- CI dumps the release permissions and fails on anything except AndroidX
  core's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

## CI, since 0.1.1

`.github/workflows/build.yml` validates the wrapper jar, builds debug and
unsigned release, runs unit tests and uploads both APKs. JDK 21 Temurin.
Gradle is cached by `actions/setup-java`, not by `gradle/actions/setup-gradle`,
whose cache became closed source in v6. `chmod +x gradlew` runs first because
the web uploader drops the executable bit.

`.github/dependabot.yml` checks Gradle and Actions weekly, grouped. Merged
Dependabot pull requests change the repo without a zip.

## Database, since 0.2.0

Room, schema version 1, file `amehrug.db`. Package `data/db`.

- `notes`: type, folder, color, title, body, pinned, createdAt, modifiedAt,
  deletedAt, and `itemsText`, the list items joined by new lines, kept only
  for search. Index on folder, pinned, modifiedAt.
- `notes_fts`: FTS4, external content on `notes` (title, body, itemsText),
  tokenizer unicode61, which folds case and accents. Room keeps it in sync
  with triggers.
- `spans` (startIndex, endIndex, five style flags), `list_items` (position,
  body, checked, indent 0 to 3), `attachments` (random unique fileName,
  mime, size, duration, position), `reminders` (atMillis, repeat). All with
  ON DELETE CASCADE on the note.
- `labels`, primary key `name` COLLATE NOCASE, and `note_labels` with ON
  DELETE and ON UPDATE CASCADE, so renaming a label follows everywhere and
  deleting one keeps the notes.
- Enums stored by name. Colors and note types use Notally's names.

Rules:

- No `allowMainThreadQueries`, no destructive migration. DAOs are suspend or
  Flow only.
- `NoteDao.saveFull` upserts the note and replaces spans, items, labels and
  reminders in one transaction. Attachments are never touched by a save,
  because they own files.
- `NoteRepository` is the only door. It sanitizes every note first
  (`NoteRules`): spans clamped to the body and deduplicated, titles and items
  on one line, indent clamped, labels trimmed and deduplicated without case,
  reminders sorted. A new empty note is not stored.
- Search goes through `FtsQuery`: every word becomes a quoted prefix term,
  only letters and digits survive, ten terms at most. No user input can
  change the query shape. Trash is excluded.
- Deleting forever returns the attachment file names, for the file store of
  task 14 to delete.
- `AppGraph` builds everything lazily. At start it opens the database off the
  main thread, logs the open time and the note count, and purges the trash
  after 30 days. Counts only.

Checked under kotlinc 2.4.0: `tools/harness/ModelCheck.kt`, 21 checks, all
passed. The data layer compiles against stubs of the APIs it uses, which
proves it consistent with itself, not with the real Room.

## Encryption at rest, since 0.3.0

Nothing readable is left on the phone.

- **Database**: SQLCipher 4.18.0 through Room's `openHelperFactory`. The
  whole file is encrypted, journal included. Its passphrase is 32 random
  bytes, handed over as 64 hexadecimal characters so it has one reading.
- **Media**: every attachment is a separate AES-256-GCM file in `filesDir`,
  which is private to the app. Notally left them in the shared media folder,
  where the media scanner and any app with photo access could read them.
  Names are random UUIDs, so a name says nothing about the note.
- **Keys**: one AES-256-GCM key in the Android Keystore, in the secure
  element when the phone has one, wraps both secrets. Only the wrapped form
  is on disk, under `noBackupFilesDir`, which no backup and no device
  transfer reads. The Keystore key never leaves the hardware, so a copy of
  the app folder taken from a rooted phone opens nothing.
- **Key file format** (`KeyEnvelope`): version, nonce length, nonce, wrapped
  bytes. Written aside and moved into place, so a crash never leaves half a
  key.
- **Media file format** (`FileCrypto`): "AMH1", an 8 byte random prefix, then
  64 kB chunks each sealed with its own tag. The tag covers the chunk number
  and whether the chunk is the last one, so a file that was cut, reordered or
  edited is refused instead of half read. A single GCM pass would not do
  this: `CipherInputStream` reports a bad tag as a normal end of file.
- **Clear text copies**: only in the cache folder, only for players that must
  seek, and the cache is emptied at every start.
- **Failure is loud**: if the key cannot be unwrapped, `KeyUnavailableException`
  is thrown and nothing opens. If a database file exists with no key on this
  device, the startup check says so in the log and stops.
- SQLCipher's own Logcat output is switched off with `NoopTarget`.

Checked under kotlinc 2.4.0 with real AES, `tools/harness/CryptoCheck.kt`,
27 checks, all passed: round trips from 0 to 196615 bytes, two encryptions of
the same input differ, wrong key refused, flipped bit refused, wrong header
refused, file cut refused, last chunk removed refused, two chunks swapped
refused.

## App lock, since 0.4.0

- **Prompt**: the platform `BiometricPrompt`, not androidx.biometric. minSdk
  is 31, so the system already has the API, and a dependency is avoided.
  Fingerprint, face or the device credential, whichever the phone offers.
  With DEVICE_CREDENTIAL allowed there must be no negative button.
- **Offered only** when the phone itself has a screen lock, checked with
  `KeyguardManager.isDeviceSecure` and `BiometricManager.canAuthenticate`.
  Otherwise the switch stays off and greyed out.
- **Starts locked.** `AppLock` holds `true` until the settings say the lock
  is off, so no note can flash before the decision. The activity reports
  going to the background and coming back, and `LockPolicy` decides.
- **Monotonic clock**: `SystemClock.elapsedRealtime`, so changing the date on
  the phone buys no extra unlocked time, and a clock that goes backwards
  locks.
- **Timeouts**: at once, 15 s, 1 min, 5 min, 15 min. A value outside this list
  is refused when written and ignored when read.
- **Settings live in the encrypted database**, table `settings`, as name and
  value rows. No DataStore dependency, and a new setting never needs a
  migration. Schema stays at version 1 because nothing has shipped yet.
- **When the settings cannot be read**, the app shows a message and no note:
  an unknown lock state is treated as locked.
- Provisional settings screen with the switch, the timeout and a Lock now
  button. The real one is task 19.

Checked under kotlinc 2.4.0 in `ModelCheck.kt`, 35 checks, all passed. The
new ones cover the timeout arithmetic, the backwards clock, the zero timeout,
and settings values that are damaged, unknown or not numbers.

## Backup, since 0.5.0

Manual for now, from the settings screen. Scheduling is task 8b.

- **One file**, extension `.amb`: a zip holding `amehrug.json` and the
  attachments, wrapped in AES-256-GCM. The key comes from the user's password
  through PBKDF2-HMAC-SHA256, 600000 iterations, with a random salt. Salt and
  iteration count sit in the header, so a file written today still opens when
  those numbers change.
- **The body reuses the chunked format of the media files**, so a backup that
  was cut or edited is refused instead of half restored. A wrong password and
  a damaged file look the same, which is the intended behaviour.
- **The password is never stored** and never leaves the dialog. A backup
  nobody else can open is the whole point, and that includes us.
- **The clear zip only exists in the app cache**, for the length of the
  operation, and is deleted whatever happens, including on failure.
- **Restore adds**, it never replaces or erases. Notes come back with their
  own dates and new identifiers. Media are read from the zip and re-encrypted
  into the store under new random names.
- **The reader is strict where it must be**: an attachment name containing a
  path is dropped, JSON is bounded in depth and length, a backup from a newer
  version is refused. It is forgiving where it can be: an unknown colour or
  type falls back to the default rather than losing the note.

**No serialization library.** JSON is a hundred lines in `model/Json.kt`,
because the current version of kotlinx.serialization could not be read on an
official page, and because a hand written reader can be attacked here.

Checked under kotlinc 2.4.0 with real AES and real JSON,
`tools/harness/BackupCheck.kt`, 29 checks, all passed: quotes, accents and
emoji survive a round trip, malformed JSON is refused in six shapes, the
backup round trip keeps text, styles, items, labels, reminders and dates,
identifiers are not restored, an attachment path is dropped, and the sealed
file is refused on a wrong password, a cut file, an edited byte, or a file
that is not ours.
