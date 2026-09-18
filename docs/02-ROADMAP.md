# Amehrug roadmap

One task per turn. Each task ends with a full zip and a version bump. Versions
of every tool and library are looked up on official pages when the task is
built, never written here from memory.

## Phase 0, foundation

1. **Skeleton.** Done in 0.1.0. New Gradle project with a version catalog, current AGP
   without legacy flags, Kotlin, Compose BOM, minSdk 31. Package
   `com.amehrug.app`. One empty screen with dynamic color and edge to edge.
   GPL LICENSE, README crediting Notally, `.gitignore`. Notally code kept
   aside as reference, not compiled.
2. **CI.** Done in 0.1.1. GitHub Actions workflow that builds debug and release and runs unit
   tests, with the Gradle wrapper and the workflow pinned together.
   Dependabot config. `.github/` must be pasted through the web editor.
3. **Manifest hardening.** Done in 0.1.2. `allowBackup=false`, empty data extraction rules,
   no INTERNET, `FLAG_SECURE` behind a setting that is on by default,
   release minified, no Jitpack.
4. **Diagnostic log.** Done in 0.1.3. In-app log of errors and timings, never note content,
   with "Copy all" and "Save as .txt".

## Phase 1, data and security core

5. **Database.** Done in 0.2.0. Room with proper tables (notes, list items, labels,
   attachments, reminders) instead of JSON columns. Full text search table.
   All queries off the main thread.
6. **Encryption at rest.** Done in 0.3.0. SQLCipher for the database. Key generated once,
   wrapped by the Android Keystore. Media stored in internal storage and
   encrypted file by file.
7. **App lock.** Done in 0.4.0. Biometric or device credential, lock timeout, lock on leaving
   the app.
8. **Encrypted backup.** Own format: ZIP inside AES-GCM, key derived from a
   user password. Manual backup and restore done in 0.5.0.
8b. **Scheduled backup** to a folder the user picks, with the password kept
   wrapped by the Keystore. Next task.
9. **Notally import.** ZIP (database version 1 to 5, images, audio) and the
   legacy XML. Converter tested under kotlinc against real Notally exports.

## Phase 2, parity with Notally, in Compose

10. **Notes list.** Grid and list, pinned section, colors, labels, archive,
    trash with auto purge, multi select with bulk actions.
11. **Text editor.** Title and body, bold, italic, monospace, strikethrough,
    links detected, undo and redo, autosave.
12. **Checklists.** Drag to reorder, checked items moved to the bottom.
13. **Search.** FTS with filters by type, color, label and date.
14. **Images.** Pick, view with zoom, remove. Stored encrypted.
15. **Audio.** Record with a foreground service, play back.
16. **Reminders.** Exact alarms, notification channel, reboot handling.
17. **Widget.** Glance widget for one note or list.
18. **Export.** TXT, Markdown, HTML and PDF. PDF without WebView if a native
    path exists.
19. **Settings.** Theme, dynamic color toggle, AMOLED dark, text size, date
    format, security options, backup.

## Phase 3, new features

20. **Markdown.** Headings, bullet and numbered lists, quotes, highlight,
    with live styling.
21. **Nested checklists** and progress shown on the card.
22. **Notebooks.** Folders on top of labels, and sort options.
23. **Version history** per note, with restore.
24. **Locked notes.** Per note lock on top of the app lock.
25. **Drawing.** Handwriting and sketch notes, stored as vectors.
26. **File attachments** of any type, encrypted.
27. **Voice to text** with the on-device recognizer only
    (`createOnDeviceSpeechRecognizer`, API 31). Refused if no on-device
    engine is present, never falls back to a network one.
28. **Recurring reminders.**
29. **Quick capture.** Quick Settings tile, app shortcuts, share target for
    text and images.
30. **Adaptive layout.** List and detail side by side on tablets and
    foldables, navigation rail.
31. **Folder sync for Syncthing users.** Encrypted export mirrored to a chosen
    folder, no server.
32. **Import from Google Keep Takeout** and plain Markdown folders.
33. **Panic wipe**, optional, behind confirmation.

## Needs a decision later, not now

- **OCR on images.** ML Kit runs on device but pulls Google libraries that may
  send usage logs. It is used only if that can be proven off, otherwise an
  open source engine is considered or the feature is dropped.
- **Release channel.** GitHub releases, F-Droid, or Google Play. Each needs
  its own signing and metadata setup.
