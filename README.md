# Amehrug

Private notes for Android.

## What it does

- Notes and checklists, pinned, coloured, labelled, archived or binned.
- Select several at once and pin, colour, label, archive or bin them together.
- Full text search that ignores case and accents.
- Pictures in a note, stored encrypted like everything else.
- A lock behind your fingerprint, face or screen lock.
- A backup sealed with a password you choose, and restore from it.
- Import from a Notally backup, notes, labels, colours and pictures.
- A dock you can reorder by holding a button and dragging it.
- Material You colours from your wallpaper, and a layout that spreads out on
  a tablet or an unfolded screen.
- No permission at all. 
- Bold, italic, monospace and strikethrough, applied to a selection or armed
  before typing. Styles from an imported Notally note are kept.
- Export to TXT, Markdown, HTML or PDF, from an open note or from a selection
  on the wall. The file goes wherever the system picker is pointed, which
  needs no permission.

## Not yet

- The home screen widget.
- Automatic backup on a schedule. Backups are manual for now.

## Encryption

- Database: SQLCipher, key wrapped by the Android Keystore.
- Attachments: AES-256-GCM, one sealed file each, in private storage.
- Backups: AES-256-GCM, key derived from your password with Argon2id.
- Screenshots and the recents preview are blocked, cloud backup is off.

## Install

Android 12 or later. Download an APK from Releases. Pick `arm64-v8a` for
almost any recent phone, or `universal` if you are not sure.

Every release carries a `SHA256SUMS.txt`. To check what you downloaded:

```
sha256sum Amehrug-<version>-<abi>.apk
```

Compare the result with the matching line in that file.

## Backups

Keep one. A note lives in one encrypted database on one phone, and a phone
that is lost or wiped takes it with it. Settings, Backup, export, and put the
file somewhere you control.

## Build

Android Studio, or `./gradlew assembleDebug`. Requires JDK 21.
Every push to `main` builds debug and release and publishes a signed APK per
architecture.

## Credits

Amehrug is a fork of [Notally](https://github.com/OmGodse/Notally) by OMGodse,
released under the GNU General Public License v3.

Icons are Material Symbols Rounded from
[google/material-design-icons](https://github.com/google/material-design-icons),
Apache License 2.0.

## License

GNU General Public License v3.0. See `LICENSE.md`.
