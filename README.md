# Amehrug

Private notes for Android. Offline, always.

Amehrug never asks for network access. Notes stay on the phone and leave it
only when you export them. The name comes from Kabyle.

Status: early rewrite, not ready for daily use yet.

## What it does

- Notes and checklists, pinned, coloured, labelled, archived or binned.
- Full text search that ignores case and accents.
- A lock behind your fingerprint, face or screen lock.
- A backup sealed with a password you choose.
- No permission at all. Not even network.

## Encryption

- Database: SQLCipher, key wrapped by the Android Keystore.
- Attachments: AES-256-GCM, one sealed file each, in private storage.
- Backups: AES-256-GCM, key derived from your password with Argon2id.

## Build

Android Studio, or `./gradlew assembleDebug`. Requires JDK 21.
Every push to `main` publishes a signed APK per architecture in Releases.

## Credits

Amehrug is a fork of [Notally](https://github.com/OmGodse/Notally) by OMGodse,
released under the GNU General Public License v3.

Icons are Material Symbols Rounded from
[google/material-design-icons](https://github.com/google/material-design-icons),
Apache License 2.0.

## License

GNU General Public License v3.0. See `LICENSE.md`.
