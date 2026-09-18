# Working agreement

Carried over from MTGA and LinkedOut. Only the rules that still have a
subject are kept, and the priorities are rewritten for an offline notes app.

## One task per turn, the user decides when to test

- One task per turn. Build it, deliver the full project zip, stop.
- Wait for the user to say "go", "next" or "continue" before the next task.
- The user decides when to build and test. Do not demand a test after every
  task. Do say plainly, in one line, when stacking makes a failure hard to
  trace, and propose to wait when the risk is real.
- Keep turns short: read only the files the task needs, a handful of tool
  calls, then deliver.
- If a task is large, propose the split in two lines and do the first part.
- Before starting a task, state it in a few lines and ask to confirm when the
  scope is not already agreed. After "go", build without further questions.

## Writing style, enforced by user preference

- Never use em dashes. Never use semicolons. This applies to every response,
  to code comments, and to documents like these.
- Short answers. Do not pad.
- Ask clarifying questions before generating, but do not ask the user to make
  decisions they have delegated.
- The user writes in English or French. Answer in the language of their
  message. These documents stay in English.

## How the user wants decisions made

"I don't want to give you all the directives, I want you to be independent
through mature and intensive reflection." And: "you build, I test."

Decide engineering questions yourself, state the decision and the reasoning in
a few lines, then build. Ask only when the choice is genuinely theirs: a trust
or privacy trade-off, an external account, money, or a feature they did not
request.

This includes changing the plan. When a planned approach cannot work, say
what changed and why. Never silently build the thing that was asked for when
it cannot work.

## Stated priorities

- **Security. Efficiency. Modernity.**
  - Security: no INTERNET permission, ever. No analytics, no crash reporting,
    no account, no cloud backup. Notes, media and backups encrypted at rest.
    Any new permission must be named and justified before it is added.
    Nothing leaves the device unless the user exports it.
  - Efficiency: small APK, fast cold start, no work on the main thread, few
    dependencies. Every dependency must be maintained and come from Google or
    Maven Central.
  - Modernity: Compose, Material 3 Expressive, dynamic color, edge to edge,
    predictive back, current AGP.
- Accessibility to old Android versions is not a goal. minSdk rises when a
  feature needs it.
- Notally users must be able to bring their notes.

## Delivery rules

1. **Ship the full project each time**, as a zip preserving paths from the
   project root, named `Amehrug-<version>.zip`. Remove older zips from outputs
   so only one is on offer.
2. **Never mix channels.** Either the assistant ships zips and the user makes
   no manual edits, or the user edits and the assistant sends single files.
3. **The GitHub web uploader skips dotted folders.** When `.github/` changes,
   say so, and tell the user to paste those files through the web editor.
4. The user cannot easily extract files from the device. They can copy and
   paste. An in-app diagnostic log with "Copy all" and "Save as .txt" is part
   of the first tasks, and it never contains note content.
5. Bump versionName and versionCode on every delivered zip.
6. When something cannot be verified, say so plainly and state what was
   checked instead. There is no Android SDK in the sandbox, so the Gradle
   build is never verified here. Pure Kotlin (parsers, importers, crypto
   format, converters) is extracted so it can run under kotlinc, and it is
   run before delivery.
7. When a symptom cannot be reproduced in the sandbox, the next deliverable
   is the instrument, not another guess.
8. **Versions come from official pages, never from memory.** Search first,
   then fetch from developer.android.com or the library's own release page.
   The AGP 9 skill is readable as raw markdown at
   `raw.githubusercontent.com/android/skills/main/build-system/agp/agp-9-upgrade/`.
9. **An API is used only after its signature is confirmed** for the exact
   version the BOM ships. An API marked internal or experimental in that
   version is not used, however well documented it is elsewhere.

## Mistakes made, do not repeat

- Guessed library versions that did not exist. Four failed CI builds.
- Told the user a zip contained their edit when it did not.
- Used an API above minSdk. Check API levels against the current minSdk.
- Read a log line as an error without reading the code path that wrote it.
- Relied on `MaterialExpressiveTheme` when the shipped material3 had it
  internal. Check visibility, not only the signature.
- Edited code by cutting text between computed indexes. A fallback fired and
  swallowed two declarations. Read the code and edit by exact match.
- ktlint parses syntax and does not resolve names. After an edit, check that
  every local used in a touched file is still declared.
- Two effects started by the same composition ran in the wrong order and one
  saved state before the other restored it. When two effects depend on each
  other, make the dependency explicit.
- Fixed the right bug in the wrong file, then announced it broadly. Say which
  screen a fix reaches.
- Claimed a fix without running it. A function can almost always be extracted
  into a harness. Extract it.
- Read a defect into a correct number. Check what a counter means before
  believing it.
- A check with a cheap escape hatch hid a whole class of failure. Ask what it
  lets through.
- Removing a stored setting must not crash on an old file. Every JSON decoder
  uses `ignoreUnknownKeys = true`.

## Environment facts about the user

- Linux, Android Studio, builds locally and via GitHub Actions.
- Dependabot will be set up. Merged Dependabot pull requests change the repo
  without a zip from the assistant, so always build on the latest zip the
  user sends.
