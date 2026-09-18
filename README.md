# Amehrug

Private notes for Android. Offline, always.

Amehrug never asks for network access. Notes stay on the phone and leave it
only when you export them. The name comes from Kabyle.

Status: early rewrite, nothing usable yet. See `docs/02-ROADMAP.md`.

## What it does today

- Opens an encrypted database, with no permission at all.
- Locks behind your fingerprint, face or screen lock.
- Writes and reads a backup sealed with a password you choose.

## Goals

- No INTERNET permission, no analytics, no account, no cloud backup.
- Notes, media and backups encrypted on the device.
- Jetpack Compose with Material You, Android 12 and later.
- Imports backups made by Notally.

## Encryption

- Database: SQLCipher, key wrapped by the Android Keystore.
- Attachments: AES-256-GCM, one sealed file each, in private storage.
- Backups: AES-256-GCM, key derived from your password with Argon2id.

## Credits

Amehrug is a fork of [Notally](https://github.com/OmGodse/Notally) by OMGodse,
released under the GNU General Public License v3. Its source is not copied
here, only the design lessons and the ability to read its backups.

## License

GNU General Public License v3.0. See `LICENSE.md`.
