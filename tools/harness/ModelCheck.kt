import com.amehrug.app.model.AppSettings
import com.amehrug.app.model.FtsQuery
import com.amehrug.app.model.LockPolicy
import com.amehrug.app.model.SettingsCodec
import com.amehrug.app.model.ListItem
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteRules
import com.amehrug.app.model.Reminder
import com.amehrug.app.model.TextSpan

fun expect(ok: Boolean, what: String) {
    if (!ok) throw AssertionError(what)
    println("ok  $what")
}

fun main() {
    expect(FtsQuery.build("réunion lu") == "\"réunion*\" \"lu*\"", "fts prefix terms with accents")
    expect(FtsQuery.build("  ") == null, "fts blank is null")
    expect(FtsQuery.build("\"*()-:^") == null, "fts only symbols is null")
    expect(FtsQuery.build("a OR b") == "\"a*\" \"OR*\" \"b*\"", "fts operators are quoted")
    expect(FtsQuery.build("x\" OR 1=1 --") == "\"x*\" \"OR*\" \"1*\" \"1*\"", "fts injection neutralised")
    expect(FtsQuery.build("l'été") == "\"l*\" \"été*\"", "fts apostrophe splits")
    expect(FtsQuery.build((1..20).joinToString(" ") { "w$it" })!!.split(" ").size == 10, "fts max 10 terms")
    expect(FtsQuery.build("z".repeat(100)) == "\"" + "z".repeat(64) + "*\"", "fts term cut at 64")

    expect(NoteRules.normalizeLabel("  Travail   perso ") == "Travail perso", "label normalized")
    expect(NoteRules.normalizeLabel("   ") == null, "blank label is null")
    expect(NoteRules.normalizeLabel("x".repeat(80))!!.length == 50, "label cut at 50")

    val note = Note(
        title = "Line\nbreak",
        body = "hello",
        spans = listOf(
            TextSpan(3, 99, bold = true),
            TextSpan(0, 2),
            TextSpan(2, 2, italic = true),
            TextSpan(-5, 1, italic = true),
            TextSpan(-5, 1, italic = true),
        ),
        items = listOf(ListItem(" a\nb ", indent = 9), ListItem("", indent = -1)),
        labels = listOf("Work", " work ", "", "Home"),
        reminders = listOf(Reminder(20), Reminder(10), Reminder(20)),
    )
    val clean = NoteRules.sanitize(note)
    expect(clean.title == "Line break", "title single line")
    expect(clean.spans == listOf(TextSpan(0, 1, italic = true), TextSpan(3, 5, bold = true)), "spans clamped, deduplicated, sorted, useless dropped")
    expect(clean.items[0].indent == 3 && clean.items[1].indent == 0, "indent clamped")
    expect(clean.items[0].body == " a b ", "item single line")
    expect(clean.labels == listOf("Work", "Home"), "labels case insensitive, first spelling wins")
    expect(clean.reminders.map { it.atMillis } == listOf(10L, 20L), "reminders deduplicated and sorted")

    expect(NoteRules.itemsText(listOf(ListItem(" milk "), ListItem("  "), ListItem("eggs"))) == "milk\neggs", "items text")
    expect(NoteRules.isEmpty(Note(items = listOf(ListItem(" ")))), "blank note is empty")
    expect(!NoteRules.isEmpty(Note(items = listOf(ListItem("x")))), "list with text is not empty")
    expect(NoteRules.trashCutoff(31L * 86_400_000) == 86_400_000L, "trash cutoff 30 days")
    val on = AppSettings(lockEnabled = true, lockTimeoutSeconds = 60)
    val off = AppSettings(lockEnabled = false)
    expect(LockPolicy.lockedAtStart(on) && !LockPolicy.lockedAtStart(off), "locked at start only when enabled")
    expect(LockPolicy.shouldLock(on, null, 1000), "no known absence locks")
    expect(!LockPolicy.shouldLock(off, null, 1000), "lock off never locks")
    expect(!LockPolicy.shouldLock(on, 1000, 1000 + 59_999), "under the timeout stays open")
    expect(LockPolicy.shouldLock(on, 1000, 1000 + 60_000), "at the timeout it locks")
    expect(LockPolicy.shouldLock(on, 5000, 1000), "a clock going backwards locks")
    expect(LockPolicy.shouldLock(on.copy(lockTimeoutSeconds = 0), 1000, 1000), "zero timeout locks at once")

    expect(SettingsCodec.decode(emptyMap()) == AppSettings(), "settings defaults")
    expect(SettingsCodec.decode(mapOf("lock.enabled" to "true", "lock.timeoutSeconds" to "900")) == AppSettings(true, 900), "settings read")
    expect(SettingsCodec.decode(mapOf("lock.enabled" to "oui")) == AppSettings(), "damaged flag falls back")
    expect(SettingsCodec.decode(mapOf("lock.timeoutSeconds" to "77")) == AppSettings(), "unknown timeout falls back")
    expect(SettingsCodec.decode(mapOf("lock.timeoutSeconds" to "x")) == AppSettings(), "timeout that is not a number falls back")
    expect(SettingsCodec.encodeLockTimeout(77).second == "60", "encoding refuses an unknown timeout")
    expect(SettingsCodec.encodeLockEnabled(true) == ("lock.enabled" to "true"), "encoding the flag")

    println("ALL PASSED")
}
