# Releasing

A push of a tag named `v<versionName>` builds, checks and publishes the APK.
`.github/workflows/release.yml` does the work.

## Once, on your machine: make a signing key

The key identifies every future version of the app. Android refuses an update
signed by a different key, so **losing this file means no user can ever
update**, they would have to uninstall and lose their notes. Keep two copies,
offline.

    keytool -genkeypair -v \
      -keystore amehrug.jks \
      -alias amehrug \
      -keyalg RSA -keysize 4096 -validity 10000 \
      -storetype PKCS12

Then turn it into text for GitHub:

    base64 -w0 amehrug.jks > amehrug.jks.base64

## Once, on GitHub: four secrets

Settings, Secrets and variables, Actions, New repository secret:

| Name | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the content of `amehrug.jks.base64` |
| `ANDROID_KEYSTORE_PASSWORD` | the store password you chose |
| `ANDROID_KEY_ALIAS` | `amehrug` |
| `ANDROID_KEY_PASSWORD` | the key password you chose |

Never commit the `.jks` or its base64 form. `.gitignore` already refuses
`*.jks` and `*.keystore`.

## Every release

1. Check that `versionName` and `versionCode` in `app/build.gradle.kts` are
   the ones you want. The workflow stops if the tag and `versionName` differ.
2. Tag and push:

       git tag v0.6.1
       git push origin v0.6.1

3. The workflow builds the release APK, fails if the APK asks for any
   permission it should not, names the file `Amehrug-0.6.1.apk`, writes its
   SHA-256 next to it, and creates the GitHub release with generated notes.

Without the secrets the release still appears, with a file named
`-unsigned.apk`, which no phone will install. That is the signal that the
secrets are missing.

## Checking what you downloaded

    sha256sum -c Amehrug-0.6.1.apk.sha256

## Not done yet

- F-Droid, which wants reproducible builds and its own metadata.
- Google Play, which wants an account, a fee and an app bundle.
